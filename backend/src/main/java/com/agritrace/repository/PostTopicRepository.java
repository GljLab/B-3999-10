package com.agritrace.repository;

import com.agritrace.entity.PostTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface PostTopicRepository extends JpaRepository<PostTopic, Long> {

    List<PostTopic> findByPostId(Long postId);

    List<PostTopic> findByTopicId(Long topicId);

    @Query("SELECT pt.topicId FROM PostTopic pt WHERE pt.postId = :postId")
    List<Long> findTopicIdsByPostId(@Param("postId") Long postId);

    @Query("SELECT pt.postId FROM PostTopic pt WHERE pt.topicId = :topicId")
    List<Long> findPostIdsByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT pt.postId FROM PostTopic pt WHERE pt.topicId IN :topicIds")
    List<Long> findPostIdsByTopicIdIn(@Param("topicIds") List<Long> topicIds);

    @Query("SELECT t.id, t.name, t.icon, t.description, COUNT(pt.postId) as postCount " +
           "FROM Topic t LEFT JOIN PostTopic pt ON t.id = pt.topicId " +
           "WHERE pt.postId IN :postIds " +
           "GROUP BY t.id, t.name, t.icon, t.description")
    List<Object[]> countPostsByTopicForPostIds(@Param("postIds") List<Long> postIds);

    void deleteByPostId(Long postId);

    void deleteByTopicId(Long topicId);

    boolean existsByPostIdAndTopicId(Long postId, Long topicId);

    @Modifying
    @Query("DELETE FROM PostTopic pt WHERE pt.postId = :postId AND pt.topicId NOT IN :topicIds")
    void deleteByPostIdAndTopicIdNotIn(@Param("postId") Long postId, @Param("topicIds") Set<Long> topicIds);

    @Query("SELECT pt.topicId FROM PostTopic pt WHERE pt.postId IN :postIds")
    List<Long> findTopicIdsByPostIdIn(@Param("postIds") List<Long> postIds);

    @Query(value = "SELECT topic_id, COUNT(*) as cnt FROM post_topic " +
                   "WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
                   "GROUP BY topic_id ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> findActiveTopicIdsLast7Days();
}
