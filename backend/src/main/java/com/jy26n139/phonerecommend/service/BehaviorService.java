package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.entity.UserBehavior;
import com.jy26n139.phonerecommend.mapper.UserBehaviorMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class BehaviorService {
    private final UserBehaviorMapper behaviorMapper;

    public BehaviorService(UserBehaviorMapper behaviorMapper) {
        this.behaviorMapper = behaviorMapper;
    }

    public void record(Long userId, String productId, String action) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setProductId(productId);
        behavior.setAction(action == null ? "view" : action);
        behaviorMapper.insert(behavior);
    }

    public List<String> history(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        return behaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                        .eq(UserBehavior::getUserId, userId)
                        .orderByDesc(UserBehavior::getCreatedAt)
                        .last("limit " + limit))
                .stream()
                .map(UserBehavior::getProductId)
                .distinct()
                .toList();
    }

    public List<UserBehavior> recent(Long userId, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return behaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .eq(UserBehavior::getUserId, userId)
                .orderByDesc(UserBehavior::getCreatedAt)
                .last("limit " + limit));
    }
}
