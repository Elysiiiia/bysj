package com.jy26n139.phonerecommend.service;

import com.jy26n139.phonerecommend.dto.SentimentResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SentimentClient {
    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "好用", "满意", "喜欢", "推荐", "不错", "优秀", "完美", "超好", "值得",
            "清晰", "流畅", "快速", "高清", "耐用", "舒适", "漂亮", "好看", "惊艳", "超值",
            "性价比", "划算", "实惠", "给力", "顺滑", "细腻", "高端", "精致", "稳定",
            "满分", "棒", "赞", "厉害", "强大", "出色", "可靠", "非常好", "很好", "极好",
            "拍照好", "音质好", "手感好", "屏幕好", "快充好", "系统流畅", "非常满意",
            "物超所值", "颜值高", "做工好", "物流快", "包装好", "正品", "强烈推荐"
    );
    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "不好", "失望", "差", "垃圾", "退货", "质量差", "卡顿", "发热", "发烫",
            "坏了", "假货", "后悔", "糟糕", "烂", "不值", "坑", "骗人", "难用",
            "模糊", "卡", "慢", "费电", "掉电", "充不进", "不稳定", "死机", "崩溃",
            "不推荐", "别买", "避雷", "翻车", "掉漆", "做工差", "虚假宣传", "有问题",
            "很差", "太差", "极差", "一般", "不满意", "不行", "太烫", "太慢", "太重"
    );

    public SentimentResponse analyze(Double rating, String content) {
        double safeRating = rating == null ? 3.0 : rating;
        String safeContent = content == null ? "" : content;

        double base = (safeRating - 1.0) / 4.0;
        int positiveCount = countKeywords(safeContent, POSITIVE_KEYWORDS);
        int negativeCount = countKeywords(safeContent, NEGATIVE_KEYWORDS);
        int totalKeywordCount = positiveCount + negativeCount;

        double keywordScore = totalKeywordCount > 0 ? (double) positiveCount / totalKeywordCount : 0.5;
        double score = clamp(base * 0.7 + keywordScore * 0.3);
        String label = score >= 0.75 ? "positive" : score >= 0.5 ? "neutral" : "negative";
        return new SentimentResponse(round4(score), label);
    }

    private int countKeywords(String content, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
