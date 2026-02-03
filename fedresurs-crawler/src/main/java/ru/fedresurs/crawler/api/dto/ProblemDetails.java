package ru.fedresurs.crawler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDetails {
    private String type;
    private String title;
    private Integer status;
    private String detail;
    private String instance;
}

