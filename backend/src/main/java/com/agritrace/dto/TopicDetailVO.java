package com.agritrace.dto;

import com.agritrace.entity.Topic;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TopicDetailVO {
    private Long id;
    private String name;
    private String description;
    private String icon;
    private Integer postCount;
    private Integer followCount;
    private Integer isFeatured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean followed;
    private Integer userPostCount;
    private Double healthScore;

    public static TopicDetailVO from(Topic topic) {
        TopicDetailVO vo = new TopicDetailVO();
        vo.setId(topic.getId());
        vo.setName(topic.getName());
        vo.setDescription(topic.getDescription());
        vo.setIcon(topic.getIcon());
        vo.setPostCount(topic.getPostCount());
        vo.setFollowCount(topic.getFollowCount());
        vo.setIsFeatured(topic.getIsFeatured());
        vo.setCreatedAt(topic.getCreatedAt());
        vo.setUpdatedAt(topic.getUpdatedAt());
        vo.setFollowed(false);
        vo.setUserPostCount(0);
        vo.setHealthScore(0.0);
        return vo;
    }

    public static TopicDetailVO from(Topic topic, boolean followed) {
        TopicDetailVO vo = from(topic);
        vo.setFollowed(followed);
        return vo;
    }
}
