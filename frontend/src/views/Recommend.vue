<template>
  <AppLayout title="个性化推荐">
    <div style="display:grid;grid-template-columns:1fr 300px;gap:20px">
      <div>
        <div class="card" style="margin-bottom:16px">
          <div class="card-header">🤖 推荐算法说明</div>
          <div class="card-body" style="padding:14px 18px">
            <ul class="algo-steps">
              <li>从浏览、点赞和评论反馈中提取偏好信号</li>
              <li>基于评论情感矩阵做基于物品的协同过滤（Item-CF）计算商品相似度</li>
              <li>协同过滤结果融合商品情感分、评分和品牌/价格偏好共同排序</li>
              <li>点赞与好评会提高相近商品的推荐权重，差评会降低同类商品权重</li>
              <li>新用户冷启动：优先推荐高情感分、高评分、高评论量商品</li>
            </ul>
          </div>
        </div>
        <div class="card">
          <div class="card-header">⭐ 为您推荐 <span style="font-weight:400;font-size:12px;color:#64748b">{{ rows.length }} 件</span></div>
          <div class="card-body">
            <div v-if="rows.length" class="phone-grid"><PhoneCard v-for="item in rows" :key="item.phone.id" :phone="item.phone" /></div>
            <div v-else style="text-align:center;padding:50px;color:#94a3b8">
              <div style="font-size:48px">📭</div>
              <p>暂无推荐，请先浏览一些商品</p>
              <RouterLink to="/products" class="btn btn-primary">去浏览商品</RouterLink>
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-header">🧠 推荐解释 <span style="font-weight:400;font-size:12px;color:#64748b">{{ summary }}</span></div>
          <div class="card-body">
            <div v-if="rows.length" class="recommend-explain-list">
              <div v-for="item in rows" :key="`${item.phone.productId}-explain`" class="recommend-explain-card">
                <div class="recommend-explain-top">
                  <div>
                    <div class="recommend-explain-title">{{ item.phone.title }}</div>
                    <div class="recommend-explain-meta">策略：{{ strategyLabel(item.strategy) }} · 商品编号：{{ item.phone.productId }}</div>
                  </div>
                  <div class="recommend-explain-score">总分 {{ item.finalScore.toFixed(4) }}</div>
                </div>
                <div class="recommend-explain-grid">
                  <div>协同过滤分：{{ item.cfScore.toFixed(4) }}</div>
                  <div>偏好融合分：{{ item.sentimentScore.toFixed(4) }}</div>
                  <div>冷启动分：{{ item.coldStartScore.toFixed(4) }}</div>
                  <div>命中来源：{{ item.sourceProducts.length ? item.sourceProducts.join(' / ') : '无' }}</div>
                </div>
                <div class="recommend-explain-actions">来源行为：{{ item.sourceActions.length ? item.sourceActions.join('，') : '无直接行为来源' }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div>
        <div class="card">
          <div class="card-header">🕐 浏览历史</div>
          <div class="card-body" style="padding:12px">
            <div v-if="history.length">
              <div v-for="phone in history" :key="phone.id" style="display:flex;gap:10px;align-items:center;padding:8px 0;border-bottom:1px solid #f1f5f9;cursor:pointer" @click="$router.push(`/product/${phone.productId}`)">
                <div style="width:44px;height:44px;background:#f8f9fa;border-radius:6px;flex-shrink:0;overflow:hidden;display:flex;align-items:center;justify-content:center">
                  <img v-if="phone.imageUrl" :src="phone.imageUrl" style="width:100%;height:100%;object-fit:contain" />
                  <span v-else>📱</span>
                </div>
                <div style="flex:1;min-width:0">
                  <div style="font-size:12px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ phone.title }}</div>
                  <div style="font-size:12px;color:#e53e3e;font-weight:700">¥{{ Math.round(phone.currentPrice || 0) }}</div>
                </div>
              </div>
            </div>
            <div v-else style="text-align:center;padding:20px;color:#94a3b8;font-size:13px">暂无浏览记录</div>
          </div>
        </div>
        <div class="card" style="margin-top:14px">
          <div class="card-header">🏷 推荐指标说明</div>
          <div class="card-body" style="font-size:12px;color:#64748b;line-height:1.8;padding:14px">
            <div style="margin-bottom:6px"><span class="sentiment-badge sentiment-positive">情感分≥65%</span> → 正面评价为主</div>
            <div style="margin-bottom:6px"><span class="sentiment-badge sentiment-neutral">情感分50-75%</span> → 评价较为中立</div>
            <div style="margin-bottom:6px"><span class="sentiment-badge sentiment-negative">情感分＜40%</span> → 负面评价较多</div>
            <hr style="border-color:#f1f5f9">
            <div>★ 评分由 1-5 星的用户评价综合计算</div>
            <div style="margin-top:4px">推荐结果每次浏览后动态更新</div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import PhoneCard from '../components/PhoneCard.vue'
import { api } from '../api'

const rows = ref([])
const history = ref([])
const summary = ref('')
function strategyLabel(value) {
  return ({
    item_cf: 'Item-CF 协同过滤',
    preference: '偏好补充',
    cold_start: '冷启动补位'
  })[value] || value
}
onMounted(async () => {
  const explain = (await api.get('/recommendations/explain?limit=16')).data || {}
  rows.value = explain.items || []
  summary.value = explain.summary || ''
  history.value = (await api.get('/recommendations/history?limit=8')).data || []
})
</script>
