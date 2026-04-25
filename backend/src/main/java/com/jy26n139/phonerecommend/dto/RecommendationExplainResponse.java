package com.jy26n139.phonerecommend.dto;

import java.util.List;

public record RecommendationExplainResponse(
        List<RecommendationExplainItem> items,
        List<String> history,
        String summary
) {
}
