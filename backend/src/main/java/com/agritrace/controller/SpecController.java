package com.agritrace.controller;

import com.agritrace.dto.Result;
import com.agritrace.entity.Product;
import com.agritrace.entity.ProductSpec;
import com.agritrace.repository.ProductBatchRepository;
import com.agritrace.repository.ProductRepository;
import com.agritrace.repository.ProductSpecRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/farmer/spec")
public class SpecController {

    @Autowired
    private ProductSpecRepository specRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductBatchRepository batchRepository;

    @GetMapping("/list/{productId}")
    public Result<List<ProductSpec>> getSpecs(HttpServletRequest request, @PathVariable Long productId) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        return Result.success(specRepository.findByProductIdOrderByCreatedAtDesc(productId));
    }

    @PostMapping
    public Result<ProductSpec> createSpec(HttpServletRequest request, @RequestBody ProductSpec spec) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        Product product = productRepository.findById(spec.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        if (spec.getSpecName() == null || spec.getSpecName().trim().isEmpty()) {
            return Result.error(400, "规格名称不能为空");
        }

        if (spec.getWeight() == null || spec.getWeight().trim().isEmpty()) {
            return Result.error(400, "重量不能为空");
        }

        if (spec.getSuggestedPrice() == null || spec.getSuggestedPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error(400, "市场建议价必须大于0");
        }

        Optional<ProductSpec> existingSpec = specRepository.findByProductIdAndSpecName(
                spec.getProductId(), spec.getSpecName().trim());
        if (existingSpec.isPresent()) {
            return Result.error(400, "该产品下已存在相同名称的规格");
        }

        ProductSpec newSpec = new ProductSpec();
        newSpec.setProductId(spec.getProductId());
        newSpec.setSpecName(spec.getSpecName().trim());
        newSpec.setWeight(spec.getWeight().trim());
        newSpec.setSuggestedPrice(spec.getSuggestedPrice());

        newSpec = specRepository.save(newSpec);
        return Result.success(newSpec);
    }

    @PutMapping
    public Result<ProductSpec> updateSpec(HttpServletRequest request, @RequestBody ProductSpec spec) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        if (spec.getId() == null) {
            return Result.error(400, "规格ID不能为空");
        }

        ProductSpec existingSpec = specRepository.findById(spec.getId()).orElse(null);
        if (existingSpec == null) {
            return Result.error(404, "规格不存在");
        }

        Product product = productRepository.findById(existingSpec.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        if (spec.getSpecName() == null || spec.getSpecName().trim().isEmpty()) {
            return Result.error(400, "规格名称不能为空");
        }

        if (spec.getWeight() == null || spec.getWeight().trim().isEmpty()) {
            return Result.error(400, "重量不能为空");
        }

        if (spec.getSuggestedPrice() == null || spec.getSuggestedPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error(400, "市场建议价必须大于0");
        }

        Optional<ProductSpec> duplicateSpec = specRepository.findByProductIdAndSpecNameAndIdNot(
                existingSpec.getProductId(), spec.getSpecName().trim(), spec.getId());
        if (duplicateSpec.isPresent()) {
            return Result.error(400, "该产品下已存在相同名称的规格");
        }

        existingSpec.setSpecName(spec.getSpecName().trim());
        existingSpec.setWeight(spec.getWeight().trim());
        existingSpec.setSuggestedPrice(spec.getSuggestedPrice());

        existingSpec = specRepository.save(existingSpec);
        return Result.success(existingSpec);
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteSpec(HttpServletRequest request, @PathVariable Long id) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        ProductSpec spec = specRepository.findById(id).orElse(null);
        if (spec == null) {
            return Result.error(404, "规格不存在");
        }

        Product product = productRepository.findById(spec.getProductId()).orElse(null);
        if (product == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !product.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        boolean isUsed = batchRepository.existsBySpecId(id);
        if (isUsed) {
            return Result.error(400, "该规格已被批次使用，无法删除");
        }

        specRepository.deleteById(id);
        return Result.success(null);
    }
}
