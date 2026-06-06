package com.agritrace.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BatchVO {
    private Long id;
    private Long productId;
    private String productName;
    private Long specId;
    private String specName;
    private String specWeight;
    private BigDecimal specSuggestedPrice;
    private String batchNo;
    private LocalDate productionDate;
    private String qualityGrade;
    private String remark;
    private Boolean hasTracingCode;
    private String currentTraceCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
