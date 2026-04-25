package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jy26n139.phonerecommend.common.PageResult;
import com.jy26n139.phonerecommend.config.AuthContext;
import com.jy26n139.phonerecommend.dto.CommentForm;
import com.jy26n139.phonerecommend.dto.SentimentResponse;
import com.jy26n139.phonerecommend.entity.Comment;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.mapper.CommentMapper;
import com.jy26n139.phonerecommend.mapper.PhoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CommentService {
    private final CommentMapper commentMapper;
    private final PhoneMapper phoneMapper;
    private final SentimentClient sentimentClient;
    private final BehaviorService behaviorService;

    public CommentService(CommentMapper commentMapper, PhoneMapper phoneMapper, SentimentClient sentimentClient, BehaviorService behaviorService) {
        this.commentMapper = commentMapper;
        this.phoneMapper = phoneMapper;
        this.sentimentClient = sentimentClient;
        this.behaviorService = behaviorService;
    }

    public PageResult<Comment> page(long page, long pageSize, String keyword, String productId) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(productId)) {
            wrapper.eq(Comment::getProductId, productId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Comment::getNickname, keyword)
                    .or().like(Comment::getBrand, keyword)
                    .or().like(Comment::getContent, keyword));
        }
        wrapper.orderByDesc(Comment::getId);
        Page<Comment> result = commentMapper.selectPage(Page.of(page, pageSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public List<Comment> recent(int limit) {
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>().orderByDesc(Comment::getId).last("limit " + limit));
    }

    public void save(CommentForm form) {
        Comment existing = form.id() == null ? null : commentMapper.selectById(form.id());
        SentimentResponse sentiment = sentimentClient.analyze(form.rating(), form.content());
        Comment comment = new Comment();
        comment.setId(form.id());
        comment.setProductId(form.productId());
        comment.setBrand(form.brand());
        comment.setTitle(form.title());
        comment.setNickname(form.nickname());
        comment.setRating(form.rating());
        comment.setSpec(form.spec());
        comment.setCommentDate(form.commentDate());
        comment.setContent(form.content());
        comment.setSentimentScore(sentiment.score());
        comment.setSentimentLabel(sentiment.label());
        if (comment.getId() == null) {
            commentMapper.insert(comment);
        } else {
            commentMapper.updateById(comment);
        }
        if (existing != null && StringUtils.hasText(existing.getProductId())
                && !existing.getProductId().equals(comment.getProductId())) {
            refreshPhoneStats(existing.getProductId());
        }
        refreshPhoneStats(comment.getProductId());
        recordPreference(comment);
    }

    public void delete(Long id) {
        Comment existing = commentMapper.selectById(id);
        commentMapper.deleteById(id);
        if (existing != null && StringUtils.hasText(existing.getProductId())) {
            refreshPhoneStats(existing.getProductId());
        }
    }

    private void refreshPhoneStats(String productId) {
        if (!StringUtils.hasText(productId)) {
            return;
        }
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getProductId, productId));
        Phone phone = phoneMapper.selectOne(new LambdaQueryWrapper<Phone>()
                .eq(Phone::getProductId, productId)
                .last("limit 1"));
        if (phone == null) {
            return;
        }
        int reviewCount = comments.size();
        double avgRating = comments.stream()
                .map(Comment::getRating)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double sentimentScore = comments.stream()
                .map(Comment::getSentimentScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);
        phone.setAvgRating(Math.round(avgRating * 100.0) / 100.0);
        phone.setReviewCount(reviewCount);
        phone.setSentimentScore(Math.round(sentimentScore * 10000.0) / 10000.0);
        phoneMapper.updateById(phone);
    }

    private void recordPreference(Comment comment) {
        AuthContext.AuthUser authUser = AuthContext.get();
        if (authUser == null || authUser.userId() == null || "admin".equals(authUser.role())) {
            return;
        }
        String label = comment.getSentimentLabel() == null ? "neutral" : comment.getSentimentLabel();
        String action = switch (label) {
            case "positive" -> "comment_positive";
            case "negative" -> "comment_negative";
            default -> "comment_neutral";
        };
        behaviorService.record(authUser.userId(), comment.getProductId(), action);
    }
}
