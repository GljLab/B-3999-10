package com.agritrace.dto;

import lombok.Data;

@Data
public class TopicUpdateRequest {
    private String name;
    private String description;
    private String icon;
    private Integer status;
    private Integer isFeatured;
    private Integer sortOrder;
}
