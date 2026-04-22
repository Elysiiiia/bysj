from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="Phone Recommend Sentiment Service")

POSITIVE_KEYWORDS = [
    "好用", "满意", "喜欢", "推荐", "不错", "优秀", "完美", "超好", "值得",
    "清晰", "流畅", "快速", "高清", "耐用", "舒适", "漂亮", "好看", "惊艳", "超值",
    "性价比", "划算", "实惠", "给力", "顺滑", "细腻", "高端", "精致", "稳定",
    "满分", "棒", "赞", "厉害", "强大", "出色", "可靠", "非常好", "很好", "极好",
    "拍照好", "音质好", "手感好", "屏幕好", "快充好", "系统流畅", "非常满意",
    "物超所值", "颜值高", "做工好", "物流快", "包装好", "正品", "强烈推荐",
]

NEGATIVE_KEYWORDS = [
    "不好", "失望", "差", "垃圾", "退货", "质量差", "卡顿", "发热", "发烫",
    "坏了", "假货", "后悔", "糟糕", "烂", "不值", "坑", "骗人", "难用",
    "模糊", "卡", "慢", "费电", "掉电", "充不进", "不稳定", "死机", "崩溃",
    "不推荐", "别买", "避雷", "翻车", "掉漆", "做工差", "虚假宣传", "有问题",
    "很差", "太差", "极差", "一般", "不满意", "不行", "太烫", "太慢", "太重",
]

STOPWORDS = {
    "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
    "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看",
    "好", "自己", "这", "那", "但", "还", "与", "或", "因为", "所以", "如果",
    "虽然", "然后", "这个", "这样", "这么", "那么", "什么", "怎么", "一个",
    "一些", "可以", "非常", "真的", "感觉", "觉得", "手机", "买", "用",
}


class AnalyzeRequest(BaseModel):
    rating: float = 3.0
    content: str = ""


class AnalyzeResponse(BaseModel):
    score: float
    label: str


class BatchAnalyzeRequest(BaseModel):
    items: list[AnalyzeRequest]


class KeywordsRequest(BaseModel):
    texts: list[str]
    top_n: int = 50


def calculate_sentiment_score(rating: float, content: str) -> tuple[float, str]:
    base = (float(rating) - 1) / 4.0
    pos = sum(1 for keyword in POSITIVE_KEYWORDS if keyword in content)
    neg = sum(1 for keyword in NEGATIVE_KEYWORDS if keyword in content)
    total_keywords = pos + neg
    keyword_score = pos / total_keywords if total_keywords else 0.5
    score = max(0.0, min(1.0, base * 0.7 + keyword_score * 0.3))
    if score >= 0.75:
        label = "positive"
    elif score >= 0.50:
        label = "neutral"
    else:
        label = "negative"
    return round(score, 4), label


def extract_keywords(texts: list[str], top_n: int = 50) -> list[dict[str, int | str]]:
    freq: dict[str, int] = {}
    for text in texts:
        if not text:
            continue
        for n in (2, 3):
            for i in range(len(text) - n + 1):
                word = text[i:i + n]
                if any(char in STOPWORDS for char in word):
                    continue
                if word.isdigit() or any(char in word for char in "，。！？,.!?、\n\r\t "):
                    continue
                freq[word] = freq.get(word, 0) + 1
    return [
        {"name": word, "value": count}
        for word, count in sorted(freq.items(), key=lambda item: -item[1])[:top_n]
        if count >= 3
    ]


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "up"}


@app.post("/api/sentiment/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    score, label = calculate_sentiment_score(request.rating, request.content or "")
    return AnalyzeResponse(score=score, label=label)


@app.post("/api/sentiment/batch", response_model=list[AnalyzeResponse])
def batch_analyze(request: BatchAnalyzeRequest) -> list[AnalyzeResponse]:
    return [AnalyzeResponse(score=score, label=label)
            for score, label in (calculate_sentiment_score(item.rating, item.content or "") for item in request.items)]


@app.post("/api/sentiment/keywords")
def keywords(request: KeywordsRequest) -> dict[str, list[dict[str, int | str]]]:
    return {"words": extract_keywords(request.texts, request.top_n)}
