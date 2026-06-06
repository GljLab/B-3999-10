package com.agritrace.dto;

import lombok.Data;

@Data
public class CommentRequest {
    private Long parentId;
    private String content;
}
