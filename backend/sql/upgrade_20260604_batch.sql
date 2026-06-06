-- 批次管理系统升级脚本
-- 执行日期: 2026-06-04
-- 功能: 添加产品批次管理功能

USE `agri_trace`;

-- ----------------------------
-- 1. 新增批次表 product_batch
-- ----------------------------
DROP TABLE IF EXISTS `product_batch`;
CREATE TABLE `product_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` BIGINT NOT NULL COMMENT '关联产品ID',
  `batch_no` VARCHAR(50) NOT NULL COMMENT '批次编号',
  `production_date` DATE NOT NULL COMMENT '生产日期/采摘日期',
  `quality_grade` VARCHAR(20) NOT NULL COMMENT '质量等级(特级/一级/普通等)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '批次备注说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_batch`(`product_id`, `batch_no`) USING BTREE,
  INDEX `idx_batch_product`(`product_id`) USING BTREE,
  CONSTRAINT `fk_batch_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='农产品生产批次表';

-- ----------------------------
-- 2. 为溯源码表增加批次关联字段
-- ----------------------------
ALTER TABLE `tracing_code` 
ADD COLUMN `batch_id` BIGINT NULL COMMENT '关联批次ID' AFTER `product_id`,
ADD INDEX `idx_tracing_batch`(`batch_id`) USING BTREE,
ADD CONSTRAINT `fk_tracing_batch` FOREIGN KEY (`batch_id`) REFERENCES `product_batch` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- ----------------------------
-- 3. 为老数据的溯源码创建默认批次 (历史数据兼容)
-- ----------------------------
-- 为已有溯源码的产品创建"早期批次"
INSERT INTO `product_batch` (`product_id`, `batch_no`, `production_date`, `quality_grade`, `remark`)
SELECT DISTINCT 
    tc.product_id,
    CONCAT('EARLY-', tc.product_id) as batch_no,
    COALESCE(p.harvest_date, CURDATE()) as production_date,
    '普通' as quality_grade,
    '早期批次' as remark
FROM `tracing_code` tc
INNER JOIN `product` p ON tc.product_id = p.id
WHERE tc.batch_id IS NULL;

-- 将老的溯源码关联到新建的批次
UPDATE `tracing_code` tc
INNER JOIN `product_batch` pb ON tc.product_id = pb.product_id AND pb.batch_no = CONCAT('EARLY-', tc.product_id)
SET tc.batch_id = pb.id
WHERE tc.batch_id IS NULL;
