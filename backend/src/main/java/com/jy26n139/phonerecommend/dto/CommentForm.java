package com.jy26n139.phonerecommend.dto;

public record CommentForm(
        Long id,
        String productId,
        String brand,
        String title,
        String nickname,
        Double rating,
        String spec,
        String commentDate,
        String content
) {
}
