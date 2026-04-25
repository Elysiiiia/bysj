package com.jy26n139.phonerecommend.dto;

import com.jy26n139.phonerecommend.entity.Phone;

import java.util.List;

public record RecommendationExplainItem(
        Phone phone,
        String strategy,
        double finalScore,
        double cfScore,
        double sentimentScore,
        double coldStartScore,
        List<String> sourceProducts,
        List<String> sourceActions
) {
}
