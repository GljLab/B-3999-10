package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MyCommentVO {
    private Long id;
    private Long postId;
    private String postTitle;
    private String postAuthorName;
    private Long parentId;
    private String parentUserName;
    private String content;
    private LocalDateTime createdAt;
    private Boolean postDeleted;

    public static MyCommentVO from(com.agritrace.entity.PostComment comment, com.agritrace.entity.CommunityPost post, com.agritrace.entity.User postAuthor, com.agritrace.entity.User parentUser) {
        MyCommentVO vo = new MyCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setParentUserName(parentUser != null ? (parentUser.getRealName() != null ? parentUser.getRealName() : parentUser.getUsername()) : null);
        if (post != null) {
            vo.setPostTitle(post.getTitle());
            vo.setPostDeleted(false);
            vo.setPostAuthorName(postAuthor != null ? (postAuthor.getRealName() != null ? postAuthor.getRealName() : postAuthor.getUsername()) : "未知用户");
        } else {
            vo.setPostTitle("该内容已被删除");
            vo.setPostDeleted(true);
            vo.setPostAuthorName("");
        }
        return vo;
    }
}
