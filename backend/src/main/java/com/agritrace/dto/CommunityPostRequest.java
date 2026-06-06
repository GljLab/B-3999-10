package com.agritrace.dto;

import lombok.Data;
import java.util.List;

@Data
public class CommunityPostRequest {
    private String title;
    private String description;
    private String images;
    private List<Long> topicIds;
}
