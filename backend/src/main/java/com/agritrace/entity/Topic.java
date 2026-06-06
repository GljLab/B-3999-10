package com.agritrace.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "topic")
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 100)
    private String icon;

    @Column(name = "post_count", nullable = false)
    private Integer postCount = 0;

    @Column(name = "follow_count", nullable = false)
    private Integer followCount = 0;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "is_featured", nullable = false)
    private Integer isFeatured = 0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (postCount == null) postCount = 0;
        if (followCount == null) followCount = 0;
        if (status == null) status = 1;
        if (isFeatured == null) isFeatured = 0;
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
