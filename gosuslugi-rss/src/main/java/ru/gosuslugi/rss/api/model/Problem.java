package ru.gosuslugi.rss.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Problem {
    private String type;
    private String title;
    private Integer status;
    private String detail;
    private String instance;
}

