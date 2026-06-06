package com.agritrace.repository;

import com.agritrace.entity.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByName(String name);

    List<Topic> findByStatusOrderBySortOrderAscCreatedAtDesc(Integer status);

    Page<Topic> findByStatus(Integer status, Pageable pageable);

    Page<Topic> findByStatusAndNameContainingOrDescriptionContaining(
            Integer status, String nameKeyword, String descKeyword, Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.status = 1 ORDER BY t.followCount DESC, t.postCount DESC")
    List<Topic> findHotTopics(Pageable pageable);

    @Query("SELECT t FROM Topic t WHERE t.status = 1 AND t.isFeatured = 1 ORDER BY t.sortOrder ASC")
    List<Topic> findFeaturedTopics();

    @Query("SELECT t FROM Topic t WHERE t.status = 1 AND " +
           "(t.name LIKE %:keyword% OR t.description LIKE %:keyword%) " +
           "ORDER BY t.sortOrder ASC, t.followCount DESC")
    List<Topic> searchTopics(@Param("keyword") String keyword);

    boolean existsByName(String name);

    List<Topic> findByIdIn(List<Long> ids);
}
