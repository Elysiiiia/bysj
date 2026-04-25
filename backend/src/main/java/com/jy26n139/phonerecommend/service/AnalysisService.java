package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.entity.Comment;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.mapper.CommentMapper;
import com.jy26n139.phonerecommend.mapper.PhoneMapper;
import com.jy26n139.phonerecommend.mapper.UserBehaviorMapper;
import com.jy26n139.phonerecommend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AnalysisService {
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看",
            "好", "自己", "这", "那", "但", "还", "与", "或", "因为", "所以", "如果",
            "虽然", "然后", "这个", "这样", "这么", "那么", "什么", "怎么", "一个",
            "一些", "可以", "非常", "真的", "感觉", "觉得", "手机", "买", "用"
    );

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
        List<Phone> phones = phoneMapper.selectList(null);
        List<Comment> recentComments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>().orderByDesc(Comment::getId).last("limit 5")
        );
        List<Map<String, Object>> brandCounts = phones.stream()
                .filter(phone -> phone.getBrand() != null)
                .collect(Collectors.groupingBy(Phone::getBrand, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> mapOf("brand", e.getKey(), "cnt", e.getValue()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phones", phoneMapper.selectCount(null));
        result.put("comments", commentMapper.selectCount(null));
        result.put("users", userMapper.selectCount(null));
        result.put("behaviors", behaviorMapper.selectCount(null));
        result.put("brands", brandCounts);
        result.put("recentComments", recentComments);
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
        result.put("wordcloud", wordcloud(comments));
        return result;
    }

    public Map<String, Object> brandSentiment() {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().isNotNull(Comment::getBrand));
        Map<String, Map<String, Long>> grouped = comments.stream()
                .collect(Collectors.groupingBy(
                        c -> Optional.ofNullable(c.getBrand()).orElse("未知"),
                        Collectors.groupingBy(c -> labelZh(c.getSentimentLabel()), Collectors.counting())
                ));

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
            return mapOf(
                    "brand", b,
                    "total", total,
                    "positive", stat.getOrDefault("正面", 0L),
                    "neutral", stat.getOrDefault("中性", 0L),
                    "negative", stat.getOrDefault("负面", 0L),
                    "posRate", total == 0 ? 0 : Math.round(stat.getOrDefault("正面", 0L) * 1000.0 / total) / 10.0
            );
        }).toList();
        return mapOf("brands", brands, "labels", labels, "series", series, "brandStats", brandStats);
    }

    public Map<String, Object> specSatisfaction() {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().isNotNull(Comment::getSpec));
        Pattern storagePattern = Pattern.compile("(\\d+)(GB|TB)", Pattern.CASE_INSENSITIVE);
        Map<String, List<Double>> specScores = new HashMap<>();

        for (Comment comment : comments) {
            String key = extractSpec(comment.getSpec(), storagePattern);
            if (key != null) {
                specScores.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(comment.getSentimentScore() == null ? 0.5 : comment.getSentimentScore());
            }
        }

        List<Map<String, Object>> specData = specScores.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .sorted(Comparator.comparingInt((Map.Entry<String, List<Double>> e) -> -e.getValue().size()))
                .limit(15)
                .map(e -> {
                    double avg = avg(e.getValue());
                    return mapOf(
                            "spec", e.getKey(),
                            "count", e.getValue().size(),
                            "avgSentiment", avg,
                            "avgSentimentPct", Math.round(avg * 1000.0) / 10.0
                    );
                })
                .toList();

        List<Map<String, Object>> priceSentiment = priceSentiment(phoneMapper.selectList(null));
        List<Map<String, Object>> scatter = comments.stream()
                .filter(comment -> comment.getRating() != null)
                .limit(500)
                .map(comment -> mapOf(
                        "x", comment.getRating(),
                        "y", round3(comment.getSentimentScore() == null ? 0.5 : comment.getSentimentScore()),
                        "brand", comment.getBrand()
                ))
                .toList();

        return mapOf("specData", specData, "priceSentiment", priceSentiment, "scatter", scatter);
    }

    public Map<String, Object> copurchase() {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().isNotNull(Comment::getNickname));
        List<Phone> phones = phoneMapper.selectList(null);
        Map<String, Phone> phoneMap = phones.stream()
                .filter(phone -> phone.getProductId() != null)
                .collect(Collectors.toMap(Phone::getProductId, phone -> phone, (a, b) -> a));

        Map<String, Set<String>> userProducts = new HashMap<>();
        for (Comment comment : comments) {
            userProducts.computeIfAbsent(comment.getNickname(), ignored -> new HashSet<>()).add(comment.getProductId());
        }

        Map<String, Integer> pairCount = new HashMap<>();
        for (Set<String> products : userProducts.values()) {
            List<String> sorted = products.stream().filter(Objects::nonNull).sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    pairCount.merge(sorted.get(i) + "||" + sorted.get(j), 1, Integer::sum);
                }
            }
        }

        List<Map.Entry<String, Integer>> topPairs = pairCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .toList();

        Map<String, Integer> nodeWeight = new HashMap<>();
        Set<String> nodeIds = new LinkedHashSet<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map.Entry<String, Integer> pair : topPairs) {
            String[] ids = pair.getKey().split("\\|\\|");
            if (ids.length != 2) {
                continue;
            }
            String source = ids[0];
            String target = ids[1];
            nodeIds.add(source);
            nodeIds.add(target);
            nodeWeight.merge(source, pair.getValue(), Integer::sum);
            nodeWeight.merge(target, pair.getValue(), Integer::sum);
            links.add(mapOf(
                    "source", nodeLabel(source, phoneMap),
                    "target", nodeLabel(target, phoneMap),
                    "value", pair.getValue()
            ));
        }

        int maxWeight = Math.max(nodeWeight.values().stream().mapToInt(Integer::intValue).max().orElse(1), 1);
        List<Map<String, Object>> nodes = nodeIds.stream()
                .map(id -> {
                    Phone phone = phoneMap.get(id);
                    int weight = nodeWeight.getOrDefault(id, 1);
                    return mapOf(
                            "name", nodeLabel(id, phoneMap),
                            "brand", phone == null ? "" : Optional.ofNullable(phone.getBrand()).orElse(""),
                            "symbolSize", 18 + Math.round(weight * 36f / maxWeight)
                    );
                })
                .toList();

        Map<String, Set<String>> userBrands = new HashMap<>();
        for (Comment comment : comments) {
            Phone phone = phoneMap.get(comment.getProductId());
            if (phone != null && phone.getBrand() != null) {
                userBrands.computeIfAbsent(comment.getNickname(), ignored -> new HashSet<>()).add(phone.getBrand());
            }
        }

        Map<String, Integer> brandPairCount = new HashMap<>();
        for (Set<String> brands : userBrands.values()) {
            List<String> sorted = brands.stream().sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    brandPairCount.merge(sorted.get(i) + "||" + sorted.get(j), 1, Integer::sum);
                }
            }
        }

        List<String> brands = brandPairCount.keySet().stream()
                .flatMap(pair -> List.of(pair.split("\\|\\|")).stream())
                .distinct()
                .sorted()
                .toList();
        List<List<Object>> heatData = new ArrayList<>();
        for (int i = 0; i < brands.size(); i++) {
            for (int j = 0; j < brands.size(); j++) {
                if (i == j) {
                    heatData.add(List.of(i, j, 0));
                    continue;
                }
                String pair = brands.get(Math.min(i, j)) + "||" + brands.get(Math.max(i, j));
                heatData.add(List.of(i, j, brandPairCount.getOrDefault(pair, 0)));
            }
        }

        int totalUsers = Math.max(userBrands.size(), 1);
        List<Map<String, Object>> rules = topPairs.stream()
                .limit(10)
                .map(pair -> {
                    String[] ids = pair.getKey().split("\\|\\|");
                    Phone left = phoneMap.get(ids[0]);
                    Phone right = phoneMap.get(ids[1]);
                    return mapOf(
                            "antecedent", left == null ? ids[0] : Optional.ofNullable(left.getBrand()).orElse(ids[0]),
                            "consequent", right == null ? ids[1] : Optional.ofNullable(right.getBrand()).orElse(ids[1]),
                            "support", round4(pair.getValue() * 1.0 / totalUsers),
                            "count", pair.getValue()
                    );
                })
                .toList();

        return mapOf("nodes", nodes, "links", links, "brands", brands, "heatData", heatData, "rules", rules);
    }

    private List<Map<String, Object>> brandSales(List<Phone> phones) {
        return phones.stream()
                .collect(Collectors.groupingBy(
                        p -> Optional.ofNullable(p.getBrand()).orElse("未知"),
                        Collectors.summingDouble(p -> parseSales(p.getSales()))
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(8)
                .map(e -> mapOf("brand", e.getKey(), "sales", e.getValue().longValue()))
                .toList();
    }

    private List<Map<String, Object>> priceRanges(List<Phone> phones) {
        Map<String, Long> counts = phones.stream()
                .collect(Collectors.groupingBy(p -> priceRange(p.getCurrentPrice()), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().map(e -> mapOf("range", e.getKey(), "count", e.getValue())).toList();
    }

    private List<Map<String, Object>> priceSentiment(List<Phone> phones) {
        String[] ranges = {"2000以下", "2000-3000", "3000-4000", "4000-5000", "5000以上"};
        return List.of(ranges).stream().map(range -> {
            List<Double> scores = phones.stream()
                    .filter(phone -> range.equals(priceRange(phone.getCurrentPrice())))
                    .map(phone -> phone.getSentimentScore() == null ? 0.5 : phone.getSentimentScore())
                    .toList();
            return mapOf(
                    "range", range,
                    "avg", scores.isEmpty() ? 0 : avg(scores),
                    "count", scores.size()
            );
        }).toList();
    }

    private List<Map<String, Object>> sentimentDist(List<Comment> comments) {
        return comments.stream()
                .collect(Collectors.groupingBy(c -> Optional.ofNullable(c.getSentimentLabel()).orElse("neutral"), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> mapOf("label", e.getKey(), "count", e.getValue()))
                .toList();
    }

    private List<Map<String, Object>> monthlyTrend(List<Comment> comments) {
        return comments.stream()
                .filter(c -> c.getCommentDate() != null && c.getCommentDate().length() >= 7)
                .collect(Collectors.groupingBy(c -> c.getCommentDate().substring(0, 7), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> mapOf(
                        "month", e.getKey(),
                        "count", e.getValue().size(),
                        "avgRating", round2(e.getValue().stream().map(c -> c.getRating() == null ? 0.0 : c.getRating()).mapToDouble(Double::doubleValue).average().orElse(0))
                ))
                .toList();
    }

    private List<Map<String, Object>> radarData(List<Comment> comments) {
        return comments.stream()
                .filter(c -> c.getBrand() != null)
                .collect(Collectors.groupingBy(Comment::getBrand))
                .entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<Comment>> e) -> -e.getValue().size()))
                .limit(6)
                .map(e -> {
                    List<Comment> items = e.getValue();
                    return mapOf(
                            "brand", e.getKey(),
                            "avgSentiment", round1(items.stream().map(c -> (c.getSentimentScore() == null ? 0.5 : c.getSentimentScore()) * 100).mapToDouble(Double::doubleValue).average().orElse(50)),
                            "avgRating", round1(items.stream().map(c -> (c.getRating() == null ? 3.0 : c.getRating()) * 20).mapToDouble(Double::doubleValue).average().orElse(60)),
                            "reviewCnt", Math.min(items.size() / 5.0, 100.0),
                            "maxSentiment", round1(items.stream().map(c -> (c.getSentimentScore() == null ? 0.5 : c.getSentimentScore()) * 100).mapToDouble(Double::doubleValue).max().orElse(100)),
                            "minSentiment", round1(items.stream().map(c -> (c.getSentimentScore() == null ? 0.5 : c.getSentimentScore()) * 100).mapToDouble(Double::doubleValue).min().orElse(0))
                    );
                })
                .toList();
    }

    private List<Map<String, Object>> wordcloud(List<Comment> comments) {
        Map<String, Integer> freq = new HashMap<>();
        comments.stream().limit(500).map(Comment::getContent).filter(Objects::nonNull).forEach(text -> {
            for (int n = 2; n <= 3; n++) {
                for (int i = 0; i <= text.length() - n; i++) {
                    String word = text.substring(i, i + n);
                    if (word.chars().anyMatch(ch -> Character.isWhitespace(ch) || "，。！？,.!?、".indexOf(ch) >= 0)) {
                        continue;
                    }
                    if (word.chars().allMatch(Character::isDigit)) {
                        continue;
                    }
                    if (word.chars().anyMatch(ch -> STOPWORDS.contains(String.valueOf((char) ch)))) {
                        continue;
                    }
                    freq.merge(word, 1, Integer::sum);
                }
            }
        });
        return freq.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(60)
                .map(e -> mapOf("name", e.getKey(), "value", e.getValue()))
                .toList();
    }

    private String labelZh(String label) {
        return switch (label == null ? "neutral" : label) {
            case "positive" -> "正面";
            case "negative" -> "负面";
            default -> "中性";
        };
    }

    private String extractSpec(String spec, Pattern pattern) {
        if (spec == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(spec);
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group(1) + matcher.group(2).toUpperCase());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("+", parts.subList(Math.max(0, parts.size() - 2), parts.size()));
    }

    private String nodeLabel(String productId, Map<String, Phone> phoneMap) {
        Phone phone = phoneMap.get(productId);
        if (phone == null) {
            return productId;
        }
        String brand = Optional.ofNullable(phone.getBrand()).orElse("");
        String title = Optional.ofNullable(phone.getTitle()).orElse(productId);
        String shortTitle = title.length() > 10 ? title.substring(0, 10) : title;
        return brand.isBlank() ? shortTitle : brand + "·" + shortTitle;
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

    private double avg(List<Double> values) {
        return values.isEmpty() ? 0 : round3(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
