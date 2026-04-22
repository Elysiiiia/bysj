package com.jy26n139.phonerecommend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("phones")
public class Phone {
    private Long id;
    private String brand;
    private String title;
    private Double currentPrice;
    private Double originalPrice;
    private Double discountPrice;
    private String sales;
    private String shopName;
    private String imageUrl;
    private String govSubsidy;
    private String selfOperated;
    private String productId;
    private String linkUrl;
    private Double sentimentScore;
    private Double avgRating;
    private Integer reviewCount;
}
