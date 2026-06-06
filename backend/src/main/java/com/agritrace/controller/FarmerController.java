package com.agritrace.controller;
import com.agritrace.dto.Result;
import com.agritrace.entity.Product;
import com.agritrace.entity.TracingCode;
import com.agritrace.repository.HotProductRepository;
import com.agritrace.repository.ProductRepository;
import com.agritrace.repository.TracingCodeRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/farmer")
public class FarmerController {
    @Autowired private ProductRepository productRepository;
    @Autowired private TracingCodeRepository tracingCodeRepository;
    @Autowired private HotProductRepository hotProductRepository;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @GetMapping("/products")
    public Result<List<Product>> getProducts(HttpServletRequest request) {
        Long userId = ((Number)request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");
        if ("SYS_ADMIN".equals(role)) {
            return Result.success(productRepository.findAll());
        }
        return Result.success(productRepository.findByFarmerId(userId));
    }

    @GetMapping("/product/{id}")
    public Result<Product> getProduct(HttpServletRequest request, @PathVariable Long id) {
        Long userId = ((Number)request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");
        Product p = productRepository.findById(id).orElse(null);
        if (p == null) return Result.error(404, "Product not found");
        if (!"SYS_ADMIN".equals(role) && !p.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }
        return Result.success(p);
    }

    @PostMapping("/product")
    public Result<?> addProduct(HttpServletRequest request, @RequestBody Product product) {
        Long userId = ((Number)request.getAttribute("userId")).longValue();
        product.setFarmerId(userId);
        if (product.getDescription() != null) {
            product.setDescription(sanitizeHtml(product.getDescription()));
        }
        productRepository.save(product);
        return Result.success(product);
    }

    @PutMapping("/product")
    public Result<?> updateProduct(HttpServletRequest request, @RequestBody Product product) {
        Long userId = ((Number)request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        if (product.getId() == null) {
            return Result.error(400, "Product ID cannot be empty");
        }

        Product existingProduct = productRepository.findById(product.getId()).orElse(null);
        if (existingProduct == null) {
            return Result.error(404, "Product not found");
        }

        if (!"SYS_ADMIN".equals(role) && !existingProduct.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }

        if (product.getProductName() != null) {
            existingProduct.setProductName(product.getProductName());
        }
        if (product.getCategory() != null) {
            existingProduct.setCategory(product.getCategory());
        }
        if (product.getOrigin() != null) {
            existingProduct.setOrigin(product.getOrigin());
        }
        if (product.getDescription() != null) {
            existingProduct.setDescription(sanitizeHtml(product.getDescription()));
        }
        if (product.getHarvestDate() != null) {
            existingProduct.setHarvestDate(product.getHarvestDate());
        }
        if (product.getImageUrl() != null) {
            existingProduct.setImageUrl(product.getImageUrl());
        }

        productRepository.save(existingProduct);
        return Result.success(existingProduct);
    }

    @PostMapping("/product/image")
    public Result<?> uploadImage(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "请选择要上传的图片");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return Result.error(400, "只支持JPG、PNG格式的图片");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error(400, "图片大小不能超过5MB");
        }

        try {
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String newFilename = UUID.randomUUID().toString() + extension;
            File destFile = new File(uploadDir, newFilename);
            file.transferTo(destFile);

            String imageUrl = "/api/uploads/" + newFilename;
            return Result.success(imageUrl);
        } catch (IOException e) {
            return Result.error(500, "图片上传失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/product/{id}")
    public Result<?> deleteProduct(HttpServletRequest request, @PathVariable Long id) {
        Long userId = ((Number)request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");
        Product p = productRepository.findById(id).orElse(null);
        if (p == null) return Result.error(404, "Product not found");
        if (!"SYS_ADMIN".equals(role) && !p.getFarmerId().equals(userId)) {
            return Result.error(403, "Permission denied");
        }
        productRepository.deleteById(id);
        return Result.success(null);
    }

    @PostMapping("/trace_code/{productId}")
    public Result<?> generateTraceCode(HttpServletRequest request, @PathVariable Long productId) {
        return Result.error(400, "Please create a batch first, then generate tracing code in batch management.");
    }
    
    @GetMapping("/trace_code/list/{productId}")
    public Result<List<TracingCode>> getTracingCodes(@PathVariable Long productId) {
        return Result.success(tracingCodeRepository.findByProductId(productId));
    }

    private String sanitizeHtml(String html) {
        if (html == null) return null;
        html = html.replaceAll("<script[^>]*>.*?</script>", "");
        html = html.replaceAll("javascript:", "");
        html = html.replaceAll("on\\w+\\s*=", "");
        html = html.replaceAll("<iframe[^>]*>.*?</iframe>", "");
        return html;
    }
}
