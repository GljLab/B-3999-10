package com.agritrace.repository;

import com.agritrace.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {
    List<ProductBatch> findByProductIdOrderByProductionDateDesc(Long productId);

    Optional<ProductBatch> findByProductIdAndBatchNo(Long productId, String batchNo);

    @Query("SELECT pb FROM ProductBatch pb WHERE pb.productId = :productId AND pb.id != :excludeId AND pb.batchNo = :batchNo")
    Optional<ProductBatch> findByProductIdAndBatchNoExcludeId(@Param("productId") Long productId, @Param("batchNo") String batchNo, @Param("excludeId") Long excludeId);

    @Query("SELECT pb FROM ProductBatch pb JOIN Product p ON pb.productId = p.id WHERE p.farmerId = :farmerId ORDER BY pb.productionDate DESC")
    List<ProductBatch> findByFarmerId(@Param("farmerId") Long farmerId);

    boolean existsByProductId(Long productId);

    boolean existsBySpecId(Long specId);
}
