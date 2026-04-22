package com.jy26n139.phonerecommend.controller;

import com.jy26n139.phonerecommend.common.ApiResult;
import com.jy26n139.phonerecommend.common.PageResult;
import com.jy26n139.phonerecommend.entity.User;
import com.jy26n139.phonerecommend.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResult<PageResult<User>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String q) {
        return ApiResult.ok(userService.page(page, pageSize, q));
    }

    @PostMapping
    public ApiResult<Void> save(@RequestBody User user) {
        userService.save(user);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResult.ok(null);
    }
}
