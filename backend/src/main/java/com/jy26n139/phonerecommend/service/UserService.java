package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jy26n139.phonerecommend.common.PageResult;
import com.jy26n139.phonerecommend.entity.User;
import com.jy26n139.phonerecommend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {
    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public PageResult<User> page(long page, long pageSize, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(User::getUsername, keyword);
        }
        wrapper.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(Page.of(page, pageSize), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public void save(User user) {
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
    }

    public void delete(Long id) {
        userMapper.deleteById(id);
    }
}
