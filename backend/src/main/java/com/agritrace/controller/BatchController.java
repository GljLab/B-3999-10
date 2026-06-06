package com.agritrace.controller;

import com.agritrace.dto.BatchRequest;
import com.agritrace.dto.BatchVO;
import com.agritrace.dto.Result;
import com.agritrace.entity.Product;
import com.agritrace.entity.ProductBatch;
import com.agritrace.entity.ProductSpec;
import com.agritrace.entity.TracingCode;
import com.agritrace.repository.ProductBatchRepository;
import com.agritrace.repository.ProductRepository;
import com.agritrace.repository.ProductSpecRepository;
import com.agritrace.repository.TracingCodeRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/farmer/batch")
public class BatchController {

    @Autowired
    private ProductBatchRepository batchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpecRepository specRepository;

    @Autowired
    private TracingCodeRepository tracingCodeRepository;

    @GetMapping("/list/{productId}")
    public Result<List<BatchVO>> getBatches(HttpServletRequest request, @PathVariable Long productId) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        List<ProductBatch> batches = batchRepository.findByProductIdOrderByProductionDateDesc(productId);
        List<BatchVO> batchVOs = new ArrayList<>();
        for (ProductBatch batch : batches) {
            batchVOs.add(convertToVO(batch, product));
        }

        return Result.success(batchVOs);
    }

    @GetMapping("/{id}")
    public Result<BatchVO> getBatch(HttpServletRequest request, @PathVariable Long id) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        ProductBatch batch = batchRepository.findById(id).orElse(null);
        if (batch == null) {
            return Result.error(404, "Batch not found");
        }

        Product product = productRepository.findById(batch.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        return Result.success(convertToVO(batch, product));
    }

    @PostMapping
    public Result<BatchVO> createBatch(HttpServletRequest request, @RequestBody BatchRequest batchRequest) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        Product product = productRepository.findById(batchRequest.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        ProductSpec spec = null;
        if (batchRequest.getSpecId() != null) {
            spec = specRepository.findById(batchRequest.getSpecId()).orElse(null);
            if (spec == null || !spec.getProductId().equals(batchRequest.getProductId())) {
                return Result.error(400, "无效的规格选择");
            }
        }

        if (batchRequest.getBatchNo() == null || batchRequest.getBatchNo().trim().isEmpty()) {
            return Result.error(400, "Batch number cannot be empty");
        }

        if (batchRequest.getProductionDate() == null) {
            return Result.error(400, "Production date cannot be empty");
        }

        if (batchRequest.getProductionDate().isAfter(LocalDate.now())) {
            return Result.error(400, "Production date cannot be in the future");
        }

        if (batchRequest.getQualityGrade() == null || batchRequest.getQualityGrade().trim().isEmpty()) {
            return Result.error(400, "Quality grade cannot be empty");
        }

        Optional<ProductBatch> existingBatch = batchRepository.findByProductIdAndBatchNo(
                batchRequest.getProductId(), batchRequest.getBatchNo());
        if (existingBatch.isPresent()) {
            return Result.error(400, "Batch number already exists for this product");
        }

        ProductBatch batch = new ProductBatch();
        batch.setProductId(batchRequest.getProductId());
        batch.setSpecId(batchRequest.getSpecId());
        batch.setBatchNo(batchRequest.getBatchNo().trim());
        batch.setProductionDate(batchRequest.getProductionDate());
        batch.setQualityGrade(batchRequest.getQualityGrade().trim());
        batch.setRemark(batchRequest.getRemark() != null ? batchRequest.getRemark().trim() : null);

        batch = batchRepository.save(batch);

        return Result.success(convertToVO(batch, product));
    }

    @PutMapping
    public Result<BatchVO> updateBatch(HttpServletRequest request, @RequestBody BatchRequest batchRequest) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        if (batchRequest.getId() == null) {
            return Result.error(400, "Batch ID cannot be empty");
        }

        ProductBatch batch = batchRepository.findById(batchRequest.getId()).orElse(null);
        if (batch == null) {
            return Result.error(404, "Batch not found");
        }

        Product product = productRepository.findById(batch.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        ProductSpec spec = null;
        if (batchRequest.getSpecId() != null) {
            spec = specRepository.findById(batchRequest.getSpecId()).orElse(null);
            if (spec == null || !spec.getProductId().equals(batch.getProductId())) {
                return Result.error(400, "无效的规格选择");
            }
        }

        if (batchRequest.getBatchNo() == null || batchRequest.getBatchNo().trim().isEmpty()) {
            return Result.error(400, "Batch number cannot be empty");
        }

        if (batchRequest.getProductionDate() == null) {
            return Result.error(400, "Production date cannot be empty");
        }

        if (batchRequest.getProductionDate().isAfter(LocalDate.now())) {
            return Result.error(400, "Production date cannot be in the future");
        }

        if (batchRequest.getQualityGrade() == null || batchRequest.getQualityGrade().trim().isEmpty()) {
            return Result.error(400, "Quality grade cannot be empty");
        }

        Optional<ProductBatch> existingBatch = batchRepository.findByProductIdAndBatchNoExcludeId(
                batch.getProductId(), batchRequest.getBatchNo(), batch.getId());
        if (existingBatch.isPresent()) {
            return Result.error(400, "Batch number already exists for this product");
        }

        batch.setSpecId(batchRequest.getSpecId());
        batch.setBatchNo(batchRequest.getBatchNo().trim());
        batch.setProductionDate(batchRequest.getProductionDate());
        batch.setQualityGrade(batchRequest.getQualityGrade().trim());
        batch.setRemark(batchRequest.getRemark() != null ? batchRequest.getRemark().trim() : null);

        batch = batchRepository.save(batch);

        return Result.success(convertToVO(batch, product));
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteBatch(HttpServletRequest request, @PathVariable Long id) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        ProductBatch batch = batchRepository.findById(id).orElse(null);
        if (batch == null) {
            return Result.error(404, "Batch not found");
        }

        Product product = productRepository.findById(batch.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        boolean hasTracingCode = tracingCodeRepository.existsByBatchId(id);
        if (hasTracingCode) {
            return Result.error(400, "Cannot delete batch with existing tracing codes. Please invalidate them first.");
        }

        batchRepository.deleteById(id);

        return Result.success(null);
    }

    @PostMapping("/trace_code/{batchId}")
    public Result<TracingCode> generateTraceCode(HttpServletRequest request, @PathVariable Long batchId) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        ProductBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return Result.error(404, "Batch not found");
        }

        Product product = productRepository.findById(batch.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        List<TracingCode> existingCodes = tracingCodeRepository.findByBatchId(batchId);
        for (TracingCode code : existingCodes) {
            if (code.getStatus() == 1) {
                code.setStatus(0);
                tracingCodeRepository.save(code);
            }
        }

        TracingCode tc = new TracingCode();
        tc.setProductId(product.getId());
        tc.setBatchId(batchId);
        tc.setTraceCode(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        tc.setStatus(1);
        tracingCodeRepository.save(tc);

        return Result.success(tc);
    }

    @GetMapping("/all")
    public Result<List<BatchVO>> getAllBatches(HttpServletRequest request) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        List<ProductBatch> batches;
        if ("SYS_ADMIN".equals(role)) {
            batches = batchRepository.findAll();
        } else {
            batches = batchRepository.findByFarmerId(userId);
        }

        List<BatchVO> batchVOs = new ArrayList<>();
        for (ProductBatch batch : batches) {
            Product product = productRepository.findById(batch.getProductId()).orElse(null);
            if (product != null) {
                batchVOs.add(convertToVO(batch, product));
            }
        }

        return Result.success(batchVOs);
    }

    private BatchVO convertToVO(ProductBatch batch, Product product) {
        BatchVO vo = new BatchVO();
        vo.setId(batch.getId());
        vo.setProductId(batch.getProductId());
        vo.setProductName(product.getProductName());
        vo.setBatchNo(batch.getBatchNo());
        vo.setProductionDate(batch.getProductionDate());
        vo.setQualityGrade(batch.getQualityGrade());
        vo.setRemark(batch.getRemark());
        vo.setCreatedAt(batch.getCreatedAt());
        vo.setUpdatedAt(batch.getUpdatedAt());

        vo.setSpecId(batch.getSpecId());
        if (batch.getSpecId() != null) {
            ProductSpec spec = specRepository.findById(batch.getSpecId()).orElse(null);
            if (spec != null) {
                vo.setSpecName(spec.getSpecName());
                vo.setSpecWeight(spec.getWeight());
                vo.setSpecSuggestedPrice(spec.getSuggestedPrice());
            }
        }

        boolean hasTracingCode = tracingCodeRepository.existsByBatchId(batch.getId());
        vo.setHasTracingCode(hasTracingCode);

        if (hasTracingCode) {
            Optional<TracingCode> latestCode = tracingCodeRepository.findTopByBatchIdOrderByGeneratedAtDesc(batch.getId());
            latestCode.ifPresent(tracingCode -> vo.setCurrentTraceCode(tracingCode.getTraceCode()));
        }

        return vo;
    }
}
