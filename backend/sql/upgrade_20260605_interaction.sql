USE `agri_trace`;

ALTER TABLE `community_post` ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT '认可数量' AFTER `view_count`;
ALTER TABLE `community_post` ADD COLUMN `bookmark_count` INT NOT NULL DEFAULT 0 COMMENT '收藏数量' AFTER `like_count`;
ALTER TABLE `community_post` ADD COLUMN `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数量' AFTER `bookmark_count`;

DROP TABLE IF EXISTS `post_like`;
CREATE TABLE `post_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `post_id` BIGINT NOT NULL COMMENT '帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_post_user_like`(`post_id`, `user_id`) USING BTREE,
  INDEX `idx_like_user`(`user_id`) USING BTREE,
  INDEX `idx_like_created`(`created_at`) USING BTREE,
  CONSTRAINT `fk_like_post` FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子认可表';

DROP TABLE IF EXISTS `post_bookmark`;
CREATE TABLE `post_bookmark` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `post_id` BIGINT NOT NULL COMMENT '帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_post_user_bookmark`(`post_id`, `user_id`) USING BTREE,
  INDEX `idx_bookmark_user`(`user_id`) USING BTREE,
  INDEX `idx_bookmark_created`(`created_at`) USING BTREE,
  CONSTRAINT `fk_bookmark_post` FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_bookmark_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子收藏表';

DROP TABLE IF EXISTS `post_comment`;
CREATE TABLE `post_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `post_id` BIGINT NOT NULL COMMENT '帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '评论用户ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID(回复某条评论时非空)',
  `content` VARCHAR(500) NOT NULL COMMENT '评论内容',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已删除(0否1是)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_comment_post`(`post_id`) USING BTREE,
  INDEX `idx_comment_user`(`user_id`) USING BTREE,
  INDEX `idx_comment_parent`(`parent_id`) USING BTREE,
  INDEX `idx_comment_created`(`created_at`) USING BTREE,
  CONSTRAINT `fk_comment_post` FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子评论表';
