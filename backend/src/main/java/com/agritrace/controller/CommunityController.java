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

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @GetMapping("/posts")
    public Result<Map<String, Object>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommunityPost> postPage = communityPostRepository.findAllByOrderByCreatedAtDesc(pageable);

        Long currentUserId = null;
        try {
            Object uid = request.getAttribute("userId");
            if (uid != null) {
                currentUserId = ((Number) uid).longValue();
            }
        } catch (Exception ignored) {}

        final Long finalUserId = currentUserId;
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        if (finalUserId != null) {
            List<Long> postIds = postPage.getContent().stream().map(CommunityPost::getId).toList();
            if (!postIds.isEmpty()) {
                likedPostIds = postLikeRepository.findByPostIdInAndUserId(postIds, finalUserId)
                        .stream().map(PostLike::getPostId).collect(Collectors.toSet());
                bookmarkedPostIds = postBookmarkRepository.findByPostIdInAndUserId(postIds, finalUserId)
                        .stream().map(PostBookmark::getPostId).collect(Collectors.toSet());
            }
        }

        Set<Long> finalLikedPostIds = likedPostIds;
        Set<Long> finalBookmarkedPostIds = bookmarkedPostIds;

        Map<String, Object> result = new HashMap<>();
        result.put("content", postPage.getContent().stream().map(post -> {
            User author = userRepository.findById(post.getUserId()).orElse(null);
            CommunityPostVO vo = CommunityPostVO.from(post, author);
            if (finalUserId != null) {
                vo.setLiked(finalLikedPostIds.contains(post.getId()));
                vo.setBookmarked(finalBookmarkedPostIds.contains(post.getId()));
            }
            return vo;
        }).toList());
        result.put("totalElements", postPage.getTotalElements());
        result.put("totalPages", postPage.getTotalPages());
        result.put("currentPage", postPage.getNumber());
        result.put("last", postPage.isLast());

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

        Long currentUserId = null;
        try {
            Object uid = request.getAttribute("userId");
            if (uid != null) {
                currentUserId = ((Number) uid).longValue();
            }
        } catch (Exception ignored) {}

        if (currentUserId != null) {
            vo.setLiked(postLikeRepository.existsByPostIdAndUserId(id, currentUserId));
            vo.setBookmarked(postBookmarkRepository.existsByPostIdAndUserId(id, currentUserId));
        }

        return Result.success(vo);
    }

    @PostMapping("/posts")
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

        Long userId = ((Number) request.getAttribute("userId")).longValue();

        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setTitle(req.getTitle().trim());
        post.setDescription(req.getDescription().trim());
        post.setImages(req.getImages() != null ? req.getImages().trim() : null);
        communityPostRepository.save(post);

        User author = userRepository.findById(userId).orElse(null);
        return Result.success(CommunityPostVO.from(post, author));
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

        if (hasReplies && post != null) {
            // deleted comment still counts in commentCount for display
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
