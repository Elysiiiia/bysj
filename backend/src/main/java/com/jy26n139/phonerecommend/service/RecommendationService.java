package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.entity.Comment;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.entity.UserBehavior;
import com.jy26n139.phonerecommend.mapper.CommentMapper;
import com.jy26n139.phonerecommend.mapper.PhoneMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final PhoneMapper phoneMapper;
    private final CommentMapper commentMapper;
    private final BehaviorService behaviorService;

    public RecommendationService(PhoneMapper phoneMapper, CommentMapper commentMapper, BehaviorService behaviorService) {
        this.phoneMapper = phoneMapper;
        this.commentMapper = commentMapper;
        this.behaviorService = behaviorService;
    }

    public List<Phone> recommend(Long userId, int topN) {
        List<UserBehavior> behaviors = behaviorService.recent(userId, 30);
        if (behaviors.isEmpty()) {
            return coldStart(topN);
        }
        List<Phone> all = phoneMapper.selectList(null);
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .select(Comment::getProductId, Comment::getNickname, Comment::getSentimentScore)
                .isNotNull(Comment::getProductId)
                .isNotNull(Comment::getNickname));
        Map<String, Phone> byId = all.stream()
                .filter(phone -> phone.getProductId() != null)
                .collect(Collectors.toMap(Phone::getProductId, Function.identity(), (a, b) -> a));
        Set<String> excluded = behaviors.stream()
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        Map<String, Double> sourceWeights = buildSourceWeights(behaviors);
        Map<String, Map<String, Double>> itemUsers = buildItemUserMatrix(comments);
        UserPreference preference = buildUserPreference(behaviors, byId);

        List<Phone> cfRecs = itemCfRecommend(sourceWeights, itemUsers, byId, excluded, topN);
        List<Phone> sentimentRecs = sentimentRecommend(preference, byId, excluded, topN);

        List<Phone> merged = mergeRecommendations(cfRecs, sentimentRecs, excluded, topN);
        if (merged.size() >= topN) {
            return merged;
        }
        List<Phone> cold = coldStart(topN * 2).stream()
                .filter(phone -> phone.getProductId() != null && !excluded.contains(phone.getProductId()))
                .toList();
        return mergeRecommendations(merged, cold, excluded, topN);
    }

    public List<Phone> coldStart(int topN) {
        return phoneMapper.selectList(null).stream()
                .sorted(Comparator.comparingDouble(this::coldStartScore).reversed())
                .limit(topN)
                .toList();
    }

    public List<Phone> similar(String productId, int topN) {
        Phone target = phoneMapper.selectOne(new LambdaQueryWrapper<Phone>().eq(Phone::getProductId, productId).last("limit 1"));
        if (target == null) {
            return List.of();
        }
        return phoneMapper.selectList(new LambdaQueryWrapper<Phone>().ne(Phone::getProductId, productId))
                .stream()
                .sorted(Comparator.comparingDouble((Phone p) -> similarity(target, p)).reversed())
                .limit(topN)
                .toList();
    }

    private double similarity(Phone a, Phone b) {
        double brand = Objects.equals(a.getBrand(), b.getBrand()) ? 0.4 : 0.0;
        double ap = a.getCurrentPrice() == null ? 0 : a.getCurrentPrice();
        double bp = b.getCurrentPrice() == null ? 0 : b.getCurrentPrice();
        double price = 1.0 - Math.min(Math.abs(ap - bp) / Math.max(ap, 1.0), 1.0);
        double sentiment = 1.0 - Math.abs((a.getSentimentScore() == null ? 0.5 : a.getSentimentScore())
                - (b.getSentimentScore() == null ? 0.5 : b.getSentimentScore()));
        double rating = (b.getAvgRating() == null ? 3.0 : b.getAvgRating()) / 5.0;
        return brand + price * 0.25 + sentiment * 0.25 + rating * 0.10;
    }

    private Map<String, Map<String, Double>> buildItemUserMatrix(List<Comment> comments) {
        Map<String, Map<String, Double>> itemUsers = new HashMap<>();
        for (Comment comment : comments) {
            if (comment.getProductId() == null || comment.getNickname() == null) {
                continue;
            }
            itemUsers.computeIfAbsent(comment.getProductId(), ignored -> new HashMap<>())
                    .put(comment.getNickname(), comment.getSentimentScore() == null ? 0.5 : comment.getSentimentScore());
        }
        return itemUsers;
    }

    private Map<String, Double> buildSourceWeights(List<UserBehavior> behaviors) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (UserBehavior behavior : behaviors) {
            if (behavior.getProductId() == null) {
                continue;
            }
            double weight = preferenceWeight(behavior.getAction());
            weights.putIfAbsent(behavior.getProductId(), weight);
        }
        return weights;
    }

    private UserPreference buildUserPreference(List<UserBehavior> behaviors, Map<String, Phone> byId) {
        Map<String, Integer> brandCounts = new HashMap<>();
        List<Double> prices = new ArrayList<>();
        for (UserBehavior behavior : behaviors) {
            Phone phone = byId.get(behavior.getProductId());
            if (phone == null) {
                continue;
            }
            if (phone.getBrand() != null && !phone.getBrand().isBlank()) {
                brandCounts.merge(phone.getBrand(), 1, Integer::sum);
            }
            if (phone.getCurrentPrice() != null) {
                prices.add(phone.getCurrentPrice());
            }
        }
        Set<String> preferredBrands = brandCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Double avgPrice = prices.isEmpty() ? null : prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new UserPreference(preferredBrands, avgPrice);
    }

    private List<Phone> itemCfRecommend(Map<String, Double> sourceWeights,
                                        Map<String, Map<String, Double>> itemUsers,
                                        Map<String, Phone> byId,
                                        Set<String> excluded,
                                        int topN) {
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, Double> entry : sourceWeights.entrySet()) {
            String productId = entry.getKey();
            Map<String, Double> sourceUsers = itemUsers.get(productId);
            if (sourceUsers == null || sourceUsers.isEmpty()) {
                continue;
            }
            double weight = entry.getValue();
            for (Map.Entry<String, Map<String, Double>> candidate : itemUsers.entrySet()) {
                String candidateId = candidate.getKey();
                if (excluded.contains(candidateId)) {
                    continue;
                }
                double similarity = cosineSimilarity(sourceUsers, candidate.getValue());
                if (similarity <= 0) {
                    continue;
                }
                scores.merge(candidateId, similarity * weight, Double::sum);
            }
        }
        List<String> ranked = scores.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue() * 0.6
                        + normalizedSentiment(byId.get(e.getKey())) * 0.25
                        + normalizedRating(byId.get(e.getKey())) * 0.15))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
        return ranked.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Phone> sentimentRecommend(UserPreference preference,
                                           Map<String, Phone> byId,
                                           Set<String> excluded,
                                           int topN) {
        return byId.values().stream()
                .filter(phone -> phone.getProductId() != null && !excluded.contains(phone.getProductId()))
                .map(phone -> Map.entry(phone, sentimentPreferenceScore(preference, phone)))
                .sorted(Map.Entry.<Phone, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<Phone> mergeRecommendations(List<Phone> primary, List<Phone> secondary, Set<String> excluded, int topN) {
        LinkedHashMap<String, Phone> merged = new LinkedHashMap<>();
        for (Phone phone : primary) {
            if (phone == null || phone.getProductId() == null || excluded.contains(phone.getProductId())) {
                continue;
            }
            merged.putIfAbsent(phone.getProductId(), phone);
            if (merged.size() >= topN) {
                return new ArrayList<>(merged.values());
            }
        }
        for (Phone phone : secondary) {
            if (phone == null || phone.getProductId() == null || excluded.contains(phone.getProductId())) {
                continue;
            }
            merged.putIfAbsent(phone.getProductId(), phone);
            if (merged.size() >= topN) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        Set<String> keys = new HashSet<>(a.keySet());
        keys.retainAll(b.keySet());
        if (keys.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        for (String key : keys) {
            dot += a.getOrDefault(key, 0.0) * b.getOrDefault(key, 0.0);
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (normA * normB);
    }

    private double sentimentPreferenceScore(UserPreference preference, Phone phone) {
        double sentiment = normalizedSentiment(phone) * 0.4;
        double rating = normalizedRating(phone) * 0.2;
        double reviewCount = Math.min((phone.getReviewCount() == null ? 0 : phone.getReviewCount()) / 200.0, 1.0) * 0.1;
        double brandBonus = preference.preferredBrands().contains(phone.getBrand()) ? 0.2 : 0.0;
        double priceBonus = 0.0;
        if (preference.avgPrice() != null && preference.avgPrice() > 0 && phone.getCurrentPrice() != null) {
            double diff = Math.abs(phone.getCurrentPrice() - preference.avgPrice()) / preference.avgPrice();
            priceBonus = Math.max(0.0, 0.1 - diff * 0.1);
        }
        return sentiment + rating + reviewCount + brandBonus + priceBonus;
    }

    private double coldStartScore(Phone phone) {
        return normalizedSentiment(phone) * 0.5
                + normalizedRating(phone) * 0.3
                + Math.min((phone.getReviewCount() == null ? 0 : phone.getReviewCount()) / 200.0, 1.0) * 0.2;
    }

    private double normalizedSentiment(Phone phone) {
        return phone == null || phone.getSentimentScore() == null ? 0.5 : phone.getSentimentScore();
    }

    private double normalizedRating(Phone phone) {
        return phone == null || phone.getAvgRating() == null ? 0.0 : phone.getAvgRating() / 5.0;
    }

    private double preferenceWeight(String action) {
        if (action == null || action.isBlank()) {
            return 1.0;
        }
        return switch (action) {
            case "like" -> 1.6;
            case "comment_positive" -> 1.3;
            case "comment_neutral" -> 0.9;
            case "comment_negative" -> -1.2;
            default -> 1.0;
        };
    }

    private record UserPreference(Set<String> preferredBrands, Double avgPrice) {
    }
}
