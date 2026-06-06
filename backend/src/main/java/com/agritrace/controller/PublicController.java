package com.agritrace.controller;
import com.agritrace.dto.Result;
import com.agritrace.entity.HotProduct;
import com.agritrace.entity.Logistics;
import com.agritrace.entity.Product;
import com.agritrace.entity.ProductBatch;
import com.agritrace.entity.ProductSpec;
import com.agritrace.entity.TracingCode;
import com.agritrace.repository.HotProductRepository;
import com.agritrace.repository.LogisticsRepository;
import com.agritrace.repository.ProductBatchRepository;
import com.agritrace.repository.ProductRepository;
import com.agritrace.repository.ProductSpecRepository;
import com.agritrace.repository.TracingCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    @Autowired private HotProductRepository hotProductRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private TracingCodeRepository tracingCodeRepository;
    @Autowired private LogisticsRepository logisticsRepository;
    @Autowired private ProductBatchRepository batchRepository;
    @Autowired private ProductSpecRepository specRepository;

    @GetMapping("/hot")
    public Result<?> getHotProducts() {
        List<HotProduct> hots = hotProductRepository.findByIsDisplayOrderBySearchCountDesc(1);
        List<Map<String, Object>> res = new ArrayList<>();
        for (HotProduct hp : hots) {
            Product p = productRepository.findById(hp.getProductId()).orElse(null);
            if (p != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("productName", p.getProductName());
                map.put("category", p.getCategory());
                map.put("origin", p.getOrigin());
                map.put("imageUrl", p.getImageUrl());
                map.put("searchCount", hp.getSearchCount());
                res.add(map);
            }
        }
        return Result.success(res);
    }
    
    @GetMapping("/trace/{code}")
    public Result<?> traceCode(@PathVariable String code) {
        Optional<TracingCode> opt = tracingCodeRepository.findByTraceCode(code);
        if (opt.isEmpty()) return Result.error(404, "Invalid tracing code");
        
        TracingCode tc = opt.get();
        Product p = productRepository.findById(tc.getProductId()).orElse(null);
        List<Logistics> logs = logisticsRepository.findByTraceCodeId(tc.getId());
        
        ProductBatch batch = null;
        ProductSpec spec = null;
        if (tc.getBatchId() != null) {
            batch = batchRepository.findById(tc.getBatchId()).orElse(null);
            if (batch != null && batch.getSpecId() != null) {
                spec = specRepository.findById(batch.getSpecId()).orElse(null);
            }
        }

        if (p != null && p.getDescription() != null) {
            p.setDescription(sanitizeHtml(p.getDescription()));
        }
        
        if (p != null) {
            HotProduct hp = hotProductRepository.findByProductId(p.getId()).orElse(null);
            if (hp != null) {
                hp.setSearchCount(hp.getSearchCount() + 1);
                hotProductRepository.save(hp);
            } else {
                hp = new HotProduct();
                hp.setProductId(p.getId());
                hp.setSearchCount(1);
                hp.setIsDisplay(1);
                hotProductRepository.save(hp);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("product", p);
        data.put("logistics", logs);
        data.put("traceInfo", tc);
        data.put("batch", batch);
        data.put("spec", spec);
        return Result.success(data);
    }
    
    @GetMapping("/hash")
    public Result<?> getHash() {
        return Result.success(at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(10, "123456".toCharArray()));
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
