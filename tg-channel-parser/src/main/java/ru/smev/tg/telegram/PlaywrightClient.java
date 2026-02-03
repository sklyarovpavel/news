package ru.smev.tg.telegram;

import com.microsoft.playwright.*;
import com.microsoft.playwright.PlaywrightException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.smev.tg.config.CrawlerProperties;
import ru.smev.tg.rate.RateLimiter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlaywrightClient implements RenderedTelegramClient {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightClient.class);

    private final CrawlerProperties props;
    private final RateLimiter rateLimiter;
    private Playwright playwright;
    private Browser browser;

    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile(".*/(?<id>\\d+)(?:\\?.*)?$");

    public PlaywrightClient(CrawlerProperties props, RateLimiter rateLimiter) {
        this.props = props;
        this.rateLimiter = rateLimiter;
    }

    private synchronized void ensureBrowser() {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        if (browser == null) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(java.util.List.of(
                            "--no-sandbox", "--disable-dev-shm-usage",
                            "--disable-gpu", "--disable-setuid-sandbox"
                    ));
            browser = playwright.chromium().launch(options);
        }
    }

    @Override
    public Mono<List<RawPost>> fetchPosts(int offset, int count) {
        int target = Math.max(0, offset) + Math.max(1, count) + 1; // +1 для вычисления nextCursor
        return rateLimiter.acquire()
                .then(Mono.fromCallable(() -> {
                    log.info("PlaywrightClient.fetchPosts: offset={} count={} target={}", offset, count, target);
                    try {
                        ensureBrowser();
                        Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                                .setUserAgent(props.getSource().getUserAgent())
                                .setLocale(props.getSource().getAcceptLanguage())
                                .setExtraHTTPHeaders(java.util.Map.of(
                                        "Accept-Language", props.getSource().getAcceptLanguage(),
                                        "Accept", props.getSource().getAccept()
                                ));

                        try (BrowserContext context = browser.newContext(ctxOpts);
                             Page page = context.newPage()) {
                            page.setDefaultTimeout(props.getRequestTimeoutMs());
                            page.setDefaultNavigationTimeout(props.getRequestTimeoutMs());
                        // Пагинация через параметр ?before=<id>
                        List<RawPost> all = new ArrayList<>();
                        String base = props.getSource().getBaseUrl();
                        Long beforeId = null;
                        int pageAttempts = 0;
                        while (all.size() < target && pageAttempts < 30) {
                            String pageUrl = beforeId == null ? base : base + "?before=" + beforeId;
                            log.info("Open channel page: {}", pageUrl);
                            try {
                                page.navigate(pageUrl);
                            } catch (PlaywrightException e) {
                                log.warn("Navigate warning (channel): {}", e.toString());
                            }
                            page.waitForSelector(".tgme_widget_message");
                            String pageHtml = page.content();
                            Document pdoc = Jsoup.parse(pageHtml);
                            Elements msgEls = pdoc.select(".tgme_widget_message");
                            log.info("Playwright: found {} message elements on page", msgEls.size());
                            // отладка: покажем несколько ссылок дат
                            int dbg = 0;
                            for (Element elDbg : msgEls) {
                                Element aDbg = elDbg.selectFirst(".tgme_widget_message_date a[href]");
                                if (aDbg != null) {
                                    log.info("Debug href[{}]: {}", dbg, aDbg.attr("href"));
                                    dbg++;
                                    if (dbg >= 3) break;
                                }
                            }
                            Long minSeenId = null;
                            int i = 0;
                            for (Element el : msgEls) {
                                try {
                                    Element a = el.selectFirst(".tgme_widget_message_date a[href]");
                                    String url = null;
                                    if (a != null) {
                                        url = a.attr("abs:href");
                                        if (url == null || url.isBlank()) {
                                            url = a.attr("href");
                                        }
                                    } else {
                                        // fallback: из data-post собираем URL
                                        String dataPost = el.attr("data-post");
                                        if (dataPost != null && !dataPost.isBlank()) {
                                            url = "https://t.me/" + dataPost;
                                        }
                                    }
                                    String id = extractMessageId(url);
                                    if (id == null) {
                                        i++;
                                        continue;
                                    }
                                    long numericId = Long.parseLong(id.replace("post-", ""));
                                    if (minSeenId == null || numericId < minSeenId) {
                                        minSeenId = numericId;
                                    }
                                    OffsetDateTime publishedAt = null;
                                    Element time = el.selectFirst(".tgme_widget_message_date time[datetime]");
                                    if (time != null) {
                                        publishedAt = parseDateTime(time.attr("datetime"));
                                    }
                                    Element textEl = el.selectFirst(".tgme_widget_message_text");
                                    String text = textEl != null ? textEl.text().trim() : "";
                                    boolean truncated = text != null && text.length() > 160 && text.endsWith("…");
                                    if (truncated) {
                                        String full = fetchFullText(context, url);
                                        if (full != null && !full.isBlank()) {
                                            text = full;
                                        }
                                    }
                                    all.add(RawPost.builder()
                                            .id(id)
                                            .url(url)
                                            .text(text == null ? "" : text)
                                            .publishedAt(publishedAt)
                                            .build());
                                } catch (Throwable perItem) {
                                    log.debug("Skip item {} due to error: {}", i, perItem.toString());
                                }
                                i++;
                            }
                            if (minSeenId == null) {
                                break;
                            }
                            beforeId = minSeenId;
                            pageAttempts++;
                        }

                            // дедупликация по id и обрезка до target
                            List<RawPost> result = new ArrayList<>();
                            Set<String> seen = new HashSet<>();
                            for (RawPost p : all) {
                                if (p.getId() == null) continue;
                                if (seen.add(p.getId())) {
                                    result.add(p);
                                }
                                if (result.size() >= target) break;
                            }
                            log.info("Playwright: collected {} unique posts (target {})", result.size(), target);
                            return result;
                        }
                    } catch (Throwable e) {
                        log.warn("Playwright failed, fallback to Jsoup: {}", e.toString());
                        List<RawPost> via = fetchViaJsoup(target);
                        log.info("Jsoup fallback: collected {} posts", via.size());
                        return via;
                    }
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private List<RawPost> fetchViaJsoup(int target) throws Exception {
        Connection conn = Jsoup.connect(props.getSource().getBaseUrl())
                .userAgent(props.getSource().getUserAgent())
                .header("Accept-Language", props.getSource().getAcceptLanguage())
                .header("Accept", props.getSource().getAccept())
                .timeout(Math.toIntExact(props.getRequestTimeoutMs()))
                .followRedirects(true);
        Document doc = conn.get();
        Elements items = doc.select(".tgme_widget_message");
        List<RawPost> all = new ArrayList<>();
        for (Element el : items) {
            Element a = el.selectFirst(".tgme_widget_message_date a[href]");
            if (a == null) continue;
            String url = a.attr("abs:href");
            String id = extractMessageId(url);
            Element time = el.selectFirst(".tgme_widget_message_date time[datetime]");
            OffsetDateTime publishedAt = time != null ? parseDateTime(time.attr("datetime")) : null;
            Element textEl = el.selectFirst(".tgme_widget_message_text");
            String text = textEl != null ? textEl.text().trim() : "";
            all.add(RawPost.builder()
                    .id(id)
                    .url(url)
                    .text(text)
                    .publishedAt(publishedAt)
                    .build());
            if (all.size() >= target) break;
        }
        // дедупликация
        List<RawPost> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RawPost p : all) {
            if (p.getId() == null) continue;
            if (seen.add(p.getId())) {
                result.add(p);
            }
            if (result.size() >= target) break;
        }
        return result;
    }

    private String extractMessageId(String url) {
        if (url == null) return null;
        Matcher m = MESSAGE_ID_PATTERN.matcher(url);
        if (m.matches()) {
            return "post-" + m.group("id");
        }
        return null;
    }

    private OffsetDateTime parseDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String extractText(Locator item) {
        try {
            Locator textNode = item.locator(".tgme_widget_message_text");
            if (textNode.count() == 0) return "";
            // берем HTML и приводим к тексту с переносами
            String html = textNode.first().innerHTML();
            Document doc = Jsoup.parseBodyFragment(html);
            String text = doc.text();
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean looksTruncated(String text, Locator item) {
        if (text == null) return false;
        // эвристика: длинный текст, заканчивается многоточием, и есть кнопка Читать далее
        boolean endsWithEllipsis = text.length() > 160 && text.endsWith("…");
        boolean hasReadMore = item.locator("a:has-text(\"ещё\")").count() > 0
                || item.locator("a:has-text(\"more\")").count() > 0;
        return endsWithEllipsis || hasReadMore;
    }

    private String fetchFullText(BrowserContext context, String url) {
        try (Page postPage = context.newPage()) {
            postPage.setDefaultTimeout(props.getRequestTimeoutMs());
            postPage.setDefaultNavigationTimeout(props.getRequestTimeoutMs());
            try {
                postPage.navigate(url);
            } catch (PlaywrightException e) {
                log.warn("Navigate warning (post): {}", e.toString());
            }
            postPage.waitForSelector(".tgme_widget_message_text", new Page.WaitForSelectorOptions().setTimeout(3000));
            Locator textNode = postPage.locator(".tgme_widget_message_text");
            if (textNode.count() == 0) return null;
            String html = textNode.first().innerHTML();
            Document doc = Jsoup.parseBodyFragment(html);
            return doc.text().trim();
        } catch (Exception e) {
            log.warn("Failed to fetch full text for {}: {}", url, e.toString());
            return null;
        }
    }
}

