package ru.fedresurs.crawler.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FedresursNewsParser {
    public record Parsed(String id, String url, String text, String publishedAtIso) {}

    private static final Pattern ID_FROM_URL = Pattern.compile(".*/news/(\\d+).*");

    public List<Parsed> parseListHtml(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<Parsed> result = new ArrayList<>();

        // Стратегия: попробовать несколько распространённых шаблонов верстки
        Elements items = new Elements();
        items.addAll(doc.select("article"));
        items.addAll(doc.select("div.news-item"));
        items.addAll(doc.select("li.news-item"));
        if (items.isEmpty()) {
            // fallback: любые ссылки на /news/
            items = doc.select("a[href*=\"/news/\"]");
        }

        for (Element el : items) {
            Element linkEl = el.selectFirst("a[href*=\"/news/\"]");
            if (linkEl == null && el.tagName().equals("a")) {
                linkEl = el;
            }
            if (linkEl == null) continue;

            String href = linkEl.absUrl("href");
            if (href == null || href.isBlank()) continue;

            String text = linkEl.hasText() ? linkEl.text() : el.text();
            if (text == null) text = "";

            String id = extractIdFromUrl(href);

            String iso = null;
            Element timeEl = el.selectFirst("time[datetime]");
            if (timeEl != null) {
                String dt = timeEl.attr("datetime").trim();
                try {
                    OffsetDateTime odt = OffsetDateTime.parse(dt);
                    iso = odt.toString();
                } catch (Exception ignored) {
                }
            }
            if (iso == null) {
                Element dateEl = el.selectFirst(".date, .news-date, span.date, div.date");
                if (dateEl != null) {
                    String raw = dateEl.text().trim();
                    // попытка распарсить YYYY-MM-DD или DD.MM.YYYY
                    iso = tryParseDateGuess(raw);
                }
            }

            if (iso == null) {
                // Последнее средство — текущее время МСК (чтобы не отбрасывать запись)
                iso = OffsetDateTime.now(ZoneOffset.ofHours(3)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }

            if (id == null || id.isBlank()) {
                id = "news-" + UUID.nameUUIDFromBytes((href + "|" + iso).getBytes());
            }

            result.add(new Parsed(id, href, text, iso));
        }

        return result;
    }

    private static String extractIdFromUrl(String href) {
        try {
            String path = URI.create(href).getPath();
            Matcher m = ID_FROM_URL.matcher(path);
            if (m.matches()) {
                return "news-" + m.group(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String tryParseDateGuess(String raw) {
        String cleaned = raw.replace('\u00A0', ' ').trim();
        // Простые форматы
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
}

