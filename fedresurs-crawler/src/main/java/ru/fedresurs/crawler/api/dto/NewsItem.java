package ru.fedresurs.crawler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItem {
    private String id;
    private String url;
    private String title;
    private String text; // полный текст новости с детальной страницы
    private String publishedAt; // ISO 8601 date-time
}

