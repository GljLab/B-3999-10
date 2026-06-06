package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MyBookmarkVO {
    private Long id;
    private Long postId;
    private String postTitle;
    private String postAuthorName;
    private LocalDateTime bookmarkedAt;
    private Boolean postDeleted;

    public static MyBookmarkVO from(com.agritrace.entity.PostBookmark bookmark, com.agritrace.entity.CommunityPost post, com.agritrace.entity.User postAuthor) {
        MyBookmarkVO vo = new MyBookmarkVO();
        vo.setId(bookmark.getId());
        vo.setPostId(bookmark.getPostId());
        vo.setBookmarkedAt(bookmark.getCreatedAt());
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
