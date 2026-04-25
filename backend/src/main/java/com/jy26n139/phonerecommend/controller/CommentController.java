package com.jy26n139.phonerecommend.controller;

import com.jy26n139.phonerecommend.common.ApiResult;
import com.jy26n139.phonerecommend.common.PageResult;
import com.jy26n139.phonerecommend.config.AuthContext;
import com.jy26n139.phonerecommend.dto.CommentForm;
import com.jy26n139.phonerecommend.entity.Comment;
import com.jy26n139.phonerecommend.service.CommentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResult<PageResult<Comment>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String productId) {
        return ApiResult.ok(commentService.page(page, pageSize, q, productId));
    }

    @PostMapping
    public ApiResult<Void> save(@RequestBody CommentForm form) {
        if (form.id() != null) {
            AuthContext.requireAdmin();
        }
        commentService.save(form);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        AuthContext.requireAdmin();
        commentService.delete(id);
        return ApiResult.ok(null);
    }
}
