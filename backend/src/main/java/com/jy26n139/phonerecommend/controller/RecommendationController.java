package com.jy26n139.phonerecommend.controller;

import com.jy26n139.phonerecommend.common.ApiResult;
import com.jy26n139.phonerecommend.config.AuthContext;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ApiResult<List<Phone>> recommend(@RequestParam(defaultValue = "8") int limit) {
        return ApiResult.ok(recommendationService.recommend(AuthContext.userId(), limit));
    }
}
