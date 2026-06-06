package com.agritrace.controller;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.agritrace.dto.*;
import com.agritrace.entity.CommunityPost;
import com.agritrace.entity.PostComment;
import com.agritrace.entity.User;
import com.agritrace.repository.CommunityPostRepository;
import com.agritrace.repository.LogisticsRepository;
import com.agritrace.repository.PostCommentRepository;
import com.agritrace.repository.UserRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            @RequestParam(required = false) String endDate) {

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

        Map<String, Object> result = new HashMap<>();
        result.put("content", postPage.getContent().stream().map(post -> {
            User author = userRepository.findById(post.getUserId()).orElse(null);
            return CommunityPostDetailVO.from(post, author);
        }).toList());
        result.put("totalElements", postPage.getTotalElements());
        result.put("totalPages", postPage.getTotalPages());
        result.put("currentPage", postPage.getNumber());
        result.put("last", postPage.isLast());

        return Result.success(result);
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
        return Result.success(stats);
    }
}
