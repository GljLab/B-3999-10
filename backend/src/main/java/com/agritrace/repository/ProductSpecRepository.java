package com.agritrace.repository;

import com.agritrace.entity.ProductSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSpecRepository extends JpaRepository<ProductSpec, Long> {
    List<ProductSpec> findByProductIdOrderByCreatedAtDesc(Long productId);
    Optional<ProductSpec> findByProductIdAndSpecName(Long productId, String specName);
    Optional<ProductSpec> findByProductIdAndSpecNameAndIdNot(Long productId, String specName, Long id);
    boolean existsByProductId(Long productId);
}
