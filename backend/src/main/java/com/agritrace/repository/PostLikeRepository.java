package com.agritrace.repository;

import com.agritrace.entity.PostLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);
    void deleteByPostIdAndUserId(Long postId, Long userId);
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    List<PostLike> findByPostIdInAndUserId(List<Long> postIds, Long userId);
    Page<PostLike> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    void deleteByPostId(Long postId);

    @Query("SELECT l.postId FROM PostLike l WHERE l.userId = :userId")
    List<Long> findPostIdsByUserId(@Param("userId") Long userId);

    int countByUserId(Long userId);
}
