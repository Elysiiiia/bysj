package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jy26n139.phonerecommend.config.JwtService;
import com.jy26n139.phonerecommend.dto.LoginRequest;
import com.jy26n139.phonerecommend.dto.LoginResponse;
import com.jy26n139.phonerecommend.entity.User;
import com.jy26n139.phonerecommend.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, JwtService jwtService) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username())
                .eq(User::getPassword, request.password())
                .last("limit 1"));
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if ("admin".equals(request.role()) && !"admin".equals(user.getRole())) {
            throw new IllegalArgumentException("该账号没有管理员权限");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRole());
    }
}
