package ru.smev.tg.telegram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawPost {
    private String id;
    private String url;
    private String text;
    private OffsetDateTime publishedAt;
}

