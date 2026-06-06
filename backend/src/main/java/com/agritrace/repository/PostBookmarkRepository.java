package com.agritrace.repository;

import com.agritrace.entity.PostBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostBookmarkRepository extends JpaRepository<PostBookmark, Long> {
    Optional<PostBookmark> findByPostIdAndUserId(Long postId, Long userId);
    void deleteByPostIdAndUserId(Long postId, Long userId);
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    List<PostBookmark> findByPostIdInAndUserId(List<Long> postIds, Long userId);
    Page<PostBookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    void deleteByPostId(Long postId);

    @Query("SELECT b FROM PostBookmark b WHERE b.userId = :userId AND b.postId IN :postIds")
    List<PostBookmark> findByUserIdAndPostIdIn(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    @Query("SELECT b.postId FROM PostBookmark b WHERE b.userId = :userId")
    List<Long> findPostIdsByUserId(@Param("userId") Long userId);

    int countByUserId(Long userId);
}
