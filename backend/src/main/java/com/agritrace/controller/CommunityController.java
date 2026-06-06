package com.agritrace.controller;

import com.agritrace.dto.*;
import com.agritrace.entity.*;
import com.agritrace.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/community")
public class CommunityController {

    @Autowired
    private CommunityPostRepository communityPostRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostBookmarkRepository postBookmarkRepository;
    @Autowired
    private PostCommentRepository postCommentRepository;
    @Autowired
    private PostTopicRepository postTopicRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private UserTopicFollowRepository userTopicFollowRepository;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    private Long getCurrentUserId(HttpServletRequest request) {
        try {
            Object uid = request.getAttribute("userId");
            if (uid != null) {
                return ((Number) uid).longValue();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getUserRole(HttpServletRequest request) {
        try {
            return (String) request.getAttribute("role");
        } catch (Exception ignored) {}
        return null;
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

    @GetMapping("/posts")
    public Result<Map<String, Object>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timeline") String tab,
            HttpServletRequest request) {

        Long currentUserId = getCurrentUserId(request);
        Page<CommunityPost> postPage;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if ("followed".equals(tab) && currentUserId != null) {
            List<Long> followedTopicIds = userTopicFollowRepository.findTopicIdsByUserId(currentUserId);
            if (followedTopicIds.isEmpty()) {
                Map<String, Object> emptyResult = new HashMap<>();
                emptyResult.put("content", List.of());
                emptyResult.put("totalElements", 0);
                emptyResult.put("totalPages", 0);
                emptyResult.put("currentPage", 0);
                emptyResult.put("last", true);
                return Result.success(emptyResult);
            }
            List<Long> postIds = postTopicRepository.findPostIdsByTopicIdIn(followedTopicIds);
            Set<Long> postIdSet = new HashSet<>(postIds);
            List<CommunityPost> allPosts = postIdSet.stream()
                    .map(id -> communityPostRepository.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .filter(p -> ChronoUnit.DAYS.between(p.getCreatedAt(), LocalDateTime.now()) <= 30)
                    .sorted(Comparator.comparing(CommunityPost::getCreatedAt).reversed())
                    .toList();

            int from = Math.min(page * size, allPosts.size());
            int to = Math.min((page + 1) * size, allPosts.size());
            List<CommunityPost> pagedPosts = from < to ? allPosts.subList(from, to) : List.of();

            return buildPostListResult(pagedPosts, allPosts.size(), page, size, currentUserId);
        } else if ("recommend".equals(tab)) {
            Pageable allPageable = PageRequest.of(0, 300, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<CommunityPost> allPostPage = communityPostRepository.findAllByOrderByCreatedAtDesc(allPageable);
            List<CommunityPost> allPosts = allPostPage.getContent();

            allPosts = allPosts.stream()
                    .filter(p -> ChronoUnit.DAYS.between(p.getCreatedAt(), LocalDateTime.now()) <= 7)
                    .toList();

            List<CommunityPost> finalPosts = allPosts;
            List<CommunityPost> recommendedPosts;

            if (currentUserId != null) {
                Map<Long, Double> topicInterestScores = calculateUserTopicInterests(currentUserId);
                int interactionCount = countUserInteractions(currentUserId);

                if (interactionCount >= 10 && !topicInterestScores.isEmpty()) {
                    recommendedPosts = finalPosts.stream()
                            .sorted(Comparator.comparingDouble((CommunityPost p) ->
                                    calculatePersonalizedScore(p, topicInterestScores, LocalDateTime.now())).reversed())
                            .toList();
                } else {
                    recommendedPosts = finalPosts.stream()
                            .sorted(Comparator.comparingDouble((CommunityPost p) ->
                                    calculatePostScore(p, LocalDateTime.now())).reversed())
                            .toList();
                }
            } else {
                recommendedPosts = finalPosts.stream()
                        .sorted(Comparator.comparingDouble((CommunityPost p) ->
                                calculatePostScore(p, LocalDateTime.now())).reversed())
                        .toList();
            }

            Set<Long> usedTopics = new HashSet<>();
            List<CommunityPost> diversePosts = new ArrayList<>();
            for (CommunityPost post : recommendedPosts) {
                List<Long> postTopicIds = postTopicRepository.findTopicIdsByPostId(post.getId());
                boolean hasNewTopic = postTopicIds.stream().anyMatch(tid -> !usedTopics.contains(tid));
                if (hasNewTopic || diversePosts.size() < 5) {
                    diversePosts.add(post);
                    usedTopics.addAll(postTopicIds);
                }
                if (diversePosts.size() >= 50) break;
            }

            int from = Math.min(page * size, diversePosts.size());
            int to = Math.min((page + 1) * size, diversePosts.size());
            List<CommunityPost> pagedPosts = from < to ? diversePosts.subList(from, to) : List.of();

            return buildPostListResult(pagedPosts, diversePosts.size(), page, size, currentUserId);
        } else {
            postPage = communityPostRepository.findAllByOrderByCreatedAtDesc(pageable);
            return buildPostListResult(postPage.getContent(), postPage.getTotalElements(), page, size, currentUserId);
        }
    }

    private double calculatePostScore(CommunityPost post, LocalDateTime now) {
        long hours = ChronoUnit.HOURS.between(post.getCreatedAt(), now);
        double timeWeight = 1.0;
        if (hours <= 24) timeWeight = 2.0;
        else if (hours <= 72) timeWeight = 1.5;
        else if (hours <= 168) timeWeight = 1.0;
        else timeWeight = 0.5;

        double interactionScore = post.getViewCount() * 0.1 + post.getLikeCount()
                + post.getBookmarkCount() * 2.0 + post.getCommentCount() * 3.0;
        double qualityBonus = (post.getIsFeatured() != null && post.getIsFeatured() == 1) ? 10 : 0;

        return (interactionScore + qualityBonus) * timeWeight + Math.random() * 0.5;
    }

    private Map<Long, Double> calculateUserTopicInterests(Long userId) {
        Map<Long, Double> interestScores = new HashMap<>();

        List<Long> likedPostIds = postLikeRepository.findPostIdsByUserId(userId);
        for (Long postId : likedPostIds) {
            List<Long> topicIds = postTopicRepository.findTopicIdsByPostId(postId);
            for (Long topicId : topicIds) {
                interestScores.merge(topicId, 2.0, Double::sum);
            }
        }

        List<Long> bookmarkedPostIds = postBookmarkRepository.findPostIdsByUserId(userId);
        for (Long postId : bookmarkedPostIds) {
            List<Long> topicIds = postTopicRepository.findTopicIdsByPostId(postId);
            for (Long topicId : topicIds) {
                interestScores.merge(topicId, 3.0, Double::sum);
            }
        }

        List<Long> commentedPostIds = postCommentRepository.findPostIdsByUserId(userId);
        for (Long postId : commentedPostIds) {
            List<Long> topicIds = postTopicRepository.findTopicIdsByPostId(postId);
            for (Long topicId : topicIds) {
                interestScores.merge(topicId, 1.5, Double::sum);
            }
        }

        return interestScores;
    }

    private int countUserInteractions(Long userId) {
        int likes = postLikeRepository.countByUserId(userId);
        int bookmarks = postBookmarkRepository.countByUserId(userId);
        int comments = postCommentRepository.countByUserId(userId);
        return likes + bookmarks + comments;
    }

    private double calculatePersonalizedScore(CommunityPost post, Map<Long, Double> topicInterestScores, LocalDateTime now) {
        double baseScore = calculatePostScore(post, now);

        List<Long> postTopicIds = postTopicRepository.findTopicIdsByPostId(post.getId());
        double interestBonus = 0;
        for (Long topicId : postTopicIds) {
            interestBonus += topicInterestScores.getOrDefault(topicId, 0.0);
        }

        double discoveryBoost = postTopicIds.stream()
                .filter(tid -> !topicInterestScores.containsKey(tid))
                .count() > 0 ? 1.5 : 0;

        return baseScore + interestBonus * 2 + discoveryBoost + Math.random() * 0.3;
    }

    private Result<Map<String, Object>> buildPostListResult(
            List<CommunityPost> posts, long total, int page, int size, Long currentUserId) {

        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> bookmarkedPostIds = new HashSet<>();

        if (currentUserId != null && !posts.isEmpty()) {
            List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
            likedPostIds = postLikeRepository.findByPostIdInAndUserId(postIds, currentUserId)
                    .stream().map(PostLike::getPostId).collect(Collectors.toSet());
            bookmarkedPostIds = postBookmarkRepository.findByPostIdInAndUserId(postIds, currentUserId)
                    .stream().map(PostBookmark::getPostId).collect(Collectors.toSet());
        }

        Map<Long, List<Topic>> postTopicsMap = getPostTopicsMap(posts);
        Set<Long> finalLikedPostIds = likedPostIds;
        Set<Long> finalBookmarkedPostIds = bookmarkedPostIds;

        Map<String, Object> result = new HashMap<>();
        result.put("content", posts.stream().map(post -> {
            User author = userRepository.findById(post.getUserId()).orElse(null);
            CommunityPostVO vo = CommunityPostVO.from(post, author);
            if (currentUserId != null) {
                vo.setLiked(finalLikedPostIds.contains(post.getId()));
                vo.setBookmarked(finalBookmarkedPostIds.contains(post.getId()));
            }
            List<Topic> postTopics = postTopicsMap.getOrDefault(post.getId(), List.of());
            vo.setTopics(postTopics.stream().map(TopicVO::from).toList());
            return vo;
        }).toList());
        result.put("totalElements", total);
        result.put("totalPages", (int) Math.ceil((double) total / size));
        result.put("currentPage", page);
        result.put("last", (page + 1) * size >= total);

        return Result.success(result);
    }

    @GetMapping("/posts/{id}")
    public Result<CommunityPostDetailVO> getPostDetail(@PathVariable Long id, HttpServletRequest request) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }

        post.setViewCount(post.getViewCount() + 1);
        communityPostRepository.save(post);

        User author = userRepository.findById(post.getUserId()).orElse(null);
        CommunityPostDetailVO vo = CommunityPostDetailVO.from(post, author);

        Long currentUserId = getCurrentUserId(request);
        if (currentUserId != null) {
            vo.setLiked(postLikeRepository.existsByPostIdAndUserId(id, currentUserId));
            vo.setBookmarked(postBookmarkRepository.existsByPostIdAndUserId(id, currentUserId));
        }

        List<Long> topicIds = postTopicRepository.findTopicIdsByPostId(id);
        List<Topic> topics = topicRepository.findByIdIn(topicIds);
        vo.setTopics(topics.stream().map(TopicVO::from).toList());

        return Result.success(vo);
    }

    @PostMapping("/posts")
    @Transactional
    public Result<CommunityPostVO> createPost(HttpServletRequest request, @RequestBody CommunityPostRequest req) {
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            return Result.error(400, "主题不能为空");
        }
        if (req.getTitle().length() > 50) {
            return Result.error(400, "主题长度不能超过50字");
        }
        if (req.getDescription() == null || req.getDescription().trim().isEmpty()) {
            return Result.error(400, "详细描述不能为空");
        }
        if (req.getDescription().length() > 1000) {
            return Result.error(400, "详细描述不能超过1000字");
        }
        if (req.getImages() != null && !req.getImages().trim().isEmpty()) {
            String[] imageArray = req.getImages().split(",");
            if (imageArray.length > 3) {
                return Result.error(400, "最多只能上传3张图片");
            }
        }
        if (req.getTopicIds() != null && req.getTopicIds().size() > 5) {
            return Result.error(400, "最多只能关联5个话题标签");
        }

        Long userId = ((Number) request.getAttribute("userId")).longValue();

        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setTitle(req.getTitle().trim());
        post.setDescription(req.getDescription().trim());
        post.setImages(req.getImages() != null ? req.getImages().trim() : null);
        communityPostRepository.save(post);

        if (req.getTopicIds() != null && !req.getTopicIds().isEmpty()) {
            Set<Long> topicIdSet = new HashSet<>(req.getTopicIds());
            List<Topic> topics = topicRepository.findByIdIn(new ArrayList<>(topicIdSet));
            Map<Long, Topic> topicMap = topics.stream().collect(Collectors.toMap(Topic::getId, t -> t));

            for (Long topicId : topicIdSet) {
                Topic topic = topicMap.get(topicId);
                if (topic != null && topic.getStatus() == 1) {
                    PostTopic postTopic = new PostTopic();
                    postTopic.setPostId(post.getId());
                    postTopic.setTopicId(topicId);
                    postTopicRepository.save(postTopic);
                    topic.setPostCount(topic.getPostCount() + 1);
                    topicRepository.save(topic);
                }
            }
        }

        User author = userRepository.findById(userId).orElse(null);
        CommunityPostVO vo = CommunityPostVO.from(post, author);

        if (req.getTopicIds() != null && !req.getTopicIds().isEmpty()) {
            List<Topic> topics = topicRepository.findByIdIn(new ArrayList<>(new HashSet<>(req.getTopicIds())));
            vo.setTopics(topics.stream().map(TopicVO::from).toList());
        }

        return Result.success(vo);
    }

    @PutMapping("/posts/{id}")
    @Transactional
    public Result<CommunityPostVO> updatePost(HttpServletRequest request,
                                               @PathVariable Long id,
                                               @RequestBody CommunityPostRequest req) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }

        Long currentUserId = getCurrentUserId(request);
        String role = getUserRole(request);

        if (!post.getUserId().equals(currentUserId) && !"SYS_ADMIN".equals(role)) {
            return Result.error(403, "无权编辑此内容");
        }

        if (req.getTitle() != null) {
            if (req.getTitle().trim().isEmpty()) {
                return Result.error(400, "主题不能为空");
            }
            if (req.getTitle().length() > 50) {
                return Result.error(400, "主题长度不能超过50字");
            }
            post.setTitle(req.getTitle().trim());
        }

        if (req.getDescription() != null) {
            if (req.getDescription().trim().isEmpty()) {
                return Result.error(400, "详细描述不能为空");
            }
            if (req.getDescription().length() > 1000) {
                return Result.error(400, "详细描述不能超过1000字");
            }
            post.setDescription(req.getDescription().trim());
        }

        if (req.getImages() != null) {
            String[] imageArray = req.getImages().split(",");
            if (imageArray.length > 3) {
                return Result.error(400, "最多只能上传3张图片");
            }
            post.setImages(req.getImages().trim());
        }

        post.setEditedAt(LocalDateTime.now());
        communityPostRepository.save(post);

        if (req.getTopicIds() != null) {
            if (req.getTopicIds().size() > 5) {
                return Result.error(400, "最多只能关联5个话题标签");
            }

            Set<Long> newTopicIds = new HashSet<>(req.getTopicIds());
            List<Long> oldTopicIds = postTopicRepository.findTopicIdsByPostId(id);
            Set<Long> oldTopicIdSet = new HashSet<>(oldTopicIds);

            Set<Long> toAdd = new HashSet<>(newTopicIds);
            toAdd.removeAll(oldTopicIdSet);

            Set<Long> toRemove = new HashSet<>(oldTopicIdSet);
            toRemove.removeAll(newTopicIds);

            List<Topic> allTopics = topicRepository.findByIdIn(new ArrayList<>(newTopicIds));
            Map<Long, Topic> topicMap = allTopics.stream().collect(Collectors.toMap(Topic::getId, t -> t));

            for (Long topicId : toAdd) {
                Topic topic = topicMap.get(topicId);
                if (topic != null && topic.getStatus() == 1) {
                    PostTopic postTopic = new PostTopic();
                    postTopic.setPostId(id);
                    postTopic.setTopicId(topicId);
                    postTopicRepository.save(postTopic);
                    topic.setPostCount(topic.getPostCount() + 1);
                    topicRepository.save(topic);
                }
            }

            for (Long topicId : toRemove) {
                postTopicRepository.deleteByPostIdAndTopicIdNotIn(id, newTopicIds);
                Topic topic = topicRepository.findById(topicId).orElse(null);
                if (topic != null) {
                    topic.setPostCount(Math.max(0, topic.getPostCount() - 1));
                    topicRepository.save(topic);
                }
            }
        }

        User author = userRepository.findById(post.getUserId()).orElse(null);
        CommunityPostVO vo = CommunityPostVO.from(post, author);

        List<Long> finalTopicIds = postTopicRepository.findTopicIdsByPostId(id);
        List<Topic> finalTopics = topicRepository.findByIdIn(finalTopicIds);
        vo.setTopics(finalTopics.stream().map(TopicVO::from).toList());

        return Result.success(vo);
    }

    @DeleteMapping("/posts/{id}")
    @Transactional
    public Result<?> deletePost(HttpServletRequest request, @PathVariable Long id) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        if (!post.getUserId().equals(currentUserId) && !"SYS_ADMIN".equals(role)) {
            return Result.error(403, "无权删除此内容");
        }

        List<Long> topicIds = postTopicRepository.findTopicIdsByPostId(id);
        for (Long topicId : topicIds) {
            Topic topic = topicRepository.findById(topicId).orElse(null);
            if (topic != null) {
                topic.setPostCount(Math.max(0, topic.getPostCount() - 1));
                topicRepository.save(topic);
            }
        }

        postTopicRepository.deleteByPostId(id);
        postLikeRepository.deleteByPostId(id);
        postBookmarkRepository.deleteByPostId(id);
        postCommentRepository.deleteByPostId(id);
        communityPostRepository.delete(post);
        return Result.success("删除成功");
    }

    @PostMapping("/posts/{id}/like")
    @Transactional
    public Result<Map<String, Object>> toggleLike(HttpServletRequest request, @PathVariable Long id) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();

        if (post.getUserId().equals(currentUserId)) {
            return Result.error(400, "不能对自己的内容进行认可操作");
        }

        Optional<PostLike> existing = postLikeRepository.findByPostIdAndUserId(id, currentUserId);
        boolean liked;
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            liked = false;
        } else {
            PostLike like = new PostLike();
            like.setPostId(id);
            like.setUserId(currentUserId);
            postLikeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            liked = true;
        }
        communityPostRepository.save(post);

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", post.getLikeCount());
        return Result.success(result);
    }

    @PostMapping("/posts/{id}/bookmark")
    @Transactional
    public Result<Map<String, Object>> toggleBookmark(HttpServletRequest request, @PathVariable Long id) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();

        if (post.getUserId().equals(currentUserId)) {
            return Result.error(400, "不能收藏自己的内容");
        }

        Optional<PostBookmark> existing = postBookmarkRepository.findByPostIdAndUserId(id, currentUserId);
        boolean bookmarked;
        if (existing.isPresent()) {
            postBookmarkRepository.delete(existing.get());
            post.setBookmarkCount(Math.max(0, post.getBookmarkCount() - 1));
            bookmarked = false;
        } else {
            PostBookmark bookmark = new PostBookmark();
            bookmark.setPostId(id);
            bookmark.setUserId(currentUserId);
            postBookmarkRepository.save(bookmark);
            post.setBookmarkCount(post.getBookmarkCount() + 1);
            bookmarked = true;
        }
        communityPostRepository.save(post);

        Map<String, Object> result = new HashMap<>();
        result.put("bookmarked", bookmarked);
        result.put("bookmarkCount", post.getBookmarkCount());
        return Result.success(result);
    }

    @GetMapping("/posts/{id}/comments")
    public Result<Map<String, Object>> listComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<PostComment> topLevelComments = postCommentRepository
                .findByPostIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(id, pageable);

        List<PostComment> allReplies = postCommentRepository
                .findByPostIdAndParentIdNotNullAndDeletedFalseOrderByCreatedAtAsc(id);

        Map<Long, List<PostComment>> repliesByParentId = allReplies.stream()
                .collect(Collectors.groupingBy(PostComment::getParentId));

        Set<Long> userIds = new HashSet<>();
        allReplies.forEach(r -> userIds.add(r.getUserId()));
        topLevelComments.getContent().forEach(c -> userIds.add(c.getUserId()));

        Map<Long, User> userMap = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> userMap.put(u.getId(), u));

        List<CommentVO> commentVOs = topLevelComments.getContent().stream().map(comment -> {
            User user = userMap.get(comment.getUserId());
            CommentVO vo = CommentVO.from(comment, user);
            List<PostComment> replies = repliesByParentId.getOrDefault(comment.getId(), List.of());
            vo.setReplies(replies.stream().map(reply -> {
                User replyUser = userMap.get(reply.getUserId());
                CommentVO replyVo = CommentVO.from(reply, replyUser);
                if (reply.getParentId() != null) {
                    User parentUser = userMap.get(comment.getUserId());
                    if (parentUser != null) {
                        replyVo.setParentUserName(parentUser.getRealName() != null ? parentUser.getRealName() : parentUser.getUsername());
                    }
                }
                return replyVo;
            }).toList());
            return vo;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", commentVOs);
        result.put("totalElements", topLevelComments.getTotalElements());
        result.put("totalPages", topLevelComments.getTotalPages());
        result.put("currentPage", topLevelComments.getNumber());
        result.put("last", topLevelComments.isLast());
        return Result.success(result);
    }

    @PostMapping("/posts/{id}/comments")
    @Transactional
    public Result<CommentVO> createComment(HttpServletRequest request, @PathVariable Long id,
                                           @RequestBody CommentRequest req) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }

        if (req.getContent() == null || req.getContent().trim().isEmpty()) {
            return Result.error(400, "评论内容不能为空");
        }
        if (req.getContent().length() > 500) {
            return Result.error(400, "评论内容不能超过500字");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();

        if (req.getParentId() != null) {
            PostComment parent = postCommentRepository.findById(req.getParentId()).orElse(null);
            if (parent == null || !parent.getPostId().equals(id) || parent.getDeleted()) {
                return Result.error(400, "回复的评论不存在");
            }
        }

        PostComment comment = new PostComment();
        comment.setPostId(id);
        comment.setUserId(currentUserId);
        comment.setParentId(req.getParentId());
        comment.setContent(req.getContent().trim());
        postCommentRepository.save(comment);

        post.setCommentCount(post.getCommentCount() + 1);
        communityPostRepository.save(post);

        User user = userRepository.findById(currentUserId).orElse(null);
        CommentVO vo = CommentVO.from(comment, user);

        if (req.getParentId() != null) {
            PostComment parentComment = postCommentRepository.findById(req.getParentId()).orElse(null);
            if (parentComment != null) {
                User parentUser = userRepository.findById(parentComment.getUserId()).orElse(null);
                if (parentUser != null) {
                    vo.setParentUserName(parentUser.getRealName() != null ? parentUser.getRealName() : parentUser.getUsername());
                }
            }
        }

        return Result.success(vo);
    }

    @DeleteMapping("/comments/{id}")
    @Transactional
    public Result<?> deleteComment(HttpServletRequest request, @PathVariable Long id) {
        PostComment comment = postCommentRepository.findById(id).orElse(null);
        if (comment == null) {
            return Result.error(404, "评论不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        String role = (String) request.getAttribute("role");

        CommunityPost post = communityPostRepository.findById(comment.getPostId()).orElse(null);

        boolean isCommentOwner = comment.getUserId().equals(currentUserId);
        boolean isPostOwner = post != null && post.getUserId().equals(currentUserId);
        boolean isAdmin = "SYS_ADMIN".equals(role);

        if (!isCommentOwner && !isPostOwner && !isAdmin) {
            return Result.error(403, "无权删除此评论");
        }

        boolean hasReplies = postCommentRepository.existsByParentIdAndDeletedFalse(id);

        if (hasReplies) {
            comment.setDeleted(true);
            comment.setContent("该评论已删除");
            postCommentRepository.save(comment);
        } else {
            postCommentRepository.delete(comment);
            if (post != null) {
                post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
                communityPostRepository.save(post);
            }
        }

        return Result.success("删除成功");
    }

    @GetMapping("/my/likes")
    public Result<Map<String, Object>> myLikes(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostLike> likes = postLikeRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<MyLikeVO> voList = likes.getContent().stream().map(like -> {
            CommunityPost post = communityPostRepository.findById(like.getPostId()).orElse(null);
            User postAuthor = post != null ? userRepository.findById(post.getUserId()).orElse(null) : null;
            return MyLikeVO.from(like, post, postAuthor);
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", likes.getTotalElements());
        result.put("totalPages", likes.getTotalPages());
        result.put("currentPage", likes.getNumber());
        result.put("last", likes.isLast());
        return Result.success(result);
    }

    @DeleteMapping("/my/likes/{id}")
    @Transactional
    public Result<?> removeMyLike(HttpServletRequest request, @PathVariable Long id) {
        PostLike like = postLikeRepository.findById(id).orElse(null);
        if (like == null) {
            return Result.error(404, "记录不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        if (!like.getUserId().equals(currentUserId)) {
            return Result.error(403, "无权操作");
        }

        CommunityPost post = communityPostRepository.findById(like.getPostId()).orElse(null);
        postLikeRepository.delete(like);
        if (post != null) {
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            communityPostRepository.save(post);
        }

        return Result.success("移除成功");
    }

    @GetMapping("/my/bookmarks")
    public Result<Map<String, Object>> myBookmarks(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostBookmark> bookmarks = postBookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<MyBookmarkVO> voList = bookmarks.getContent().stream().map(bookmark -> {
            CommunityPost post = communityPostRepository.findById(bookmark.getPostId()).orElse(null);
            User postAuthor = post != null ? userRepository.findById(post.getUserId()).orElse(null) : null;
            return MyBookmarkVO.from(bookmark, post, postAuthor);
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", bookmarks.getTotalElements());
        result.put("totalPages", bookmarks.getTotalPages());
        result.put("currentPage", bookmarks.getNumber());
        result.put("last", bookmarks.isLast());
        return Result.success(result);
    }

    @DeleteMapping("/my/bookmarks/{id}")
    @Transactional
    public Result<?> removeMyBookmark(HttpServletRequest request, @PathVariable Long id) {
        PostBookmark bookmark = postBookmarkRepository.findById(id).orElse(null);
        if (bookmark == null) {
            return Result.error(404, "记录不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        if (!bookmark.getUserId().equals(currentUserId)) {
            return Result.error(403, "无权操作");
        }

        CommunityPost post = communityPostRepository.findById(bookmark.getPostId()).orElse(null);
        postBookmarkRepository.delete(bookmark);
        if (post != null) {
            post.setBookmarkCount(Math.max(0, post.getBookmarkCount() - 1));
            communityPostRepository.save(post);
        }

        return Result.success("移除成功");
    }

    @GetMapping("/my/comments")
    public Result<Map<String, Object>> myComments(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = ((Number) request.getAttribute("userId")).longValue();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostComment> comments = postCommentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<MyCommentVO> voList = comments.getContent().stream().map(comment -> {
            CommunityPost post = communityPostRepository.findById(comment.getPostId()).orElse(null);
            User postAuthor = post != null ? userRepository.findById(post.getUserId()).orElse(null) : null;
            User parentUser = null;
            if (comment.getParentId() != null) {
                PostComment parentComment = postCommentRepository.findById(comment.getParentId()).orElse(null);
                if (parentComment != null) {
                    parentUser = userRepository.findById(parentComment.getUserId()).orElse(null);
                }
            }
            return MyCommentVO.from(comment, post, postAuthor, parentUser);
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", comments.getTotalElements());
        result.put("totalPages", comments.getTotalPages());
        result.put("currentPage", comments.getNumber());
        result.put("last", comments.isLast());
        return Result.success(result);
    }

    @DeleteMapping("/my/comments/{id}")
    @Transactional
    public Result<?> removeMyComment(HttpServletRequest request, @PathVariable Long id) {
        PostComment comment = postCommentRepository.findById(id).orElse(null);
        if (comment == null) {
            return Result.error(404, "评论不存在");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        if (!comment.getUserId().equals(currentUserId)) {
            return Result.error(403, "无权操作");
        }

        boolean hasReplies = postCommentRepository.existsByParentIdAndDeletedFalse(id);

        if (hasReplies) {
            comment.setDeleted(true);
            comment.setContent("该评论已删除");
            postCommentRepository.save(comment);
        } else {
            postCommentRepository.delete(comment);
            CommunityPost post = communityPostRepository.findById(comment.getPostId()).orElse(null);
            if (post != null) {
                post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
                communityPostRepository.save(post);
            }
        }

        return Result.success("删除成功");
    }

    @PostMapping("/posts/image")
    public Result<?> uploadImage(HttpServletRequest request, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "请选择要上传的图片");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/jpg") && !contentType.equals("image/png"))) {
            return Result.error(400, "只支持JPG、PNG格式的图片");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error(400, "图片大小不能超过5MB");
        }

        try {
            java.io.File uploadDir = new java.io.File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String newFilename = java.util.UUID.randomUUID().toString() + extension;
            java.io.File destFile = new java.io.File(uploadDir, newFilename);
            file.transferTo(destFile);

            String imageUrl = "/api/uploads/" + newFilename;
            return Result.success(imageUrl);
        } catch (java.io.IOException e) {
            return Result.error(500, "图片上传失败: " + e.getMessage());
        }
    }
}
