package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommunityPostDetailVO {
    private Long id;
    private Long authorId;
    private String title;
    private String description;
    private List<String> images;
    private String authorName;
    private String authorRole;
    private String authorAvatar;
    private LocalDateTime createdAt;
    private Integer viewCount;
    private Integer likeCount;
    private Integer bookmarkCount;
    private Integer commentCount;
    private Boolean liked;
    private Boolean bookmarked;

    public static CommunityPostDetailVO from(com.agritrace.entity.CommunityPost post, com.agritrace.entity.User user) {
        CommunityPostDetailVO vo = new CommunityPostDetailVO();
        vo.setId(post.getId());
        vo.setAuthorId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setDescription(post.getDescription());
        vo.setAuthorName(user != null ? user.getRealName() != null ? user.getRealName() : user.getUsername() : "未知用户");
        vo.setAuthorRole(user != null ? user.getRole() : "");
        vo.setAuthorAvatar(null);
        vo.setCreatedAt(post.getCreatedAt());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setBookmarkCount(post.getBookmarkCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setLiked(false);
        vo.setBookmarked(false);

        if (post.getImages() == null || post.getImages().trim().isEmpty()) {
            vo.setImages(List.of());
        } else {
            vo.setImages(List.of(post.getImages().split(",")));
        }

        return vo;
    }
}
