package com.jy26n139.phonerecommend.controller;

import com.jy26n139.phonerecommend.common.ApiResult;
import com.jy26n139.phonerecommend.common.PageResult;
import com.jy26n139.phonerecommend.config.AuthContext;
import com.jy26n139.phonerecommend.dto.TrackRequest;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.entity.Comment;
import com.jy26n139.phonerecommend.mapper.CommentMapper;
import com.jy26n139.phonerecommend.service.BehaviorService;
import com.jy26n139.phonerecommend.service.PhoneService;
import com.jy26n139.phonerecommend.service.RecommendationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/phones")
public class PhoneController {
    private final PhoneService phoneService;
    private final RecommendationService recommendationService;
    private final BehaviorService behaviorService;
    private final CommentMapper commentMapper;

    public PhoneController(PhoneService phoneService, RecommendationService recommendationService, BehaviorService behaviorService, CommentMapper commentMapper) {
        this.phoneService = phoneService;
        this.recommendationService = recommendationService;
        this.behaviorService = behaviorService;
        this.commentMapper = commentMapper;
    }

    @GetMapping
    public ApiResult<PageResult<Phone>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "8") long pageSize,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) String brand) {
        return ApiResult.ok(phoneService.page(page, pageSize, q, brand));
    }

    @GetMapping("/brands")
    public ApiResult<List<String>> brands() {
        return ApiResult.ok(phoneService.brands());
    }

    @GetMapping("/featured")
    public ApiResult<List<Phone>> featured(@RequestParam(defaultValue = "4") int limit) {
        return ApiResult.ok(phoneService.featured(limit));
    }

    @GetMapping("/{productId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String productId) {
        Long userId = AuthContext.userId();
        if (userId != null) {
            behaviorService.record(userId, productId, "view");
        }
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getProductId, productId));
        Map<String, Long> sentMap = comments.stream()
                .collect(Collectors.groupingBy(c -> c.getSentimentLabel() == null ? "neutral" : c.getSentimentLabel(), Collectors.counting()));
        return ApiResult.ok(Map.of(
                "phone", phoneService.byProductId(productId),
                "related", recommendationService.similar(productId, 4),
                "totalComments", comments.size(),
                "sentMap", sentMap
        ));
    }

    @PostMapping
    public ApiResult<Void> save(@RequestBody Phone phone) {
        phoneService.save(phone);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        phoneService.delete(id);
        return ApiResult.ok(null);
    }

    @PostMapping("/track")
    public ApiResult<Void> track(@RequestBody TrackRequest request) {
        behaviorService.record(AuthContext.userId(), request.productId(), request.action());
        return ApiResult.ok(null);
    }
}
