package com.jy26n139.phonerecommend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("comments")
public class Comment {
    private Long id;
    private String productId;
    private String brand;
    private String title;
    private String nickname;
    private Double rating;
    private String spec;
    private String commentDate;
    private String content;
    private Double sentimentScore;
    private String sentimentLabel;
}
