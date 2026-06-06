package com.agritrace.repository;

import com.agritrace.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    Page<PostComment> findByPostIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(Long postId, Pageable pageable);
    List<PostComment> findByPostIdAndParentIdNotNullAndDeletedFalseOrderByCreatedAtAsc(Long postId);
    List<PostComment> findByParentIdAndDeletedFalseOrderByCreatedAtAsc(Long parentId);
    long countByPostIdAndDeletedFalse(Long postId);
    Page<PostComment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    void deleteByPostId(Long postId);
    List<PostComment> findByPostId(Long postId);

    @Query("SELECT c FROM PostComment c WHERE c.deleted = false AND " +
           "(:username IS NULL OR EXISTS (SELECT 1 FROM User u WHERE u.id = c.userId AND u.username LIKE %:username%)) AND " +
           "(:postTitle IS NULL OR EXISTS (SELECT 1 FROM CommunityPost p WHERE p.id = c.postId AND p.title LIKE %:postTitle%)) AND " +
           "(:startDate IS NULL OR c.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR c.createdAt <= :endDate) " +
           "ORDER BY c.createdAt DESC")
    Page<PostComment> findAllWithFilters(@Param("username") String username,
                                         @Param("postTitle") String postTitle,
                                         @Param("startDate") java.time.LocalDateTime startDate,
                                         @Param("endDate") java.time.LocalDateTime endDate,
                                         Pageable pageable);

    boolean existsByParentIdAndDeletedFalse(Long parentId);

    @Query("SELECT c.postId FROM PostComment c WHERE c.userId = :userId AND c.deleted = false")
    List<Long> findPostIdsByUserId(@Param("userId") Long userId);

    int countByUserId(Long userId);
}
