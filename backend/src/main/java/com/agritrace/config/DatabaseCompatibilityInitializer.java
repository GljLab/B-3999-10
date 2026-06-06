package com.agritrace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCompatibilityInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseCompatibilityInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCompatibilityInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("sys_user")) {
            log.warn("Table `sys_user` not found, skip compatibility migration.");
            return;
        }

        if (!columnExists("sys_user", "enabled")) {
            jdbcTemplate.execute(
                    "ALTER TABLE `sys_user` " +
                    "ADD COLUMN `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态: 1-启用, 0-禁用' AFTER `role`"
            );
            log.info("Added missing column `sys_user.enabled` for backward compatibility.");
        }

        int updatedRows = jdbcTemplate.update(
                "UPDATE `sys_user` SET `enabled` = 1 WHERE `enabled` IS NULL"
        );
        if (updatedRows > 0) {
            log.info("Initialized {} rows with NULL `sys_user.enabled` to 1.", updatedRows);
        }

        if (!tableExists("product_batch")) {
            log.info("Creating `product_batch` table for batch management...");
            jdbcTemplate.execute(
                "CREATE TABLE `product_batch` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                "  `product_id` BIGINT NOT NULL COMMENT '关联产品ID'," +
                "  `batch_no` VARCHAR(50) NOT NULL COMMENT '批次编号'," +
                "  `production_date` DATE NOT NULL COMMENT '生产日期/采摘日期'," +
                "  `quality_grade` VARCHAR(20) NOT NULL COMMENT '质量等级(特级/一级/普通等)'," +
                "  `remark` VARCHAR(500) DEFAULT NULL COMMENT '批次备注说明'," +
                "  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "  PRIMARY KEY (`id`) USING BTREE," +
                "  UNIQUE INDEX `uk_product_batch`(`product_id`, `batch_no`) USING BTREE," +
                "  INDEX `idx_batch_product`(`product_id`) USING BTREE," +
                "  CONSTRAINT `fk_batch_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE ON UPDATE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='农产品生产批次表'"
            );
            log.info("Table `product_batch` created successfully.");
        }

        if (!columnExists("tracing_code", "batch_id")) {
            log.info("Adding `batch_id` column to `tracing_code` table...");
            jdbcTemplate.execute(
                "ALTER TABLE `tracing_code` " +
                "ADD COLUMN `batch_id` BIGINT NULL COMMENT '关联批次ID' AFTER `product_id`, " +
                "ADD INDEX `idx_tracing_batch`(`batch_id`) USING BTREE"
            );
            log.info("Column `batch_id` added to `tracing_code` table successfully.");
        }

        migrateLegacyTracingCodes();
    }

    private void migrateLegacyTracingCodes() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `tracing_code` WHERE `batch_id` IS NULL",
            Integer.class
        );
        
        if (count != null && count > 0) {
            log.info("Found {} legacy tracing codes without batch association. Starting migration...", count);
            
            jdbcTemplate.execute(
                "INSERT IGNORE INTO `product_batch` (`product_id`, `batch_no`, `production_date`, `quality_grade`, `remark`) " +
                "SELECT DISTINCT tc.product_id, CONCAT('EARLY-', tc.product_id), " +
                "COALESCE(p.harvest_date, CURDATE()), '普通', '早期批次' " +
                "FROM `tracing_code` tc " +
                "INNER JOIN `product` p ON tc.product_id = p.id " +
                "WHERE tc.batch_id IS NULL"
            );
            
            int migrated = jdbcTemplate.update(
                "UPDATE `tracing_code` tc " +
                "INNER JOIN `product_batch` pb ON tc.product_id = pb.product_id AND pb.batch_no = CONCAT('EARLY-', tc.product_id) " +
                "SET tc.batch_id = pb.id " +
                "WHERE tc.batch_id IS NULL"
            );
            
            log.info("Successfully migrated {} legacy tracing codes to batch system.", migrated);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
