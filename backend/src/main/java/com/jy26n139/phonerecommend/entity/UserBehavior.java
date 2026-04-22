package com.jy26n139.phonerecommend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_behavior")
public class UserBehavior {
    private Long id;
    private Long userId;
    private String productId;
    private String action;
    private String createdAt;
}
