package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentVO {
    private Long id;
    private Long postId;
    private Long userId;
    private String userName;
    private String userRole;
    private Long parentId;
    private String parentUserName;
    private String content;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private List<CommentVO> replies;

    public static CommentVO from(com.agritrace.entity.PostComment comment, com.agritrace.entity.User user) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setUserId(comment.getUserId());
        vo.setUserName(user != null ? (user.getRealName() != null ? user.getRealName() : user.getUsername()) : "未知用户");
        vo.setUserRole(user != null ? user.getRole() : "");
        vo.setParentId(comment.getParentId());
        vo.setParentUserName(null);
        vo.setContent(comment.getDeleted() ? "该评论已删除" : comment.getContent());
        vo.setDeleted(comment.getDeleted());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setReplies(List.of());
        return vo;
    }
}
