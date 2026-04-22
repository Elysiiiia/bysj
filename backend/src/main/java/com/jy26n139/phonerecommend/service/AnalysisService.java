package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.entity.Comment;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.entity.User;
import com.jy26n139.phonerecommend.entity.UserBehavior;
import com.jy26n139.phonerecommend.mapper.CommentMapper;
import com.jy26n139.phonerecommend.mapper.PhoneMapper;
import com.jy26n139.phonerecommend.mapper.UserBehaviorMapper;
import com.jy26n139.phonerecommend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisService {
    private final PhoneMapper phoneMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final UserBehaviorMapper behaviorMapper;

    public AnalysisService(PhoneMapper phoneMapper, CommentMapper commentMapper, UserMapper userMapper, UserBehaviorMapper behaviorMapper) {
        this.phoneMapper = phoneMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.behaviorMapper = behaviorMapper;
    }

    public Map<String, Object> homeStats() {
        long totalPhones = phoneMapper.selectCount(null);
        long totalComments = commentMapper.selectCount(null);
        long positive = commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getSentimentLabel, "positive"));
        double posRate = totalComments == 0 ? 0 : Math.round(positive * 1000.0 / totalComments) / 10.0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPhones", totalPhones);
        result.put("totalComments", totalComments);
        result.put("posRate", posRate);
        return result;
    }

    public Map<String, Object> adminStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phones", phoneMapper.selectCount(null));
        result.put("comments", commentMapper.selectCount(null));
        result.put("users", userMapper.selectCount(null));
        result.put("behaviors", behaviorMapper.selectCount(null));
        return result;
    }

    public Map<String, Object> dashboard() {
        List<Phone> phones = phoneMapper.selectList(null);
        List<Comment> comments = commentMapper.selectList(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brandSales", brandSales(phones));
        result.put("priceRanges", priceRanges(phones));
        result.put("sentimentDist", sentimentDist(comments));
        result.put("monthlyTrend", monthlyTrend(comments));
        result.put("radarData", radarData(comments));
        result.put("wordcloud", List.of());
        return result;
    }

    public Map<String, Object> brandSentiment() {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().isNotNull(Comment::getBrand));
        Map<String, Map<String, Long>> grouped = comments.stream()
                .collect(Collectors.groupingBy(Comment::getBrand, Collectors.groupingBy(c -> labelZh(c.getSentimentLabel()), Collectors.counting())));
        List<String> brands = grouped.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Map<String, Long>> e) -> -e.getValue().values().stream().mapToLong(Long::longValue).sum()))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
        List<String> labels = List.of("正面", "中性", "负面");
        List<Map<String, Object>> series = labels.stream().map(label -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", label);
            item.put("data", brands.stream().map(b -> grouped.getOrDefault(b, Map.of()).getOrDefault(label, 0L)).toList());
            return item;
        }).toList();
        List<Map<String, Object>> brandStats = brands.stream().map(b -> {
            Map<String, Long> stat = grouped.getOrDefault(b, Map.of());
            long total = stat.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("brand", b);
            row.put("total", total);
            row.put("positive", stat.getOrDefault("正面", 0L));
            row.put("neutral", stat.getOrDefault("中性", 0L));
            row.put("negative", stat.getOrDefault("负面", 0L));
            row.put("posRate", total == 0 ? 0 : Math.round(stat.getOrDefault("正面", 0L) * 1000.0 / total) / 10.0);
            return row;
        }).toList();
        return Map.of("brands", brands, "labels", labels, "series", series, "brandStats", brandStats);
    }

    public Map<String, Object> specSatisfaction() {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().isNotNull(Comment::getSpec));
        Map<String, List<Double>> specs = new HashMap<>();
        for (Comment c : comments) {
            String key = extractSpec(c.getSpec());
            if (key != null) {
                specs.computeIfAbsent(key, k -> new ArrayList<>()).add(c.getSentimentScore() == null ? 0.5 : c.getSentimentScore());
            }
        }
        List<Map<String, Object>> specData = specs.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .sorted(Comparator.comparingInt((Map.Entry<String, List<Double>> e) -> -e.getValue().size()))
                .limit(15)
                .map(e -> Map.<String, Object>of(
                        "spec", e.getKey(),
                        "count", e.getValue().size(),
                        "avgSentiment", avg(e.getValue()),
                        "avgSentimentPct", Math.round(avg(e.getValue()) * 1000.0) / 10.0))
                .toList();
        return Map.of("specData", specData, "priceSentiment", priceRanges(phoneMapper.selectList(null)), "scatter", List.of());
    }

    public Map<String, Object> copurchase() {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().isNotNull(Comment::getNickname));
        Map<String, Set<String>> userProducts = new HashMap<>();
        for (Comment c : comments) {
            userProducts.computeIfAbsent(c.getNickname(), k -> new HashSet<>()).add(c.getProductId());
        }
        Map<String, Long> pairs = new HashMap<>();
        for (Set<String> products : userProducts.values()) {
            List<String> list = products.stream().sorted().toList();
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    pairs.merge(list.get(i) + "||" + list.get(j), 1L, Long::sum);
                }
            }
        }
        List<Map<String, Object>> links = pairs.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> {
                    String[] parts = e.getKey().split("\\|\\|");
                    return Map.<String, Object>of("source", parts[0], "target", parts[1], "value", e.getValue());
                })
                .toList();
        Set<String> nodeNames = links.stream().flatMap(l -> List.of(String.valueOf(l.get("source")), String.valueOf(l.get("target"))).stream()).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> nodes = nodeNames.stream().map(n -> Map.<String, Object>of("name", n)).toList();
        return Map.of("nodes", nodes, "links", links, "brands", List.of(), "heatData", List.of(), "rules", List.of());
    }

    private List<Map<String, Object>> brandSales(List<Phone> phones) {
        return phones.stream()
                .collect(Collectors.groupingBy(p -> Optional.ofNullable(p.getBrand()).orElse("未知"), Collectors.summingDouble(p -> parseSales(p.getSales()))))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(8)
                .map(e -> Map.<String, Object>of("brand", e.getKey(), "sales", e.getValue().longValue()))
                .toList();
    }

    private List<Map<String, Object>> priceRanges(List<Phone> phones) {
        Map<String, Long> counts = phones.stream().collect(Collectors.groupingBy(p -> priceRange(p.getCurrentPrice()), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().map(e -> Map.<String, Object>of("range", e.getKey(), "count", e.getValue())).toList();
    }

    private List<Map<String, Object>> sentimentDist(List<Comment> comments) {
        return comments.stream()
                .collect(Collectors.groupingBy(c -> Optional.ofNullable(c.getSentimentLabel()).orElse("neutral"), Collectors.counting()))
                .entrySet().stream()
                .map(e -> Map.<String, Object>of("label", e.getKey(), "count", e.getValue()))
                .toList();
    }

    private List<Map<String, Object>> monthlyTrend(List<Comment> comments) {
        return comments.stream()
                .filter(c -> c.getCommentDate() != null && c.getCommentDate().length() >= 7)
                .collect(Collectors.groupingBy(c -> c.getCommentDate().substring(0, 7), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "month", e.getKey(),
                        "count", e.getValue().size(),
                        "avgRating", avg(e.getValue().stream().map(c -> c.getRating() == null ? 0.0 : c.getRating()).toList())))
                .toList();
    }

    private List<Map<String, Object>> radarData(List<Comment> comments) {
        return comments.stream()
                .filter(c -> c.getBrand() != null)
                .collect(Collectors.groupingBy(Comment::getBrand))
                .entrySet().stream()
                .limit(6)
                .map(e -> Map.<String, Object>of(
                        "brand", e.getKey(),
                        "avgSentiment", avg(e.getValue().stream().map(c -> (c.getSentimentScore() == null ? 0.5 : c.getSentimentScore()) * 100).toList()),
                        "avgRating", avg(e.getValue().stream().map(c -> (c.getRating() == null ? 3.0 : c.getRating()) * 20).toList()),
                        "reviewCnt", Math.min(e.getValue().size() / 5.0, 100.0)))
                .toList();
    }

    private String labelZh(String label) {
        return switch (label == null ? "neutral" : label) {
            case "positive" -> "正面";
            case "negative" -> "负面";
            default -> "中性";
        };
    }

    private String extractSpec(String spec) {
        if (spec == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)(GB|TB)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(spec);
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group(1) + matcher.group(2).toUpperCase());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("+", parts.subList(Math.max(0, parts.size() - 2), parts.size()));
    }

    private double avg(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 1000.0) / 1000.0;
    }

    private double parseSales(String sales) {
        if (sales == null || sales.isBlank()) {
            return 0;
        }
        String normalized = sales.replace("+", "").trim();
        try {
            if (normalized.endsWith("万")) {
                return Double.parseDouble(normalized.replace("万", "")) * 10000;
            }
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String priceRange(Double price) {
        double p = price == null ? 0 : price;
        if (p < 2000) return "2000以下";
        if (p < 3000) return "2000-3000";
        if (p < 4000) return "3000-4000";
        if (p < 5000) return "4000-5000";
        return "5000以上";
    }
}
