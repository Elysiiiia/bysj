package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.entity.Phone;
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
        List<String> history = behaviorService.history(userId, 30);
        if (history.isEmpty()) {
            return coldStart(topN);
        }
        List<Phone> all = phoneMapper.selectList(null);
        Map<String, Phone> byId = new HashMap<>();
        for (Phone phone : all) {
            byId.put(phone.getProductId(), phone);
        }
        Set<String> excluded = new HashSet<>(history);
        Map<String, Double> scores = new HashMap<>();
        for (String pid : history) {
            Phone source = byId.get(pid);
            if (source == null) {
                continue;
            }
            for (Phone candidate : all) {
                if (candidate.getProductId() == null || excluded.contains(candidate.getProductId())) {
                    continue;
                }
                double score = similarity(source, candidate);
                scores.merge(candidate.getProductId(), score, Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(e -> byId.get(e.getKey()))
                .filter(Objects::nonNull)
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
}
