package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MyLikeVO {
    private Long id;
    private Long postId;
    private String postTitle;
    private String postAuthorName;
    private LocalDateTime likedAt;
    private Boolean postDeleted;

    public static MyLikeVO from(com.agritrace.entity.PostLike like, com.agritrace.entity.CommunityPost post, com.agritrace.entity.User postAuthor) {
        MyLikeVO vo = new MyLikeVO();
        vo.setId(like.getId());
        vo.setPostId(like.getPostId());
        vo.setLikedAt(like.getCreatedAt());
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
