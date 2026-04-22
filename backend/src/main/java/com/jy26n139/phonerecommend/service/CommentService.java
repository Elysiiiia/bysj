package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jy26n139.phonerecommend.common.PageResult;
import com.jy26n139.phonerecommend.dto.CommentForm;
import com.jy26n139.phonerecommend.dto.SentimentResponse;
import com.jy26n139.phonerecommend.entity.Comment;
import com.jy26n139.phonerecommend.mapper.CommentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CommentService {
    private final CommentMapper commentMapper;
    private final SentimentClient sentimentClient;

    public CommentService(CommentMapper commentMapper, SentimentClient sentimentClient) {
        this.commentMapper = commentMapper;
        this.sentimentClient = sentimentClient;
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
    }

    public void delete(Long id) {
        commentMapper.deleteById(id);
    }
}
