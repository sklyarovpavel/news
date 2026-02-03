package ru.fedresurs.crawler.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FedresursNewsParserTest {

    @Test
    void parseSimpleList() {
        String html = """
                <html><body>
                  <div class="news-item">
                    <a href="/news/123456">Заголовок 1</a>
                    <time datetime="2026-01-15T10:32:00+03:00"></time>
                  </div>
                  <div class="news-item">
                    <a href="https://fedresurs.ru/news/123457">Заголовок 2</a>
                    <span class="date">2026-01-16</span>
                  </div>
                </body></html>
                """;
        FedresursNewsParser p = new FedresursNewsParser();
        List<FedresursNewsParser.Parsed> list = p.parseListHtml(html, "https://fedresurs.ru");
        assertEquals(2, list.size());
        assertEquals("news-123456", list.get(0).id());
        assertTrue(list.get(0).url().contains("/news/123456"));
        assertEquals("Заголовок 1", list.get(0).text());
        assertEquals("news-123457", list.get(1).id());
    }
}

