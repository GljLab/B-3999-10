package com.agritrace.dto;

import lombok.Data;

@Data
public class TopicCreateRequest {
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
}
