package com.agritrace.dto;

import lombok.Data;

@Data
public class CommunityPostRequest {
    private String title;
    private String description;
    private String images;
}
