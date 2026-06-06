package com.agritrace.repository;

import com.agritrace.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    Page<CommunityPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<CommunityPost> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT p FROM CommunityPost p WHERE p.createdAt BETWEEN :start AND :end ORDER BY p.createdAt DESC")
    Page<CommunityPost> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT p FROM CommunityPost p JOIN com.agritrace.entity.User u ON p.userId = u.id WHERE u.username LIKE %:username% ORDER BY p.createdAt DESC")
    Page<CommunityPost> findByAuthorUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT p FROM CommunityPost p JOIN com.agritrace.entity.User u ON p.userId = u.id WHERE u.username LIKE %:username% AND p.createdAt BETWEEN :start AND :end ORDER BY p.createdAt DESC")
    Page<CommunityPost> findByAuthorUsernameAndCreatedAtBetween(@Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT p.userId) FROM CommunityPost p")
    long countDistinctUserId();

    @Query("SELECT COUNT(DISTINCT p.userId) FROM CommunityPost p WHERE p.createdAt BETWEEN :start AND :end")
    long countDistinctUserIdByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
