USE `agri_trace`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ----------------------------
-- 1. Table structure for topic : 话题标签表
-- ----------------------------
DROP TABLE IF EXISTS `topic`;
CREATE TABLE `topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(50) NOT NULL COMMENT '话题名称(唯一)',
  `description` VARCHAR(500) NOT NULL COMMENT '话题描述',
  `icon` VARCHAR(100) DEFAULT NULL COMMENT '话题图标(emoji或图片URL)',
  `post_count` INT NOT NULL DEFAULT 0 COMMENT '关联帖子数量',
  `follow_count` INT NOT NULL DEFAULT 0 COMMENT '关注用户数量',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
  `is_featured` TINYINT NOT NULL DEFAULT 0 COMMENT '是否精选: 1-是, 0-否',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_topic_name`(`name`) USING BTREE,
  INDEX `idx_topic_status`(`status`) USING BTREE,
  INDEX `idx_topic_featured`(`is_featured`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题标签表';

-- ----------------------------
-- 2. Table structure for post_topic : 帖子-话题关联表
-- ----------------------------
DROP TABLE IF EXISTS `post_topic`;
CREATE TABLE `post_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `post_id` BIGINT NOT NULL COMMENT '帖子ID',
  `topic_id` BIGINT NOT NULL COMMENT '话题ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_post_topic`(`post_id`, `topic_id`) USING BTREE,
  INDEX `idx_topic_id`(`topic_id`) USING BTREE,
  CONSTRAINT `fk_post_topic_post` FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_post_topic_topic` FOREIGN KEY (`topic_id`) REFERENCES `topic` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子-话题关联表';

-- ----------------------------
-- 3. Table structure for user_topic_follow : 用户关注话题表
-- ----------------------------
DROP TABLE IF EXISTS `user_topic_follow`;
CREATE TABLE `user_topic_follow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `topic_id` BIGINT NOT NULL COMMENT '话题ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_topic`(`user_id`, `topic_id`) USING BTREE,
  INDEX `idx_topic_id`(`topic_id`) USING BTREE,
  CONSTRAINT `fk_user_topic_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_topic_topic` FOREIGN KEY (`topic_id`) REFERENCES `topic` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注话题表';

-- ----------------------------
-- 4. Alter community_post table : 扩展帖子表
-- ----------------------------
ALTER TABLE `community_post` 
ADD COLUMN `is_featured` TINYINT NOT NULL DEFAULT 0 COMMENT '是否精选: 1-是, 0-否' AFTER `comment_count`,
ADD COLUMN `edited_at` DATETIME DEFAULT NULL COMMENT '最后编辑时间' AFTER `updated_at`,
ADD INDEX `idx_featured`(`is_featured`) USING BTREE;

-- ----------------------------
-- 5. Seed default topics : 预置核心话题标签
-- ----------------------------
INSERT INTO `topic` (`name`, `description`, `icon`, `sort_order`, `is_featured`) VALUES
('种植技术', '分享各类农作物的种植技巧、田间管理经验、高产栽培方法等', '🌱', 1, 1),
('有机种植', '探讨有机农业、绿色种植、无公害生产、生物防治等可持续农业实践', '🌿', 2, 1),
('病虫害防治', '交流农作物病虫害识别、防治方法、农药使用、生物防治等经验', '🐛', 3, 1),
('养殖经验', '分享畜禽、水产、特种养殖的技术、饲料、防疫、管理等经验', '🐄', 4, 1),
('农机设备', '讨论农业机械、设备选型、使用维护、智能化设备等话题', '🚜', 5, 1),
('市场行情', '分析农产品价格走势、市场供需、销售渠道、电商运营等', '📈', 6, 1),
('政策解读', '解读农业政策、补贴申请、项目申报、法规标准等信息', '📋', 7, 1),
('创业故事', '分享农业创业经历、成功案例、失败教训、心得体会', '💪', 8, 1),
('智慧农业', '探讨农业物联网、大数据、无人机、精准农业等新技术应用', '🤖', 9, 1),
('农产品加工', '交流农产品深加工、保鲜储存、包装设计、品牌打造等经验', '🏭', 10, 0),
('土壤肥料', '讨论土壤改良、测土配方、肥料选择、科学施肥等技术', '🌍', 11, 0),
('园艺花卉', '分享园艺技巧、花卉种植、盆景栽培、庭院设计等内容', '🌸', 12, 0);
