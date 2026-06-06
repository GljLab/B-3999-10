package com.agritrace.controller;

import com.agritrace.dto.*;
import com.agritrace.entity.*;
import com.agritrace.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private PostTopicRepository postTopicRepository;

    @Autowired
    private UserTopicFollowRepository userTopicFollowRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostBookmarkRepository postBookmarkRepository;

    private Long getCurrentUserId(HttpServletRequest request) {
        try {
            Object uid = request.getAttribute("userId");
            if (uid != null) {
                return ((Number) uid).longValue();
            }
        } catch (Exception ignored) {}
        return null;
    }

    @GetMapping
    public Result<Map<String, Object>> listTopics(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Long currentUserId = getCurrentUserId(request);
        List<Topic> topics;
        long total;

        if (!keyword.isEmpty()) {
            topics = topicRepository.searchTopics(keyword);
            total = topics.size();
        } else {
            switch (type) {
                case "hot":
                    Pageable hotPageable = PageRequest.of(0, 100);
                    topics = topicRepository.findHotTopics(hotPageable);
                    total = topics.size();
                    break;
                case "active":
                    List<Object[]> activeTopicData = postTopicRepository.findActiveTopicIdsLast7Days();
                    List<Long> activeTopicIds = activeTopicData.stream()
                            .map(arr -> ((Number) arr[0]).longValue())
                            .toList();
                    Map<Long, Integer> activeCountMap = new HashMap<>();
                    for (Object[] arr : activeTopicData) {
                        activeCountMap.put(((Number) arr[0]).longValue(), ((Number) arr[1]).intValue());
                    }
                    topics = topicRepository.findByIdIn(activeTopicIds).stream()
                            .filter(t -> t.getStatus() == 1)
                            .sorted((a, b) -> activeCountMap.getOrDefault(b.getId(), 0)
                                    - activeCountMap.getOrDefault(a.getId(), 0))
                            .toList();
                    total = topics.size();
                    break;
                case "followed":
                    if (currentUserId == null) {
                        topics = List.of();
                        total = 0;
                    } else {
                        List<Long> followedTopicIds = userTopicFollowRepository.findTopicIdsByUserId(currentUserId);
                        topics = topicRepository.findByIdIn(followedTopicIds).stream()
                                .filter(t -> t.getStatus() == 1)
                                .sorted(Comparator.comparing(Topic::getSortOrder)
                                        .thenComparing(Topic::getFollowCount).reversed())
                                .toList();
                        total = topics.size();
                    }
                    break;
                case "featured":
                    topics = topicRepository.findFeaturedTopics();
                    total = topics.size();
                    break;
                default:
                    topics = topicRepository.findByStatusOrderBySortOrderAscCreatedAtDesc(1);
                    total = topics.size();
            }
        }

        Set<Long> followedTopicIds = currentUserId != null
                ? new HashSet<>(userTopicFollowRepository.findTopicIdsByUserId(currentUserId))
                : Set.of();

        int fromIndex = Math.min(page * size, topics.size());
        int toIndex = Math.min((page + 1) * size, topics.size());
        List<Topic> pagedTopics = fromIndex < toIndex ? topics.subList(fromIndex, toIndex) : List.of();

        List<TopicVO> voList = pagedTopics.stream()
                .map(topic -> TopicVO.from(topic, followedTopicIds.contains(topic.getId())))
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", total);
        result.put("totalPages", (int) Math.ceil((double) total / size));
        result.put("currentPage", page);
        result.put("last", toIndex >= topics.size());

        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<TopicDetailVO> getTopicDetail(@PathVariable Long id, HttpServletRequest request) {
        Topic topic = topicRepository.findById(id).orElse(null);
        if (topic == null || topic.getStatus() == 0) {
            return Result.error(404, "话题不存在");
        }

        Long currentUserId = getCurrentUserId(request);
        boolean followed = currentUserId != null
                && userTopicFollowRepository.existsByUserIdAndTopicId(currentUserId, id);

        TopicDetailVO vo = TopicDetailVO.from(topic, followed);

        if (currentUserId != null) {
            List<Long> postIds = postTopicRepository.findPostIdsByTopicId(id);
            int userPostCount = (int) postIds.stream()
                    .map(pid -> communityPostRepository.findById(pid).orElse(null))
                    .filter(Objects::nonNull)
                    .filter(p -> p.getUserId().equals(currentUserId))
                    .count();
            vo.setUserPostCount(userPostCount);
        }

        double healthScore = calculateHealthScore(topic);
        vo.setHealthScore(healthScore);

        return Result.success(vo);
    }

    private double calculateHealthScore(Topic topic) {
        double score = 0.0;
        if (topic.getFollowCount() > 0) {
            double postFollowRatio = topic.getPostCount() / (double) topic.getFollowCount();
            score += Math.min(postFollowRatio * 50, 50);
        }
        if (topic.getPostCount() > 0) {
            score += 30;
        }
        if (topic.getFollowCount() > 10) {
            score += 20;
        }
        return Math.min(score, 100);
    }

    @GetMapping("/{id}/posts")
    public Result<Map<String, Object>> getTopicPosts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "smart") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Topic topic = topicRepository.findById(id).orElse(null);
        if (topic == null || topic.getStatus() == 0) {
            return Result.error(404, "话题不存在");
        }

        List<Long> postIds = postTopicRepository.findPostIdsByTopicId(id);
        List<CommunityPost> posts = postIds.stream()
                .map(pid -> communityPostRepository.findById(pid).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        posts = sortPosts(posts, sort);

        Long currentUserId = getCurrentUserId(request);
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> bookmarkedPostIds = new HashSet<>();

        int fromIndex = Math.min(page * size, posts.size());
        int toIndex = Math.min((page + 1) * size, posts.size());
        List<CommunityPost> pagedPosts = fromIndex < toIndex ? posts.subList(fromIndex, toIndex) : List.of();

        if (currentUserId != null && !pagedPosts.isEmpty()) {
            List<Long> currentPageIds = pagedPosts.stream().map(CommunityPost::getId).toList();
            likedPostIds = postLikeRepository.findByPostIdInAndUserId(currentPageIds, currentUserId)
                    .stream().map(PostLike::getPostId).collect(Collectors.toSet());
            bookmarkedPostIds = postBookmarkRepository.findByPostIdInAndUserId(currentPageIds, currentUserId)
                    .stream().map(PostBookmark::getPostId).collect(Collectors.toSet());
        }

        Set<Long> finalLikedPostIds = likedPostIds;
        Set<Long> finalBookmarkedPostIds = bookmarkedPostIds;
        Map<Long, List<Topic>> postTopicsMap = getPostTopicsMap(pagedPosts);

        List<CommunityPostVO> voList = pagedPosts.stream().map(post -> {
            User author = userRepository.findById(post.getUserId()).orElse(null);
            CommunityPostVO vo = CommunityPostVO.from(post, author);
            if (currentUserId != null) {
                vo.setLiked(finalLikedPostIds.contains(post.getId()));
                vo.setBookmarked(finalBookmarkedPostIds.contains(post.getId()));
            }
            List<Topic> postTopics = postTopicsMap.getOrDefault(post.getId(), List.of());
            vo.setTopics(postTopics.stream().map(TopicVO::from).toList());
            return vo;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", posts.size());
        result.put("totalPages", (int) Math.ceil((double) posts.size() / size));
        result.put("currentPage", page);
        result.put("last", toIndex >= posts.size());

        return Result.success(result);
    }

    private List<CommunityPost> sortPosts(List<CommunityPost> posts, String sort) {
        LocalDateTime now = LocalDateTime.now();
        return switch (sort) {
            case "latest" -> posts.stream()
                    .sorted(Comparator.comparing(CommunityPost::getCreatedAt).reversed())
                    .toList();
            case "popular" -> posts.stream()
                    .sorted(Comparator.comparingInt((CommunityPost p) ->
                            p.getLikeCount() + p.getBookmarkCount() * 2 + p.getCommentCount() * 3).reversed())
                    .toList();
            case "smart" -> posts.stream()
                    .sorted(Comparator.comparingDouble((CommunityPost p) ->
                            calculateSmartScore(p, now)).reversed())
                    .toList();
            default -> posts;
        };
    }

    private double calculateSmartScore(CommunityPost post, LocalDateTime now) {
        long hours = ChronoUnit.HOURS.between(post.getCreatedAt(), now);
        double timeWeight = 1.0;
        if (hours <= 24) timeWeight = 2.0;
        else if (hours <= 72) timeWeight = 1.5;
        else if (hours <= 168) timeWeight = 1.0;
        else timeWeight = 0.5;

        double interactionScore = post.getLikeCount() + post.getBookmarkCount() * 2.0 + post.getCommentCount() * 3.0;
        double qualityBonus = (post.getIsFeatured() != null && post.getIsFeatured() == 1) ? 10 : 0;

        return (interactionScore + qualityBonus) * timeWeight + Math.random() * 0.5;
    }

    private Map<Long, List<Topic>> getPostTopicsMap(List<CommunityPost> posts) {
        if (posts.isEmpty()) return Map.of();
        List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
        List<Long> topicIds = postTopicRepository.findTopicIdsByPostIdIn(postIds);
        List<Topic> allTopics = topicRepository.findByIdIn(topicIds);
        Map<Long, Topic> topicMap = allTopics.stream().collect(Collectors.toMap(Topic::getId, t -> t));

        Map<Long, List<Topic>> result = new HashMap<>();
        for (Long postId : postIds) {
            List<Long> pTopicIds = postTopicRepository.findTopicIdsByPostId(postId);
            List<Topic> pTopics = pTopicIds.stream()
                    .map(topicMap::get)
                    .filter(Objects::nonNull)
                    .toList();
            result.put(postId, pTopics);
        }
        return result;
    }

    @PostMapping("/{id}/follow")
    @Transactional
    public Result<Map<String, Object>> toggleFollow(@PathVariable Long id, HttpServletRequest request) {
        Topic topic = topicRepository.findById(id).orElse(null);
        if (topic == null || topic.getStatus() == 0) {
            return Result.error(404, "话题不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();

        Optional<UserTopicFollow> existing = userTopicFollowRepository.findByUserIdAndTopicId(currentUserId, id);
        boolean followed;
        if (existing.isPresent()) {
            userTopicFollowRepository.delete(existing.get());
            topic.setFollowCount(Math.max(0, topic.getFollowCount() - 1));
            followed = false;
        } else {
            UserTopicFollow follow = new UserTopicFollow();
            follow.setUserId(currentUserId);
            follow.setTopicId(id);
            userTopicFollowRepository.save(follow);
            topic.setFollowCount(topic.getFollowCount() + 1);
            followed = true;
        }
        topicRepository.save(topic);

        Map<String, Object> result = new HashMap<>();
        result.put("followed", followed);
        result.put("followCount", topic.getFollowCount());
        return Result.success(result);
    }

    @GetMapping("/my/followed")
    public Result<Map<String, Object>> getMyFollowedTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "followedAt") String sort,
            HttpServletRequest request) {

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserTopicFollow> followPage = userTopicFollowRepository.findByUserId(currentUserId, pageable);

        List<Long> topicIds = followPage.getContent().stream()
                .map(UserTopicFollow::getTopicId)
                .toList();
        List<Topic> topics = topicRepository.findByIdIn(topicIds);
        Map<Long, Topic> topicMap = topics.stream().collect(Collectors.toMap(Topic::getId, t -> t));

        List<TopicVO> voList = followPage.getContent().stream()
                .map(follow -> {
                    Topic topic = topicMap.get(follow.getTopicId());
                    if (topic == null) return null;
                    TopicVO vo = TopicVO.from(topic, true);
                    vo.setFollowedAt(follow.getCreatedAt());
                    return vo;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if ("hot".equals(sort)) {
            voList.sort((a, b) -> {
                int compare = Integer.compare(b.getFollowCount(), a.getFollowCount());
                if (compare != 0) return compare;
                return Integer.compare(b.getPostCount(), a.getPostCount());
            });
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", followPage.getTotalElements());
        result.put("totalPages", followPage.getTotalPages());
        result.put("currentPage", followPage.getNumber());
        result.put("last", followPage.isLast());

        return Result.success(result);
    }

    @GetMapping("/select-list")
    public Result<List<TopicVO>> getSelectList(HttpServletRequest request) {
        List<Topic> topics = topicRepository.findByStatusOrderBySortOrderAscCreatedAtDesc(1);
        Long currentUserId = getCurrentUserId(request);
        Set<Long> followedIds = currentUserId != null
                ? new HashSet<>(userTopicFollowRepository.findTopicIdsByUserId(currentUserId))
                : Set.of();

        List<TopicVO> voList = topics.stream()
                .map(t -> TopicVO.from(t, followedIds.contains(t.getId())))
                .toList();
        return Result.success(voList);
    }
}
