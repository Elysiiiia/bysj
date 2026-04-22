package com.jy26n139.phonerecommend.common;

import java.util.List;

public record PageResult<T>(List<T> rows, long total, long page, long pageSize) {
}
