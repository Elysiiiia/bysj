# 基于关键词匹配的中文情感分析服务

POSITIVE_KEYWORDS = [
    '好用', '满意', '喜欢', '推荐', '不错', '优秀', '完美', '超好', '值得',
    '清晰', '流畅', '快速', '高清', '耐用', '舒适', '漂亮', '好看', '惊艳', '超值',
    '性价比', '划算', '实惠', '给力', '顺滑', '细腻', '高端', '精致', '稳定',
    '满分', '棒', '赞', '厉害', '强大', '出色', '可靠', '非常好', '很好', '极好',
    '拍照好', '音质好', '手感好', '屏幕好', '快充好', '系统流畅', '非常满意',
    '物超所值', '颜值高', '做工好', '物流快', '包装好', '正品', '强烈推荐',
]

NEGATIVE_KEYWORDS = [
    '不好', '失望', '差', '垃圾', '退货', '质量差', '卡顿', '发热', '发烫',
    '坏了', '假货', '后悔', '糟糕', '烂', '不值', '坑', '骗人', '难用',
    '模糊', '卡', '慢', '费电', '掉电', '充不进', '不稳定', '死机', '崩溃',
    '不推荐', '别买', '避雷', '翻车', '掉漆', '做工差', '虚假宣传', '有问题',
    '很差', '太差', '极差', '一般', '不满意', '不行', '太烫', '太慢', '太重',
]


def calculate_sentiment_score(rating: float, content: str) -> tuple[float, str]:
    """
    综合评分和评论内容计算情感分数。
    返回 (score 0~1, label)
    score: 0=负面, 0.5=中性, 1=正面
    """
    # 评分基础分 (占 70%)
    base = (float(rating) - 1) / 4.0  # 转为 0~1

    # 关键词得分 (占 30%)
    pos = sum(1 for kw in POSITIVE_KEYWORDS if kw in content)
    neg = sum(1 for kw in NEGATIVE_KEYWORDS if kw in content)
    total_kw = pos + neg
    if total_kw > 0:
        kw_score = pos / total_kw
    else:
        kw_score = 0.5  # 无关键词默认中性

    score = base * 0.7 + kw_score * 0.3
    score = max(0.0, min(1.0, score))

    if score >= 0.75:
        label = 'positive'
    elif score >= 0.50:
        label = 'neutral'
    else:
        label = 'negative'

    return round(score, 4), label


def label_zh(label: str) -> str:
    mapping = {'positive': '正面', 'neutral': '中性', 'negative': '负面'}
    return mapping.get(label, '未知')


def extract_keywords(texts: list[str], top_n: int = 50) -> list[dict]:
    """
    从评论列表中提取高频关键词（不依赖 jieba，用简单词频统计）。
    """
    stopwords = {
        '的', '了', '是', '在', '我', '有', '和', '就', '不', '人', '都', '一',
        '上', '也', '很', '到', '说', '要', '去', '你', '会', '着', '没有', '看',
        '好', '自己', '这', '那', '他', '她', '它', '但', '都', '还', '与', '或',
        '因为', '所以', '如果', '虽然', '然后', '这个', '这样', '这么', '那么',
        '什么', '怎么', '为什么', '一个', '一些', '可以', '没有', '非常', '真的',
        '感觉', '觉得', '手机', '买', '用', '来', '下', '对', '把', '被', '让',
        '给', '从', '以', '比', '比较', '时候', '已经', '一直', '只是', '只有',
        '而且', '不是', '就是', '但是', '而是', '同时', '还是', '其实', '这次',
    }

    freq: dict[str, int] = {}
    for text in texts:
        if not text:
            continue
        # 简单 bigram + trigram 分词
        for n in (2, 3):
            for i in range(len(text) - n + 1):
                word = text[i:i + n]
                if any(c in stopwords for c in word):
                    continue
                # 过滤纯数字、含特殊符号
                if word.isdigit():
                    continue
                if any(c in word for c in '，。！？,.!?、\n\r\t '):
                    continue
                freq[word] = freq.get(word, 0) + 1

    sorted_words = sorted(freq.items(), key=lambda x: -x[1])
    return [{'name': w, 'value': c} for w, c in sorted_words[:top_n] if c >= 3]
