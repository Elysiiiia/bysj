package com.jy26n139.phonerecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jy26n139.phonerecommend.common.PageResult;
import com.jy26n139.phonerecommend.entity.Phone;
import com.jy26n139.phonerecommend.mapper.PhoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PhoneService {
    private final PhoneMapper phoneMapper;

    public PhoneService(PhoneMapper phoneMapper) {
        this.phoneMapper = phoneMapper;
    }

    public PageResult<Phone> page(long page, long pageSize, String keyword, String brand) {
        LambdaQueryWrapper<Phone> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Phone::getTitle, keyword).or().like(Phone::getBrand, keyword));
        }
        if (StringUtils.hasText(brand)) {
            wrapper.eq(Phone::getBrand, brand);
        }
        wrapper.orderByDesc(Phone::getSentimentScore).orderByDesc(Phone::getAvgRating);
        Page<Phone> result = phoneMapper.selectPage(Page.of(page, pageSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public Phone byProductId(String productId) {
        return phoneMapper.selectOne(new LambdaQueryWrapper<Phone>().eq(Phone::getProductId, productId).last("limit 1"));
    }

    public List<Phone> featured(int limit) {
        return phoneMapper.selectList(new LambdaQueryWrapper<Phone>()
                .orderByDesc(Phone::getSentimentScore)
                .orderByDesc(Phone::getAvgRating)
                .last("limit " + limit));
    }

    public List<String> brands() {
        return phoneMapper.selectObjs(new LambdaQueryWrapper<Phone>()
                        .select(Phone::getBrand)
                        .isNotNull(Phone::getBrand)
                        .groupBy(Phone::getBrand)
                        .orderByAsc(Phone::getBrand))
                .stream().map(String::valueOf).toList();
    }

    public void save(Phone phone) {
        if (phone.getId() == null) {
            phoneMapper.insert(phone);
        } else {
            phoneMapper.updateById(phone);
        }
    }

    public void delete(Long id) {
        phoneMapper.deleteById(id);
    }
}
