package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminCommentVO {
    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String userName;
    private String userRole;
    private Long parentId;
    private String content;
    private Boolean deleted;
    private LocalDateTime createdAt;

    public static AdminCommentVO from(com.agritrace.entity.PostComment comment, com.agritrace.entity.CommunityPost post, com.agritrace.entity.User user) {
        AdminCommentVO vo = new AdminCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setPostTitle(post != null ? post.getTitle() : "该内容已被删除");
        vo.setUserId(comment.getUserId());
        vo.setUserName(user != null ? (user.getRealName() != null ? user.getRealName() : user.getUsername()) : "未知用户");
        vo.setUserRole(user != null ? user.getRole() : "");
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setDeleted(comment.getDeleted());
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }
}
