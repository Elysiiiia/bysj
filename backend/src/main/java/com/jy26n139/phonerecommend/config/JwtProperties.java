package com.jy26n139.phonerecommend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record JwtProperties(String jwtSecret, String sentimentServiceUrl, String corsOrigin) {
}
