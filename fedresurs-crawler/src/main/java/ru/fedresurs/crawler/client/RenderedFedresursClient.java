package ru.fedresurs.crawler.client;

import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.fedresurs.crawler.config.CrawlerProperties;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RenderedFedresursClient implements FedresursClient {
    private static final Logger log = LoggerFactory.getLogger(RenderedFedresursClient.class);
    private final CrawlerProperties props;

    public RenderedFedresursClient(CrawlerProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Page> fetchPage(String cursor, java.time.LocalDate from, java.time.LocalDate to, int limit) {
        return Mono.fromCallable(() -> {
            int offset = 0;
            if (cursor != null && cursor.startsWith("o:")) {
                try { offset = Integer.parseInt(cursor.substring(2)); } catch (Exception ignored) {}
            }
            List<Item> items = renderAndExtract(offset + limit)
                    .stream()
                    .skip(offset)
                    .limit(limit)
                    .collect(Collectors.toList());
            String next = items.size() < limit ? null : "o:" + (offset + items.size());
            log.info("Rendered items={}, offset={}, next={}", items.size(), offset, next);
            return new Page(items, next);
        });
    }

    private List<Item> renderAndExtract(int needCount) {
        int timeoutMs = Math.max(props.getRequestTimeoutMs(), 8000);
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(props.getSource().getUserAgent())
                    .setLocale(props.getSource().getAcceptLanguage())
            );
            com.microsoft.playwright.Page page = context.newPage();
            page.navigate(props.getSource().getBaseUrl(), new com.microsoft.playwright.Page.NavigateOptions()
                    .setTimeout(timeoutMs));

            // Ждём появления хотя бы одного элемента списка ссылок на /news/
            page.waitForSelector("a[href^=\"/news/\"]", new com.microsoft.playwright.Page.WaitForSelectorOptions()
                    .setTimeout(timeoutMs));

            // Подгружаем больше элементов скроллом, пока не будет достаточно или не выйдем по лимиту попыток
            final int maxScrolls = 20;
            for (int i = 0; i < maxScrolls; i++) {
                int linkCount = page.locator("a[href^=\"/news/\"]").count();
                if (linkCount >= needCount) break;
                page.mouse().wheel(0, 2000);
                // Дадим времени SPA подгрузить
                page.waitForTimeout(400);
            }

            // Извлекаем уникальные ссылки в порядке появления
            Locator links = page.locator("a[href^=\"/news/\"]");
            int count = Math.min(links.count(), needCount);
            LinkedHashMap<String, Item> unique = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String href = links.nth(i).getAttribute("href");
                if (href == null || href.isBlank()) continue;
                String abs = href.startsWith("http") ? href : "https://fedresurs.ru" + href;
                String title = links.nth(i).innerText().trim();
                String id = deriveIdFromHref(href);
                String iso = extractDateNear(links.nth(i));
                if (iso == null) {
                    iso = OffsetDateTime.now(ZoneOffset.ofHours(3)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
                unique.putIfAbsent(abs, new Item(id, abs, title, iso));
            }
            context.close();
            browser.close();
            return new ArrayList<>(unique.values());
        }
    }

    private static String deriveIdFromHref(String href) {
        try {
            String[] parts = href.split("/");
            String last = parts[parts.length - 1];
            if (last.isBlank()) last = parts[parts.length - 2];
            // UUID или число -> префикс news-
            return "news-" + last;
        } catch (Exception e) {
            return "news-" + UUID.nameUUIDFromBytes(href.getBytes());
        }
    }

    private static String extractDateNear(Locator link) {
        // Пытаемся найти time[datetime] в блоке метаданных, если он присутствует
        try {
            Locator metaTime = link.locator("xpath=ancestor::*[1]//*[contains(@class,'news-item-metadata')]//time[@datetime]").first();
            if (metaTime != null && metaTime.count() > 0) {
                String dt = metaTime.first().getAttribute("datetime");
                if (dt != null && !dt.isBlank()) return dt;
            }
            Locator meta = link.locator("xpath=ancestor::*[1]//*[contains(@class,'news-item-metadata')]").first();
            if (meta != null && meta.count() > 0) {
                String raw = meta.first().innerText().trim();
                String iso = tryParseSimpleDate(raw);
                if (iso != null) return iso;
            }
        } catch (Exception ignored) {
        }
        // Пытаемся найти time[datetime] в ближайших родителях
        Locator candidate = link.locator("xpath=ancestor-or-self::*[1]//*[name()='time' and @datetime]").first();
        if (candidate != null && candidate.count() > 0) {
            String dt = candidate.first().getAttribute("datetime");
            if (dt != null && !dt.isBlank()) return dt;
        }
        // Ищем элементы с классами даты рядом
        Locator dateText = link.locator("xpath=ancestor::*[1]//*[contains(@class,'date') or contains(@class,'news-date')]").first();
        if (dateText != null && dateText.count() > 0) {
            String raw = dateText.first().innerText().trim();
            String iso = tryParseSimpleDate(raw);
            if (iso != null) return iso;
        }
        return null;
    }

    private static String tryParseSimpleDate(String raw) {
        String cleaned = raw.replace('\u00A0', ' ').trim();
        try {
            if (cleaned.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return cleaned.substring(0, 10) + "T00:00:00+03:00";
            }
            if (cleaned.matches("\\d{2}\\.\\d{2}\\.\\d{4}.*")) {
                String[] p = cleaned.substring(0, 10).split("\\.");
                String isoDate = p[2] + "-" + p[1] + "-" + p[0];
                return isoDate + "T00:00:00+03:00";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Mono<String> fetchContent(String url) {
        int timeoutMs = Math.max(props.getRequestTimeoutMs(), 8000);
        return Mono.fromCallable(() -> {
            try (Playwright playwright = Playwright.create()) {
                Browser browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(true)
                );
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(props.getSource().getUserAgent())
                        .setLocale(props.getSource().getAcceptLanguage())
                );
                com.microsoft.playwright.Page page = context.newPage();
                String base = props.getSource().getBaseUrl();
                String content = null;
                boolean clicked = false;
                try {
                    java.net.URI u = java.net.URI.create(url);
                    String path = u.getPath();
                    // Откроем список и кликнем по ссылке с нужным href — так SPA корректно подгрузит контент
                    page.navigate(base, new com.microsoft.playwright.Page.NavigateOptions().setTimeout(timeoutMs));
                    page.waitForSelector("a[href^=\"/news/\"]", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(timeoutMs));
                    Locator link = page.locator("a[href=\"" + path + "\"]");
                    if (link.count() == 0) {
                        link = page.locator("a[href^=\"" + path + "\"]");
                    }
                    if (link.count() > 0) {
                        link.first().click(new Locator.ClickOptions().setTimeout(timeoutMs));
                        clicked = true;
                    }
                } catch (Exception ignore) {
                }
                if (!clicked) {
                    // Фоллбек: пробуем прямую навигацию
                    page.navigate(url, new com.microsoft.playwright.Page.NavigateOptions().setTimeout(timeoutMs));
                }
                // Ждём появления контента: article/main/параграфы
                page.waitForTimeout(800);
                Locator article = page.locator("article");
                if (article.count() > 0) {
                    content = article.first().innerText();
                }
                if (content == null || content.isBlank()) {
                    Locator main = page.locator("main");
                    if (main.count() > 0) content = main.first().innerText();
                }
                if (content == null || content.isBlank()) {
                    Locator paragraphs = page.locator("p");
                    int pc = Math.min(paragraphs.count(), 40);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < pc; i++) {
                        String t = paragraphs.nth(i).innerText().trim();
                        if (!t.isBlank()) {
                            if (sb.length() > 0) sb.append("\n\n");
                            sb.append(t);
                        }
                    }
                    content = sb.toString();
                }
                context.close();
                browser.close();
                return content == null ? "" : content;
            }
        });
    }
}

