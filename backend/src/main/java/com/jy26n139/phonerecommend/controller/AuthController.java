package com.jy26n139.phonerecommend.controller;

import com.jy26n139.phonerecommend.common.ApiResult;
import com.jy26n139.phonerecommend.dto.LoginRequest;
import com.jy26n139.phonerecommend.dto.LoginResponse;
import com.jy26n139.phonerecommend.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            return ApiResult.ok(authService.login(request));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }
}
