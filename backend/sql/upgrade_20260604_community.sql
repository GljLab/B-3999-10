USE `agri_trace`;

DROP TABLE IF EXISTS `community_post`;
CREATE TABLE `community_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '作者用户ID (外键关联sys_user)',
  `title` VARCHAR(50) NOT NULL COMMENT '分享主题',
  `description` TEXT NOT NULL COMMENT '详细描述',
  `images` VARCHAR(1024) DEFAULT NULL COMMENT '配图地址集合(逗号分隔)',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '查看次数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_community_user`(`user_id`) USING BTREE,
  INDEX `idx_community_created`(`created_at`) USING BTREE,
  CONSTRAINT `fk_community_post_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区分享内容表';
