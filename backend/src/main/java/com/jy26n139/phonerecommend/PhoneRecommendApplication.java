package com.jy26n139.phonerecommend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jy26n139.phonerecommend.mapper")
public class PhoneRecommendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhoneRecommendApplication.class, args);
    }
}
