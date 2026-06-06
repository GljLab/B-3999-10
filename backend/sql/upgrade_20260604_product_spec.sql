-- 产品规格表和字段升级脚本
USE `agri_trace`;

-- 1. 为产品表添加图片URL字段
ALTER TABLE `product` ADD COLUMN `image_url` VARCHAR(255) COMMENT '产品主图访问路径' AFTER `harvest_date`;

-- 2. 为批次表添加规格ID字段
ALTER TABLE `product_batch` ADD COLUMN `spec_id` BIGINT COMMENT '关联规格ID' AFTER `product_id`;

-- 3. 创建产品规格表
DROP TABLE IF EXISTS `product_spec`;
CREATE TABLE `product_spec` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID (外键)',
  `spec_name` VARCHAR(50) NOT NULL COMMENT '规格名称 (如: 大果装, 中果装)',
  `weight` VARCHAR(50) NOT NULL COMMENT '单位重量 (如: 500g, 400g)',
  `suggested_price` DECIMAL(10,2) NOT NULL COMMENT '市场建议价',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_product_spec_name` (`product_id`, `spec_name`) USING BTREE,
  CONSTRAINT `fk_spec_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品规格价格表';
