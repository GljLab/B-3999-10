package com.agritrace.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class BatchRequest {
    private Long id;
    private Long productId;
    private Long specId;
    private String batchNo;
    private LocalDate productionDate;
    private String qualityGrade;
    private String remark;
}
