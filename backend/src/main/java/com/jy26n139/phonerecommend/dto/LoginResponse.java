package com.jy26n139.phonerecommend.dto;

public record LoginResponse(String token, Long userId, String username, String role) {
}
