package com.jy26n139.phonerecommend.controller;

import com.jy26n139.phonerecommend.common.ApiResult;
import com.jy26n139.phonerecommend.config.AuthContext;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.mapper.PhoneMapper;
import com.jy26n139.phonerecommend.service.BehaviorService;
import com.jy26n139.phonerecommend.service.RecommendationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.dto.RecommendationExplainResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final BehaviorService behaviorService;
    private final PhoneMapper phoneMapper;

    public RecommendationController(RecommendationService recommendationService, BehaviorService behaviorService, PhoneMapper phoneMapper) {
        this.recommendationService = recommendationService;
        this.behaviorService = behaviorService;
        this.phoneMapper = phoneMapper;
    }

    @GetMapping
    public ApiResult<List<Phone>> recommend(@RequestParam(defaultValue = "8") int limit) {
        return ApiResult.ok(recommendationService.recommend(AuthContext.userId(), limit));
    }

    @GetMapping("/explain")
    public ApiResult<RecommendationExplainResponse> explain(@RequestParam(defaultValue = "8") int limit) {
        return ApiResult.ok(recommendationService.explainRecommendations(AuthContext.userId(), limit));
    }

    @GetMapping("/history")
    public ApiResult<List<Phone>> history(@RequestParam(defaultValue = "8") int limit) {
        List<String> ids = behaviorService.history(AuthContext.userId(), limit);
        if (ids.isEmpty()) {
            return ApiResult.ok(List.of());
        }
        return ApiResult.ok(phoneMapper.selectList(new LambdaQueryWrapper<Phone>().in(Phone::getProductId, ids)));
    }
}
