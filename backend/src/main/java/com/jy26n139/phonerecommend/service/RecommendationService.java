package com.jy26n139.phonerecommend.service; // 当前类所在的包路径

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的 Lambda 查询构造器
import com.jy26n139.phonerecommend.dto.RecommendationExplainItem; // 推荐解释项 DTO
import com.jy26n139.phonerecommend.dto.RecommendationExplainResponse; // 推荐解释结果 DTO
import com.jy26n139.phonerecommend.entity.Comment; // 评论实体
import com.jy26n139.phonerecommend.entity.Phone; // 商品实体
import com.jy26n139.phonerecommend.entity.UserBehavior; // 用户行为实体
import com.jy26n139.phonerecommend.mapper.CommentMapper; // 评论表 Mapper
import com.jy26n139.phonerecommend.mapper.PhoneMapper; // 商品表 Mapper
import org.springframework.stereotype.Service; // 声明为 Spring Service 组件

import java.util.ArrayList; // 动态数组实现
import java.util.Comparator; // 比较器，用于排序
import java.util.HashMap; // 哈希映射
import java.util.HashSet; // 哈希集合
import java.util.LinkedHashMap; // 保留插入顺序的哈希映射
import java.util.LinkedHashSet; // 保留插入顺序的哈希集合
import java.util.List; // 列表接口
import java.util.Map; // 映射接口
import java.util.Objects; // 空值与对象比较工具类
import java.util.Set; // 集合接口
import java.util.function.Function; // 函数式接口，用于 toMap
import java.util.stream.Collectors; // Stream 收集工具

@Service // 让 Spring 托管这个推荐服务类
public class RecommendationService { // 推荐服务主类，负责个性化推荐、冷启动和相似商品推荐

    private final PhoneMapper phoneMapper; // 用于访问商品表
    private final CommentMapper commentMapper; // 用于访问评论表
    private final BehaviorService behaviorService; // 用于读取用户行为历史

    public RecommendationService(PhoneMapper phoneMapper, CommentMapper commentMapper, BehaviorService behaviorService) { // 构造器注入依赖
        this.phoneMapper = phoneMapper; // 保存商品 Mapper
        this.commentMapper = commentMapper; // 保存评论 Mapper
        this.behaviorService = behaviorService; // 保存行为服务
    }

    /**
     * 旧版推荐接口仍然只返回商品列表。
     * 为了兼容现有调用方，这里直接复用 explainRecommendations 的结果，并只取其中的商品部分。
     */
    public List<Phone> recommend(Long userId, int topN) { // 对外提供商品列表推荐
        return explainRecommendations(userId, topN).items().stream() // 先拿到带解释的推荐结果，再转成流
                .map(RecommendationExplainItem::phone) // 只取每一项中的商品对象
                .toList(); // 收集为列表返回
    }

    /**
     * 推荐主流程。
     * 有行为历史时优先走 Item-CF 协同过滤，再用偏好补充；没有历史时直接走冷启动。
     */
    public RecommendationExplainResponse explainRecommendations(Long userId, int topN) { // 返回“推荐结果 + 推荐解释”
        List<UserBehavior> behaviors = behaviorService.recent(userId, 30); // 读取用户最近 30 条行为
        if (behaviors.isEmpty()) { // 如果用户没有任何历史行为
            List<RecommendationExplainItem> coldItems = coldStartExplain(topN, Set.of()); // 直接生成冷启动解释结果
            return new RecommendationExplainResponse( // 返回冷启动解释响应
                    coldItems, // 冷启动商品列表
                    List.of(), // 无历史商品来源
                    "无历史行为，使用冷启动排序：情感分 50% + 评分 30% + 评论量 20%" // 给前端展示的摘要说明
            ); // 冷启动分支到此结束
        } // 有历史行为则继续走协同过滤

        List<Phone> all = phoneMapper.selectList(null); // 查询全部商品，作为候选池
        List<Comment> comments = commentMapper.selectList( // 查询全部有效评论，用于构建评论矩阵
                new LambdaQueryWrapper<Comment>() // 创建评论查询条件
                        .select(Comment::getProductId, Comment::getNickname, Comment::getSentimentScore) // 只取构建矩阵需要的字段
                        .isNotNull(Comment::getProductId) // 商品编号不能为空
                        .isNotNull(Comment::getNickname) // 评论昵称不能为空
        ); // 评论查询结束

        Map<String, Phone> byId = all.stream() // 将全部商品转成流
                .filter(phone -> phone.getProductId() != null) // 只保留有 productId 的商品
                .collect(Collectors.toMap(Phone::getProductId, Function.identity(), (a, b) -> a)); // 建成 productId -> Phone 的映射

        Set<String> excluded = behaviors.stream() // 将行为列表转成流
                .map(UserBehavior::getProductId) // 取出用户交互过的商品编号
                .filter(Objects::nonNull) // 去掉空编号
                .collect(HashSet::new, HashSet::add, HashSet::addAll); // 收集成排除集合，避免给用户重复推荐看过的商品

        Map<String, Double> sourceWeights = buildSourceWeights(behaviors); // 根据行为类型生成“源商品权重”
        Map<String, Map<String, Double>> itemUsers = buildItemUserMatrix(comments); // 构建 Item-CF 需要的商品-用户-情感分矩阵
        UserPreference preference = buildUserPreference(behaviors, byId); // 从行为中提取品牌和价格偏好
        Map<String, String> latestActions = buildLatestActions(behaviors); // 记录每个历史商品最近一次行为类型
        List<String> history = new ArrayList<>(sourceWeights.keySet()); // 将源商品编号保留给 explain 接口返回

        List<CandidateScore> cfRecs = itemCfRecommend(sourceWeights, itemUsers, byId, latestActions, excluded, topN); // 先跑协同过滤推荐
        List<CandidateScore> sentimentRecs = sentimentRecommend(preference, byId, excluded, topN); // 再跑偏好补充推荐

        List<CandidateScore> merged = mergeRecommendations(cfRecs, sentimentRecs, excluded, topN); // 按优先级合并两路推荐
        if (merged.size() >= topN) { // 如果前两路结果已经足够
            return new RecommendationExplainResponse( // 直接返回结果
                    toExplainItems(merged), // 转成前端可展示的解释结构
                    history, // 返回历史商品编号
                    "有历史行为，优先使用 Item-CF 协同过滤，再融合品牌/价格偏好补足推荐" // 返回说明文案
            ); // 主流程结束
        } // 如果前两路不够，就继续补冷启动

        List<CandidateScore> cold = coldStartCandidates(topN * 2, excluded); // 再生成一批冷启动候选用于补位
        List<CandidateScore> full = mergeRecommendations(merged, cold, excluded, topN); // 将已有推荐和冷启动候选合并
        return new RecommendationExplainResponse( // 返回带冷启动补位的最终结果
                toExplainItems(full), // 转成解释项
                history, // 返回历史商品编号
                "有历史行为，优先使用 Item-CF 协同过滤，再融合品牌/价格偏好；不足部分由冷启动补齐" // 返回摘要说明
        ); // explainRecommendations 结束
    }

    /**
     * 冷启动推荐入口。
     * 当用户没有历史行为时，只按商品质量信号排序返回。
     */
    public List<Phone> coldStart(int topN) { // 对外暴露冷启动商品列表
        return coldStartCandidates(topN, Set.of()).stream() // 先生成带分数的冷启动候选
                .map(CandidateScore::phone) // 只取商品部分
                .toList(); // 收集后返回
    }

    /**
     * 商品详情页的相似商品推荐。
     * 这里不是协同过滤，而是根据商品自身属性做近邻搜索。
     */
    public List<Phone> similar(String productId, int topN) { // 给定一个商品编号，找最像它的商品
        Phone target = phoneMapper.selectOne( // 查询目标商品
                new LambdaQueryWrapper<Phone>() // 构建商品查询条件
                        .eq(Phone::getProductId, productId) // 条件：productId 等于传入值
                        .last("limit 1") // 最多取一条
        ); // 查询结束
        if (target == null) { // 如果目标商品不存在
            return List.of(); // 直接返回空列表
        } // 否则继续查相似商品
        return phoneMapper.selectList( // 查询除当前商品外的其它全部商品
                        new LambdaQueryWrapper<Phone>() // 构建查询条件
                                .ne(Phone::getProductId, productId) // 条件：商品编号不等于当前商品
                ) // 查询结束
                .stream() // 转成流，方便排序
                .sorted(Comparator.comparingDouble((Phone p) -> similarity(target, p)).reversed()) // 按 similarity 分值从高到低排序
                .limit(topN) // 只取前 topN 个
                .toList(); // 收集为列表返回
    }

    /**
     * 商品相似度计算公式。
     * 这部分用于详情页相似商品，考虑品牌、价格、情感分和评分。
     */
    private double similarity(Phone a, Phone b) { // 计算两个商品之间的相似程度
        double brand = Objects.equals(a.getBrand(), b.getBrand()) ? 0.4 : 0.0; // 同品牌直接给 0.4 的高权重
        double ap = a.getCurrentPrice() == null ? 0 : a.getCurrentPrice(); // 取商品 a 的价格，空则按 0 处理
        double bp = b.getCurrentPrice() == null ? 0 : b.getCurrentPrice(); // 取商品 b 的价格，空则按 0 处理
        double price = 1.0 - Math.min(Math.abs(ap - bp) / Math.max(ap, 1.0), 1.0); // 价格越接近，price 越接近 1
        double sentiment = 1.0 - Math.abs( // 情感分差距越小，相似度越高
                (a.getSentimentScore() == null ? 0.5 : a.getSentimentScore()) // 商品 a 情感分，空值按 0.5 中性处理
                        - (b.getSentimentScore() == null ? 0.5 : b.getSentimentScore()) // 商品 b 情感分，空值按 0.5 中性处理
        ); // 情感相似度计算结束
        double rating = (b.getAvgRating() == null ? 3.0 : b.getAvgRating()) / 5.0; // 候选商品评分归一化到 0~1
        return brand + price * 0.25 + sentiment * 0.25 + rating * 0.10; // 最终返回综合相似度
    }

    /**
     * 构建 Item-CF 的商品-用户矩阵。
     * 每个商品会变成“哪些用户评论过它、给了多高情感分”的稀疏向量。
     */
    private Map<String, Map<String, Double>> buildItemUserMatrix(List<Comment> comments) { // 输入评论列表，输出矩阵
        Map<String, Map<String, Double>> itemUsers = new HashMap<>(); // 最终结构：商品 -> (用户 -> 情感分)
        for (Comment comment : comments) { // 逐条处理评论
            if (comment.getProductId() == null || comment.getNickname() == null) { // 如果商品编号或昵称为空
                continue; // 跳过这条无效评论
            } // 只有有效评论才参与矩阵构建
            itemUsers.computeIfAbsent(comment.getProductId(), ignored -> new HashMap<>()) // 如果该商品还没有向量，就先创建
                    .put(comment.getNickname(), comment.getSentimentScore() == null ? 0.5 : comment.getSentimentScore()); // 记录该用户给这个商品的情感分
        } // 评论遍历结束
        return itemUsers; // 返回构建好的 Item-CF 矩阵
    }

    /**
     * 根据最近行为生成源商品权重。
     * 不同交互的权重不同，例如点赞、正评、差评会影响推荐方向和强度。
     */
    private Map<String, Double> buildSourceWeights(List<UserBehavior> behaviors) { // 输入行为列表，输出商品权重
        Map<String, Double> weights = new LinkedHashMap<>(); // 使用有序映射，保留时间顺序
        for (UserBehavior behavior : behaviors) { // 逐条处理行为
            if (behavior.getProductId() == null) { // 没有商品编号的行为无效
                continue; // 直接跳过
            } // 只处理与具体商品绑定的行为
            double weight = preferenceWeight(behavior.getAction()); // 把行为类型转换成数值权重
            weights.putIfAbsent(behavior.getProductId(), weight); // recent() 已按时间倒序返回，因此保留最近一次行为对应的权重
        } // 行为遍历结束
        return weights; // 返回源商品权重表
    }

    /**
     * 记录每个历史商品最近一次行为类型。
     * explain 接口会把这个信息带给前端，用来说明“是浏览、点赞还是评论影响了推荐”。
     */
    private Map<String, String> buildLatestActions(List<UserBehavior> behaviors) { // 输入行为列表，输出最近动作映射
        Map<String, String> actions = new LinkedHashMap<>(); // 商品 -> 最近行为类型
        for (UserBehavior behavior : behaviors) { // 逐条遍历行为
            if (behavior.getProductId() == null) { // 商品编号为空则无意义
                continue; // 跳过
            } // 只处理有效行为
            actions.putIfAbsent(behavior.getProductId(), behavior.getAction() == null ? "view" : behavior.getAction()); // 记录该商品最近一次行为，空值默认 view
        } // 行为遍历结束
        return actions; // 返回最近行为映射
    }

    /**
     * 从历史行为中提取用户偏好。
     * 当前偏好主要包括最常出现的品牌，以及最近交互商品的平均价格区间。
     */
    private UserPreference buildUserPreference(List<UserBehavior> behaviors, Map<String, Phone> byId) { // 结合行为和商品表构建用户偏好
        Map<String, Integer> brandCounts = new HashMap<>(); // 统计各品牌出现次数
        List<Double> prices = new ArrayList<>(); // 收集用户历史商品价格
        for (UserBehavior behavior : behaviors) { // 遍历历史行为
            Phone phone = byId.get(behavior.getProductId()); // 取出行为对应的商品
            if (phone == null) { // 如果商品不存在
                continue; // 跳过
            } // 只统计有效商品
            if (phone.getBrand() != null && !phone.getBrand().isBlank()) { // 品牌字段存在且非空
                brandCounts.merge(phone.getBrand(), 1, Integer::sum); // 对该品牌计数加一
            } // 品牌统计结束
            if (phone.getCurrentPrice() != null) { // 如果商品价格存在
                prices.add(phone.getCurrentPrice()); // 收集到价格列表中
            } // 价格收集结束
        } // 行为遍历结束
        Set<String> preferredBrands = brandCounts.entrySet().stream() // 对品牌计数转成流
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()) // 按出现次数从高到低排序
                .limit(3) // 只取前三个偏好品牌
                .map(Map.Entry::getKey) // 只保留品牌名
                .collect(Collectors.toCollection(LinkedHashSet::new)); // 收集成有序集合
        Double avgPrice = prices.isEmpty() ? null : prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0); // 计算用户历史平均价格
        return new UserPreference(preferredBrands, avgPrice); // 返回封装好的用户偏好对象
    }

    /**
     * Item-CF 协同过滤推荐。
     * 先用评论矩阵做余弦相似度，再把历史商品对候选商品的贡献累加，最后融合商品本身质量信号。
     */
    private List<CandidateScore> itemCfRecommend( // 协同过滤推荐主函数
            Map<String, Double> sourceWeights, // 历史源商品及其行为权重
            Map<String, Map<String, Double>> itemUsers, // 商品-用户-情感分矩阵
            Map<String, Phone> byId, // 商品编号到商品对象的映射
            Map<String, String> latestActions, // 历史商品最近一次行为
            Set<String> excluded, // 需要排除的已交互商品
            int topN // 返回数量
    ) {
        Map<String, Double> scores = new HashMap<>(); // 候选商品的累计协同过滤分
        Map<String, Map<String, Double>> contributors = new HashMap<>(); // 候选商品由哪些历史商品推上来的贡献明细
        for (Map.Entry<String, Double> entry : sourceWeights.entrySet()) { // 遍历每一个历史源商品
            String productId = entry.getKey(); // 当前源商品编号
            Map<String, Double> sourceUsers = itemUsers.get(productId); // 取出该商品对应的用户向量
            if (sourceUsers == null || sourceUsers.isEmpty()) { // 如果该商品没有评论矩阵
                continue; // 无法参与协同过滤，跳过
            } // 只有有评论向量的商品才可参与 Item-CF
            double weight = entry.getValue(); // 取出该源商品的行为权重
            for (Map.Entry<String, Map<String, Double>> candidate : itemUsers.entrySet()) { // 遍历每个候选商品
                String candidateId = candidate.getKey(); // 当前候选商品编号
                if (excluded.contains(candidateId)) { // 如果该商品用户已经看过/交互过
                    continue; // 不再推荐给用户
                } // 过滤已交互商品
                double similarity = cosineSimilarity(sourceUsers, candidate.getValue()); // 计算源商品和候选商品的余弦相似度
                if (similarity <= 0) { // 相似度小于等于 0 说明没有有效关联
                    continue; // 跳过
                } // 只有正相似度才会推动推荐
                double contribution = similarity * weight; // 将相似度乘以行为权重，得到本次贡献值
                scores.merge(candidateId, contribution, Double::sum); // 累加到候选商品总分上
                contributors.computeIfAbsent(candidateId, ignored -> new HashMap<>()) // 如果候选商品还没有贡献表，就先创建
                        .merge(productId, contribution, Double::sum); // 记录是哪个历史商品贡献了多少分
            } // 候选商品遍历结束
        } // 源商品遍历结束
        return scores.entrySet().stream() // 对所有候选商品总分转成流
                .map(e -> { // 将每个候选商品转成 CandidateScore
                    Phone phone = byId.get(e.getKey()); // 取出候选商品实体
                    if (phone == null) { // 如果商品实体不存在
                        return null; // 返回空，后续过滤掉
                    } // 只有存在的商品才参与结果
                    double cfScore = e.getValue() * 0.6; // 协同过滤总贡献乘 0.6，作为 CF 部分得分
                    double sentimentScore = normalizedSentiment(phone) * 0.25 + normalizedRating(phone) * 0.15; // 商品自身情感分和评分共同补 0.4
                    List<String> sourceProducts = contributors.getOrDefault(e.getKey(), Map.of()).entrySet().stream() // 读取该候选商品的来源贡献表
                            .sorted(Map.Entry.<String, Double>comparingByValue().reversed()) // 按贡献值从高到低排序
                            .limit(3) // 只保留前三个主要来源商品
                            .map(Map.Entry::getKey) // 只保留来源商品编号
                            .toList(); // 收集成列表
                    List<String> sourceActions = sourceProducts.stream() // 对来源商品编号转成流
                            .map(pid -> pid + ":" + latestActions.getOrDefault(pid, "view")) // 拼接为 “商品编号:行为类型”
                            .toList(); // 收集为来源行为列表
                    return new CandidateScore( // 返回候选结果对象
                            phone, // 当前候选商品
                            "item_cf", // 标记策略来源是协同过滤
                            cfScore + sentimentScore, // 最终总分 = 协同过滤分 + 商品质量补充分
                            cfScore, // 单独保存协同过滤分
                            sentimentScore, // 单独保存情感/评分补充分
                            0.0, // 当前阶段没有冷启动分
                            sourceProducts, // 保存来源商品列表
                            sourceActions // 保存来源行为列表
                    ); // CandidateScore 构建结束
                }) // map 结束
                .filter(Objects::nonNull) // 去掉空结果
                .sorted(Comparator.comparingDouble(CandidateScore::finalScore).reversed()) // 按总分从高到低排序
                .limit(topN) // 只取前 topN 个
                .toList(); // 收集为列表返回
    }

    /**
     * 偏好补充推荐。
     * 它不依赖评论矩阵，而是根据品牌偏好、价格偏好和商品质量给候选商品打分。
     */
    private List<CandidateScore> sentimentRecommend(UserPreference preference, Map<String, Phone> byId, Set<String> excluded, int topN) { // 偏好补充推荐函数
        return byId.values().stream() // 对所有商品转成流
                .filter(phone -> phone.getProductId() != null && !excluded.contains(phone.getProductId())) // 过滤掉无编号和已交互商品
                .map(phone -> new CandidateScore( // 将每个商品映射为带解释的候选对象
                        phone, // 当前商品
                        "preference", // 标记该推荐来自偏好补充策略
                        sentimentPreferenceScore(preference, phone), // 最终总分直接用偏好补充分
                        0.0, // 当前策略没有协同过滤分
                        sentimentPreferenceScore(preference, phone), // 把偏好补充分记到 sentimentScore 字段中展示
                        0.0, // 当前策略没有冷启动分
                        List.of(), // 该策略没有具体历史来源商品
                        List.of() // 该策略没有具体历史来源行为
                )) // map 结束
                .sorted(Comparator.comparingDouble(CandidateScore::finalScore).reversed()) // 按总分降序排列
                .limit(topN) // 只取前 topN 个
                .toList(); // 收集为列表返回
    }

    /**
     * 合并多路推荐结果。
     * 先保留主策略结果，再用次策略补位，同时保证去重和顺序稳定。
     */
    private List<CandidateScore> mergeRecommendations(List<CandidateScore> primary, List<CandidateScore> secondary, Set<String> excluded, int topN) { // 合并两路候选列表
        LinkedHashMap<String, CandidateScore> merged = new LinkedHashMap<>(); // 使用有序映射保持加入顺序
        for (CandidateScore item : primary) { // 先遍历主策略列表
            if (item == null || item.phone() == null || item.phone().getProductId() == null || excluded.contains(item.phone().getProductId())) { // 过滤无效项和排除项
                continue; // 跳过
            } // 仅保留有效且未排除的商品
            merged.putIfAbsent(item.phone().getProductId(), item); // 如果该商品还没加入，就按当前顺序放入
            if (merged.size() >= topN) { // 如果已经达到目标数量
                return new ArrayList<>(merged.values()); // 直接返回
            } // 否则继续补充
        } // 主策略遍历结束
        for (CandidateScore item : secondary) { // 再遍历次策略列表
            if (item == null || item.phone() == null || item.phone().getProductId() == null || excluded.contains(item.phone().getProductId())) { // 同样过滤无效项
                continue; // 跳过
            } // 仅保留有效项
            merged.putIfAbsent(item.phone().getProductId(), item); // 只在主策略没有出现时才补入
            if (merged.size() >= topN) { // 达到数量后停止
                break; // 跳出循环
            } // 否则继续补充
        } // 次策略遍历结束
        return new ArrayList<>(merged.values()); // 返回合并后的结果
    }

    /**
     * 余弦相似度计算。
     * 在这里，向量维度是共同评论过两个商品的用户，向量值是这些用户给出的情感分。
     */
    private double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) { // 计算两个稀疏向量的余弦相似度
        Set<String> keys = new HashSet<>(a.keySet()); // 先复制向量 a 的全部维度
        keys.retainAll(b.keySet()); // 保留 a 和 b 的交集维度，即共同用户
        if (keys.isEmpty()) { // 如果没有共同用户
            return 0.0; // 相似度直接记为 0
        } // 只有有共同用户才可能相似
        double dot = 0.0; // 初始化点积
        for (String key : keys) { // 遍历共同用户维度
            dot += a.getOrDefault(key, 0.0) * b.getOrDefault(key, 0.0); // 累加点积
        } // 点积计算结束
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum()); // 计算向量 a 的模长
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum()); // 计算向量 b 的模长
        if (normA == 0 || normB == 0) { // 如果某个向量模长为 0
            return 0.0; // 避免除零，直接返回 0
        } // 模长合法才继续
        return dot / (normA * normB); // 按余弦公式返回相似度
    }

    /**
     * 偏好补充阶段的综合打分公式。
     * 这里把情感分、评分、评论量、品牌偏好和价格偏好融合成一个总分。
     */
    private double sentimentPreferenceScore(UserPreference preference, Phone phone) { // 计算单个商品的偏好补充分
        double sentiment = normalizedSentiment(phone) * 0.4; // 情感分占 40%
        double rating = normalizedRating(phone) * 0.2; // 评分占 20%
        double reviewCount = Math.min((phone.getReviewCount() == null ? 0 : phone.getReviewCount()) / 200.0, 1.0) * 0.1; // 评论量最多折算成 10%
        double brandBonus = preference.preferredBrands().contains(phone.getBrand()) ? 0.2 : 0.0; // 品牌命中偏好则额外加 0.2
        double priceBonus = 0.0; // 初始化价格加成
        if (preference.avgPrice() != null && preference.avgPrice() > 0 && phone.getCurrentPrice() != null) { // 如果用户平均价格和商品价格都存在
            double diff = Math.abs(phone.getCurrentPrice() - preference.avgPrice()) / preference.avgPrice(); // 计算价格差比例
            priceBonus = Math.max(0.0, 0.1 - diff * 0.1); // 差得越小加分越多，最多加 0.1
        } // 价格偏好计算结束
        return sentiment + rating + reviewCount + brandBonus + priceBonus; // 返回偏好补充总分
    }

    /**
     * 冷启动打分。
     * 这是没有用户历史时使用的商品质量排序公式，只看商品本身表现。
     */
    private double coldStartScore(Phone phone) { // 计算商品冷启动得分
        return normalizedSentiment(phone) * 0.5 // 情感分占 50%
                + normalizedRating(phone) * 0.3 // 评分占 30%
                + Math.min((phone.getReviewCount() == null ? 0 : phone.getReviewCount()) / 200.0, 1.0) * 0.2; // 评论量占 20%
    }

    /**
     * 生成冷启动候选列表。
     * 这里会把每个商品包装成 CandidateScore，方便和其他策略统一合并。
     */
    private List<CandidateScore> coldStartCandidates(int topN, Set<String> excluded) { // 构造冷启动候选集合
        return phoneMapper.selectList(null).stream() // 查询全部商品并转成流
                .filter(phone -> phone.getProductId() != null && !excluded.contains(phone.getProductId())) // 过滤无编号和排除商品
                .map(phone -> new CandidateScore( // 每个商品映射成 CandidateScore
                        phone, // 当前商品
                        "cold_start", // 标记来源策略为冷启动
                        coldStartScore(phone), // 最终总分就是冷启动分
                        0.0, // 没有协同过滤分
                        0.0, // 没有偏好补充分
                        coldStartScore(phone), // 把冷启动分记录到专门字段
                        List.of(), // 冷启动没有来源商品
                        List.of() // 冷启动没有来源行为
                )) // map 结束
                .sorted(Comparator.comparingDouble(CandidateScore::finalScore).reversed()) // 按冷启动分从高到低排序
                .limit(topN) // 只取前 topN 个
                .toList(); // 收集为列表返回
    }

    /**
     * 冷启动 explain 入口。
     * 作用是把冷启动候选直接转换成前端解释结构，保证前后端接口统一。
     */
    private List<RecommendationExplainItem> coldStartExplain(int topN, Set<String> excluded) { // 返回冷启动解释项列表
        return toExplainItems(coldStartCandidates(topN, excluded)); // 直接复用转换函数
    }

    /**
     * 内部候选对象转前端解释 DTO。
     * 这里顺便做统一的小数保留，便于页面展示和答辩讲解。
     */
    private List<RecommendationExplainItem> toExplainItems(List<CandidateScore> items) { // 把 CandidateScore 列表转成 explain DTO 列表
        return items.stream() // 对候选列表转成流
                .map(item -> new RecommendationExplainItem( // 将每个候选对象转成 DTO
                        item.phone(), // 商品实体
                        item.strategy(), // 推荐策略
                        round4(item.finalScore()), // 总分保留四位小数
                        round4(item.cfScore()), // 协同过滤分保留四位小数
                        round4(item.sentimentScore()), // 偏好补充分保留四位小数
                        round4(item.coldStartScore()), // 冷启动分保留四位小数
                        item.sourceProducts(), // 来源商品列表
                        item.sourceActions() // 来源行为列表
                )) // DTO 映射结束
                .toList(); // 收集为列表返回
    }

    /**
     * 统一保留四位小数。
     * explain 接口展示分值时全部走这个工具，避免前端出现过长小数。
     */
    private double round4(double value) { // 将 double 保留四位小数
        return Math.round(value * 10000.0) / 10000.0; // 先放大四位并四舍五入，再缩回去
    }

    /**
     * 读取商品情感分，并做空值兜底。
     * 数据为空时统一按 0.5 中性值处理。
     */
    private double normalizedSentiment(Phone phone) { // 返回商品情感分的规范值
        return phone == null || phone.getSentimentScore() == null ? 0.5 : phone.getSentimentScore(); // 空值返回中性默认值
    }

    /**
     * 读取商品平均评分，并归一化到 0~1 区间。
     * 这样评分就可以和情感分、评论量一起参与同一套加权计算。
     */
    private double normalizedRating(Phone phone) { // 返回商品评分的规范值
        return phone == null || phone.getAvgRating() == null ? 0.0 : phone.getAvgRating() / 5.0; // 评分为空则按 0 处理，否则除以 5 做归一化
    }

    /**
     * 将行为类型映射成数值权重。
     * 正向行为增强推荐，负向评论会削弱相似商品的协同过滤分。
     */
    private double preferenceWeight(String action) { // 将行为类型转换成推荐权重
        if (action == null || action.isBlank()) { // 如果行为类型为空
            return 1.0; // 按普通浏览处理
        } // 空值分支结束
        return switch (action) { // 根据行为类型返回不同权重
            case "like" -> 1.6; // 点赞给最高正向权重
            case "comment_positive" -> 1.3; // 正面评论给较高正向权重
            case "comment_neutral" -> 0.9; // 中性评论给轻微正向权重
            case "comment_negative" -> -1.2; // 负面评论给负权重，压低同类推荐
            default -> 1.0; // 其它行为按普通浏览处理
        }; // switch 结束
    }

    private record UserPreference( // 用户偏好记录对象
            Set<String> preferredBrands, // 用户偏好的品牌集合
            Double avgPrice // 用户最近交互商品的平均价格
    ) {
    } // UserPreference 结束

    private record CandidateScore( // 推荐候选对象，内部用于统一存储打分与解释信息
            Phone phone, // 商品对象
            String strategy, // 推荐策略来源
            double finalScore, // 最终排序分
            double cfScore, // 协同过滤分
            double sentimentScore, // 偏好/情感补充分
            double coldStartScore, // 冷启动分
            List<String> sourceProducts, // 来源商品编号列表
            List<String> sourceActions // 来源行为列表
    ) {
    } // CandidateScore 结束
} // RecommendationService 类结束
