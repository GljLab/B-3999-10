package com.agritrace.controller;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.agritrace.dto.*;
import com.agritrace.entity.*;
import com.agritrace.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Set<String> ROLE_SET = Set.of("USER", "FARMER", "LOGS_ADMIN", "SYS_ADMIN");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LogisticsRepository logisticsRepository;
    @Autowired
    private CommunityPostRepository communityPostRepository;
    @Autowired
    private PostCommentRepository postCommentRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private PostTopicRepository postTopicRepository;
    @Autowired
    private UserTopicFollowRepository userTopicFollowRepository;

    @GetMapping("/users")
    public Result<List<AdminUserVO>> listUsers() {
        List<AdminUserVO> users = userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(AdminUserVO::from)
                .collect(Collectors.toList());
        return Result.success(users);
    }

    @PostMapping("/users")
    public Result<?> createUser(@RequestBody AdminCreateUserRequest req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            return Result.error(400, "用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().trim().isEmpty()) {
            return Result.error(400, "密码不能为空");
        }
        if (req.getRole() == null || !ROLE_SET.contains(req.getRole())) {
            return Result.error(400, "角色不合法");
        }
        if (userRepository.findByUsername(req.getUsername().trim()).isPresent()) {
            return Result.error(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(req.getUsername().trim());
        user.setPassword(BCrypt.withDefaults().hashToString(10, req.getPassword().toCharArray()));
        user.setRole(req.getRole());
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setEnabled(1);
        userRepository.save(user);
        return Result.success(AdminUserVO.from(user));
    }

    @PutMapping("/users/{id}/role")
    public Result<?> updateUserRole(HttpServletRequest request, @PathVariable Long id, @RequestBody AdminUpdateUserRoleRequest req) {
        if (req.getRole() == null || !ROLE_SET.contains(req.getRole())) {
            return Result.error(400, "角色不合法");
        }

        User targetUser = userRepository.findById(id).orElse(null);
        if (targetUser == null) {
            return Result.error(404, "用户不存在");
        }
        if (isProtectedAdmin(targetUser)) {
            return Result.error(400, "admin 为系统保留账号，不允许修改");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        if (id.equals(currentUserId) && !"SYS_ADMIN".equals(req.getRole())) {
            return Result.error(400, "不能降低当前登录账号的系统管理员权限");
        }

        targetUser.setRole(req.getRole());
        userRepository.save(targetUser);
        return Result.success(AdminUserVO.from(targetUser));
    }

    @PutMapping("/users/{id}/status")
    public Result<?> updateUserStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody AdminUpdateUserStatusRequest req) {
        if (req.getEnabled() == null || (req.getEnabled() != 0 && req.getEnabled() != 1)) {
            return Result.error(400, "enabled 仅支持 0 或 1");
        }

        User targetUser = userRepository.findById(id).orElse(null);
        if (targetUser == null) {
            return Result.error(404, "用户不存在");
        }
        if (isProtectedAdmin(targetUser)) {
            return Result.error(400, "admin 为系统保留账号，不允许修改");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        if (id.equals(currentUserId) && req.getEnabled() == 0) {
            return Result.error(400, "不能禁用当前登录账号");
        }

        targetUser.setEnabled(req.getEnabled());
        userRepository.save(targetUser);
        return Result.success(AdminUserVO.from(targetUser));
    }

    @DeleteMapping("/users/{id}")
    public Result<?> deleteUser(HttpServletRequest request, @PathVariable Long id) {
        User targetUser = userRepository.findById(id).orElse(null);
        if (targetUser == null) {
            return Result.error(404, "用户不存在");
        }
        if (isProtectedAdmin(targetUser)) {
            return Result.error(400, "admin 为系统保留账号，不允许修改");
        }

        Long currentUserId = ((Number) request.getAttribute("userId")).longValue();
        if (id.equals(currentUserId)) {
            return Result.error(400, "不能删除当前登录账号");
        }
        if ("SYS_ADMIN".equals(targetUser.getRole()) && Integer.valueOf(1).equals(targetUser.getEnabled())) {
            long adminCount = userRepository.countByRoleAndEnabled("SYS_ADMIN", 1);
            if (adminCount <= 1) {
                return Result.error(400, "系统至少需要保留一个启用状态的系统管理员");
            }
        }

        if (logisticsRepository.existsByLogisticsAdminId(id)) {
            return Result.error(400, "该用户存在物流操作记录，不能删除");
        }

        try {
            userRepository.delete(targetUser);
            return Result.success("删除成功");
        } catch (DataIntegrityViolationException ex) {
            return Result.error(400, "该用户已关联业务数据，不能删除");
        }
    }

    private boolean isProtectedAdmin(User user) {
        if (user == null || user.getUsername() == null) {
            return false;
        }
        return "admin".equalsIgnoreCase(user.getUsername().trim());
    }

    @GetMapping("/community/posts")
    public Result<Map<String, Object>> listCommunityPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer tagged) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommunityPost> postPage;

        boolean hasUsername = username != null && !username.trim().isEmpty();
        boolean hasDateRange = startDate != null && !startDate.trim().isEmpty() && endDate != null && !endDate.trim().isEmpty();

        if (hasUsername && hasDateRange) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            postPage = communityPostRepository.findByAuthorUsernameAndCreatedAtBetween(username.trim(), start, end, pageable);
        } else if (hasUsername) {
            postPage = communityPostRepository.findByAuthorUsername(username.trim(), pageable);
        } else if (hasDateRange) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            postPage = communityPostRepository.findByCreatedAtBetween(start, end, pageable);
        } else {
            postPage = communityPostRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<CommunityPost> posts = postPage.getContent();

        if (tagged != null) {
            List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
            List<Long> topicIds = postTopicRepository.findTopicIdsByPostIdIn(postIds);
            final Set<Long> taggedPostIdSet = new HashSet<>();
            if (!topicIds.isEmpty()) {
                List<Long> taggedPostIds = postTopicRepository.findPostIdsByTopicIdIn(topicIds);
                taggedPostIdSet.addAll(taggedPostIds);
            }
            if (tagged == 1) {
                posts = posts.stream().filter(p -> taggedPostIdSet.contains(p.getId())).toList();
            } else {
                posts = posts.stream().filter(p -> !taggedPostIdSet.contains(p.getId())).toList();
            }
        }

        Map<Long, List<Topic>> postTopicsMap = getPostTopicsMap(posts);

        Map<String, Object> result = new HashMap<>();
        result.put("content", posts.stream().map(post -> {
            User author = userRepository.findById(post.getUserId()).orElse(null);
            CommunityPostDetailVO vo = CommunityPostDetailVO.from(post, author);
            List<Topic> postTopics = postTopicsMap.getOrDefault(post.getId(), List.of());
            vo.setTopics(postTopics.stream().map(TopicVO::from).toList());
            return vo;
        }).toList());
        result.put("totalElements", postPage.getTotalElements());
        result.put("totalPages", postPage.getTotalPages());
        result.put("currentPage", postPage.getNumber());
        result.put("last", postPage.isLast());

        return Result.success(result);
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

    @DeleteMapping("/community/posts/{id}")
    @Transactional
    public Result<?> deleteCommunityPost(@PathVariable Long id) {
        CommunityPost post = communityPostRepository.findById(id).orElse(null);
        if (post == null) {
            return Result.error(404, "内容不存在");
        }
        postCommentRepository.deleteByPostId(id);
        communityPostRepository.delete(post);
        return Result.success("删除成功");
    }

    @GetMapping("/community/comments")
    public Result<Map<String, Object>> listCommunityComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String postTitle,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime startDt = null;
        LocalDateTime endDt = null;
        if (startDate != null && !startDate.trim().isEmpty()) {
            startDt = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            endDt = LocalDate.parse(endDate).atTime(LocalTime.MAX);
        }

        String usernameParam = (username != null && !username.trim().isEmpty()) ? username.trim() : null;
        String postTitleParam = (postTitle != null && !postTitle.trim().isEmpty()) ? postTitle.trim() : null;

        Page<PostComment> commentPage = postCommentRepository.findAllWithFilters(
                usernameParam, postTitleParam, startDt, endDt, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("content", commentPage.getContent().stream().map(comment -> {
            User user = userRepository.findById(comment.getUserId()).orElse(null);
            CommunityPost post = communityPostRepository.findById(comment.getPostId()).orElse(null);
            return AdminCommentVO.from(comment, post, user);
        }).toList());
        result.put("totalElements", commentPage.getTotalElements());
        result.put("totalPages", commentPage.getTotalPages());
        result.put("currentPage", commentPage.getNumber());
        result.put("last", commentPage.isLast());
        return Result.success(result);
    }

    @DeleteMapping("/community/comments/{id}")
    @Transactional
    public Result<?> deleteCommunityComment(@PathVariable Long id) {
        PostComment comment = postCommentRepository.findById(id).orElse(null);
        if (comment == null) {
            return Result.error(404, "评论不存在");
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

    @GetMapping("/community/stats")
    public Result<Map<String, Object>> getCommunityStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPosts", communityPostRepository.count());
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        stats.put("todayPosts", communityPostRepository.countByCreatedAtBetween(todayStart, todayEnd));
        stats.put("totalAuthors", communityPostRepository.countDistinctUserId());
        stats.put("totalTopics", topicRepository.count());
        stats.put("totalTopicFollows", userTopicFollowRepository.count());
        return Result.success(stats);
    }

    @GetMapping("/topics")
    public Result<Map<String, Object>> listTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sortOrder")
                .and(Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<Topic> topicPage;

        if (!keyword.isEmpty()) {
            topicPage = topicRepository.findByStatusAndNameContainingOrDescriptionContaining(1, keyword, keyword, pageable);
        } else {
            topicPage = topicRepository.findAll(pageable);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", topicPage.getContent().stream().map(TopicDetailVO::from).toList());
        result.put("totalElements", topicPage.getTotalElements());
        result.put("totalPages", topicPage.getTotalPages());
        result.put("currentPage", topicPage.getNumber());
        result.put("last", topicPage.isLast());
        return Result.success(result);
    }

    @PostMapping("/topics")
    @Transactional
    public Result<TopicDetailVO> createTopic(@RequestBody TopicCreateRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            return Result.error(400, "话题名称不能为空");
        }
        String name = req.getName().trim();
        if (name.length() < 2 || name.length() > 20) {
            return Result.error(400, "话题名称长度需在2-20字之间");
        }
        if (topicRepository.existsByName(name)) {
            return Result.error(400, "话题名称已存在");
        }
        if (req.getDescription() == null || req.getDescription().trim().isEmpty()) {
            return Result.error(400, "话题描述不能为空");
        }

        Topic topic = new Topic();
        topic.setName(name);
        topic.setDescription(req.getDescription().trim());
        topic.setIcon(req.getIcon() != null ? req.getIcon() : "🏷️");
        topic.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        topicRepository.save(topic);

        return Result.success(TopicDetailVO.from(topic));
    }

    @PutMapping("/topics/{id}")
    @Transactional
    public Result<TopicDetailVO> updateTopic(@PathVariable Long id, @RequestBody TopicUpdateRequest req) {
        Topic topic = topicRepository.findById(id).orElse(null);
        if (topic == null) {
            return Result.error(404, "话题不存在");
        }

        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.length() < 2 || name.length() > 20) {
                return Result.error(400, "话题名称长度需在2-20字之间");
            }
            if (!topic.getName().equals(name) && topicRepository.existsByName(name)) {
                return Result.error(400, "话题名称已存在");
            }
            topic.setName(name);
        }
        if (req.getDescription() != null) {
            topic.setDescription(req.getDescription().trim());
        }
        if (req.getIcon() != null) {
            topic.setIcon(req.getIcon());
        }
        if (req.getStatus() != null) {
            topic.setStatus(req.getStatus());
        }
        if (req.getIsFeatured() != null) {
            topic.setIsFeatured(req.getIsFeatured());
        }
        if (req.getSortOrder() != null) {
            topic.setSortOrder(req.getSortOrder());
        }

        topicRepository.save(topic);
        return Result.success(TopicDetailVO.from(topic));
    }

    @DeleteMapping("/topics/{id}")
    @Transactional
    public Result<?> deleteTopic(@PathVariable Long id) {
        Topic topic = topicRepository.findById(id).orElse(null);
        if (topic == null) {
            return Result.error(404, "话题不存在");
        }

        long postCount = postTopicRepository.findByTopicId(id).size();
        if (postCount > 0) {
            return Result.error(400, "该话题下还有 " + postCount + " 篇帖子，需先解除关联才能删除");
        }

        long followCount = userTopicFollowRepository.countByTopicId(id);
        if (followCount > 0) {
            userTopicFollowRepository.deleteByTopicId(id);
        }

        topicRepository.delete(topic);
        return Result.success("话题删除成功");
    }

    @GetMapping("/topics/{id}/stats")
    public Result<Map<String, Object>> getTopicStats(@PathVariable Long id) {
        Topic topic = topicRepository.findById(id).orElse(null);
        if (topic == null) {
            return Result.error(404, "话题不存在");
        }

        List<Long> postIds = postTopicRepository.findPostIdsByTopicId(id);
        List<CommunityPost> posts = postIds.stream()
                .map(pid -> communityPostRepository.findById(pid).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        int totalLikes = posts.stream().mapToInt(CommunityPost::getLikeCount).sum();
        int totalComments = posts.stream().mapToInt(CommunityPost::getCommentCount).sum();
        int totalBookmarks = posts.stream().mapToInt(CommunityPost::getBookmarkCount).sum();
        long distinctAuthors = posts.stream().map(CommunityPost::getUserId).distinct().count();

        double avgLikes = posts.isEmpty() ? 0 : (double) totalLikes / posts.size();
        double avgComments = posts.isEmpty() ? 0 : (double) totalComments / posts.size();
        double engagementRate = topic.getFollowCount() > 0
                ? (double) posts.size() / topic.getFollowCount() * 100
                : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("postCount", posts.size());
        stats.put("followCount", topic.getFollowCount());
        stats.put("authorCount", distinctAuthors);
        stats.put("totalLikes", totalLikes);
        stats.put("totalComments", totalComments);
        stats.put("totalBookmarks", totalBookmarks);
        stats.put("avgLikes", Math.round(avgLikes * 100) / 100.0);
        stats.put("avgComments", Math.round(avgComments * 100) / 100.0);
        stats.put("engagementRate", Math.round(engagementRate * 100) / 100.0);

        return Result.success(stats);
    }

    @PostMapping("/posts/smart-tag")
    @Transactional
    public Result<Map<String, Object>> smartTagPosts(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Long> postIds = (List<Long>) req.getOrDefault("postIds", List.of());
        if (postIds.isEmpty()) {
            return Result.error(400, "请选择要标注的帖子");
        }

        List<Topic> allTopics = topicRepository.findByStatusOrderBySortOrderAscCreatedAtDesc(1);
        Map<String, List<Topic>> keywordTopicMap = buildKeywordTopicMap(allTopics);

        Map<Long, List<Long>> recommendations = new HashMap<>();
        int processed = 0;

        for (Long postId : postIds) {
            CommunityPost post = communityPostRepository.findById(postId).orElse(null);
            if (post == null) continue;

            String content = post.getTitle() + " " + post.getDescription();
            Set<Topic> matchedTopics = new HashSet<>();

            for (Map.Entry<String, List<Topic>> entry : keywordTopicMap.entrySet()) {
                if (content.toLowerCase().contains(entry.getKey().toLowerCase())) {
                    matchedTopics.addAll(entry.getValue());
                }
            }

            if (!matchedTopics.isEmpty()) {
                List<Long> topicIds = matchedTopics.stream()
                        .limit(5)
                        .map(Topic::getId)
                        .toList();
                recommendations.put(postId, topicIds);
            }
            processed++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("processed", processed);
        result.put("recommendations", recommendations);
        result.put("topics", allTopics.stream().map(TopicVO::from).toList());
        return Result.success(result);
    }

    @PostMapping("/posts/apply-tags")
    @Transactional
    public Result<?> applyTags(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        Map<String, List<Long>> tagMap = (Map<String, List<Long>>) req.getOrDefault("tagMap", Map.of());
        if (tagMap.isEmpty()) {
            return Result.error(400, "没有要应用的标签");
        }

        int appliedCount = 0;
        for (Map.Entry<String, List<Long>> entry : tagMap.entrySet()) {
            Long postId = Long.parseLong(entry.getKey());
            List<Long> topicIds = entry.getValue();

            CommunityPost post = communityPostRepository.findById(postId).orElse(null);
            if (post == null) continue;

            List<Long> oldTopicIds = postTopicRepository.findTopicIdsByPostId(postId);
            Set<Long> oldSet = new HashSet<>(oldTopicIds);
            Set<Long> newSet = new HashSet<>(topicIds);

            for (Long topicId : newSet) {
                if (!oldSet.contains(topicId)) {
                    Topic topic = topicRepository.findById(topicId).orElse(null);
                    if (topic != null && topic.getStatus() == 1) {
                        PostTopic pt = new PostTopic();
                        pt.setPostId(postId);
                        pt.setTopicId(topicId);
                        postTopicRepository.save(pt);
                        topic.setPostCount(topic.getPostCount() + 1);
                        topicRepository.save(topic);
                    }
                }
            }
            appliedCount++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("appliedCount", appliedCount);
        return Result.success(result);
    }

    private Map<String, List<Topic>> buildKeywordTopicMap(List<Topic> topics) {
        Map<String, List<Topic>> map = new HashMap<>();

        Map<String, String[]> topicKeywords = new HashMap<>();
        topicKeywords.put("种植技术", new String[]{"种植", "栽培", "播种", "育苗", "移栽", "田间管理", "高产"});
        topicKeywords.put("有机种植", new String[]{"有机", "绿色", "无公害", "生态", "自然农法", "生物防治", "不打药"});
        topicKeywords.put("病虫害防治", new String[]{"病虫害", "病害", "虫害", "防治", "打药", "农药", "杀虫", "杀菌"});
        topicKeywords.put("养殖经验", new String[]{"养殖", "养猪", "养鸡", "养牛", "养羊", "水产", "畜牧", "饲料", "防疫"});
        topicKeywords.put("农机设备", new String[]{"农机", "拖拉机", "收割机", "无人机", "设备", "机械", "智能化"});
        topicKeywords.put("市场行情", new String[]{"价格", "行情", "市场", "收购", "销售", "电商", "批发", "零售"});
        topicKeywords.put("政策解读", new String[]{"政策", "补贴", "项目", "申报", "扶持", "农业农村部", "乡村振兴"});
        topicKeywords.put("创业故事", new String[]{"创业", "返乡", "新农人", "故事", "经历", "感悟", "心得"});
        topicKeywords.put("智慧农业", new String[]{"智慧农业", "物联网", "大数据", "精准农业", "数字化", "AI", "智能"});
        topicKeywords.put("农产品加工", new String[]{"加工", "深加工", "保鲜", "储存", "包装", "品牌", "食品"});
        topicKeywords.put("土壤肥料", new String[]{"土壤", "肥料", "化肥", "有机肥", "测土配方", "施肥", "改良"});
        topicKeywords.put("园艺花卉", new String[]{"园艺", "花卉", "盆景", "庭院", "绿植", "插花", "观赏"});

        for (Topic topic : topics) {
            String[] keywords = topicKeywords.getOrDefault(topic.getName(), new String[]{topic.getName()});
            for (String keyword : keywords) {
                map.computeIfAbsent(keyword, k -> new ArrayList<>()).add(topic);
            }
        }

        return map;
    }
}
