package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommunityPostVO {
    private Long id;
    private Long authorId;
    private String title;
    private String description;
    private String coverImage;
    private String authorName;
    private String authorRole;
    private LocalDateTime createdAt;
    private Integer viewCount;
    private Integer likeCount;
    private Integer bookmarkCount;
    private Integer commentCount;
    private Boolean liked;
    private Boolean bookmarked;
    private List<TopicVO> topics;
    private Integer isFeatured;

    public static CommunityPostVO from(com.agritrace.entity.CommunityPost post, com.agritrace.entity.User user) {
        CommunityPostVO vo = new CommunityPostVO();
        vo.setId(post.getId());
        vo.setAuthorId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setDescription(post.getDescription());
        vo.setAuthorName(user != null ? user.getRealName() != null ? user.getRealName() : user.getUsername() : "未知用户");
        vo.setAuthorRole(user != null ? user.getRole() : "");
        vo.setCreatedAt(post.getCreatedAt());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setBookmarkCount(post.getBookmarkCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setLiked(false);
        vo.setBookmarked(false);
        vo.setIsFeatured(post.getIsFeatured());
        vo.setTopics(List.of());

        List<String> imageList = parseImages(post.getImages());
        if (!imageList.isEmpty()) {
            vo.setCoverImage(imageList.get(0));
        } else {
            vo.setCoverImage(null);
        }

        return vo;
    }

    static List<String> parseImages(String images) {
        if (images == null || images.trim().isEmpty()) {
            return List.of();
        }
        return List.of(images.split(","));
    }
}
