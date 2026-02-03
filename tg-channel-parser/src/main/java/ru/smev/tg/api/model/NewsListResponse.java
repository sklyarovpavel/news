package ru.smev.tg.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsListResponse {
    private List<NewsItem> items;
    private int count;
    private String nextCursor;
}

