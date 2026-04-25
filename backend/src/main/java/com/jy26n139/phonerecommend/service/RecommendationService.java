package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.entity.UserBehavior;
import com.jy26n139.phonerecommend.mapper.PhoneMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecommendationService {
    private final PhoneMapper phoneMapper;
    private final BehaviorService behaviorService;

    public RecommendationService(PhoneMapper phoneMapper, BehaviorService behaviorService) {
        this.phoneMapper = phoneMapper;
        this.behaviorService = behaviorService;
    }

    public List<Phone> recommend(Long userId, int topN) {
        List<UserBehavior> behaviors = behaviorService.recent(userId, 30);
        if (behaviors.isEmpty()) {
            return coldStart(topN);
        }
        List<Phone> all = phoneMapper.selectList(null);
        Map<String, Phone> byId = new HashMap<>();
        for (Phone phone : all) {
            byId.put(phone.getProductId(), phone);
        }
        Set<String> excluded = behaviors.stream()
                .map(UserBehavior::getProductId)
                .filter(Objects::nonNull)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        Map<String, Double> scores = new HashMap<>();
        for (UserBehavior behavior : behaviors) {
            Phone source = byId.get(behavior.getProductId());
            if (source == null) {
                continue;
            }
            double weight = preferenceWeight(behavior.getAction());
            for (Phone candidate : all) {
                if (candidate.getProductId() == null || excluded.contains(candidate.getProductId())) {
                    continue;
                }
                double score = similarity(source, candidate) * weight;
                scores.merge(candidate.getProductId(), score, Double::sum);
            }
        }
        List<Phone> recommended = scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(e -> byId.get(e.getKey()))
                .filter(Objects::nonNull)
                .toList();
        if (!recommended.isEmpty()) {
            return recommended;
        }
        return coldStart(topN).stream()
                .filter(phone -> phone.getProductId() != null && !excluded.contains(phone.getProductId()))
                .limit(topN)
                .toList();
    }

    public List<Phone> coldStart(int topN) {
        return phoneMapper.selectList(new LambdaQueryWrapper<Phone>()
                .orderByDesc(Phone::getSentimentScore)
                .orderByDesc(Phone::getAvgRating)
                .orderByDesc(Phone::getReviewCount)
                .last("limit " + topN));
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
}
