package com.jy26n139.phonerecommend.controller;

import com.jy26n139.phonerecommend.common.ApiResult;
import com.jy26n139.phonerecommend.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/home")
    public ApiResult<Map<String, Object>> homeStats() {
        return ApiResult.ok(analysisService.homeStats());
    }

    @GetMapping("/admin")
    public ApiResult<Map<String, Object>> adminStats() {
        return ApiResult.ok(analysisService.adminStats());
    }

    @GetMapping("/dashboard")
    public ApiResult<Map<String, Object>> dashboard() {
        return ApiResult.ok(analysisService.dashboard());
    }

    @GetMapping("/association1")
    public ApiResult<Map<String, Object>> association1() {
        return ApiResult.ok(analysisService.brandSentiment());
    }

    @GetMapping("/association2")
    public ApiResult<Map<String, Object>> association2() {
        return ApiResult.ok(analysisService.specSatisfaction());
    }

    @GetMapping("/association3")
    public ApiResult<Map<String, Object>> association3() {
        return ApiResult.ok(analysisService.copurchase());
    }
}
