package com.agritrace.repository;

import com.agritrace.entity.UserTopicFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTopicFollowRepository extends JpaRepository<UserTopicFollow, Long> {

    Optional<UserTopicFollow> findByUserIdAndTopicId(Long userId, Long topicId);

    boolean existsByUserIdAndTopicId(Long userId, Long topicId);

    List<UserTopicFollow> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT utf.topicId FROM UserTopicFollow utf WHERE utf.userId = :userId")
    List<Long> findTopicIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM Topic t JOIN UserTopicFollow utf ON t.id = utf.topicId " +
           "WHERE utf.userId = :userId AND t.status = 1 ORDER BY utf.createdAt DESC")
    List<Object[]> findFollowedTopicsByUserId(@Param("userId") Long userId, Pageable pageable);

    Page<UserTopicFollow> findByUserId(Long userId, Pageable pageable);

    void deleteByUserIdAndTopicId(Long userId, Long topicId);

    void deleteByTopicId(Long topicId);

    long countByTopicId(Long topicId);

    @Query("SELECT COUNT(utf) FROM UserTopicFollow utf WHERE utf.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
}
