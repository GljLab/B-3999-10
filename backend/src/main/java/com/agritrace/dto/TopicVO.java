package com.agritrace.dto;

import com.agritrace.entity.Topic;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TopicVO {
    private Long id;
    private String name;
    private String description;
    private String icon;
    private Integer postCount;
    private Integer followCount;
    private Integer isFeatured;
    private LocalDateTime createdAt;
    private LocalDateTime followedAt;
    private Boolean followed;

    public static TopicVO from(Topic topic) {
        TopicVO vo = new TopicVO();
        vo.setId(topic.getId());
        vo.setName(topic.getName());
        vo.setDescription(topic.getDescription());
        vo.setIcon(topic.getIcon());
        vo.setPostCount(topic.getPostCount());
        vo.setFollowCount(topic.getFollowCount());
        vo.setIsFeatured(topic.getIsFeatured());
        vo.setCreatedAt(topic.getCreatedAt());
        vo.setFollowed(false);
        return vo;
    }

    public static TopicVO from(Topic topic, boolean followed) {
        TopicVO vo = from(topic);
        vo.setFollowed(followed);
        return vo;
    }
}
