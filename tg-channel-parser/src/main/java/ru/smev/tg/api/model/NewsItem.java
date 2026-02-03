package ru.smev.tg.api.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItem {
    private String id;
    private String url;
    private String title;
    private String text;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime publishedAt;
}

