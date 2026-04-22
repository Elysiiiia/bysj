<template>
  <AppLayout title="首页">
    <div class="hero-banner">
      <div>
        <h2>📱 基于评论情感分析的个性化推荐</h2>
        <p>融合协同过滤与情感特征，为您精准推送最适合的手机</p>
      </div>
      <div class="hero-stats">
        <div class="hero-stat"><div class="val">{{ stats.totalPhones || 0 }}</div><div class="lbl">商品总数</div></div>
        <div class="hero-stat"><div class="val">{{ stats.totalComments || 0 }}</div><div class="lbl">用户评论</div></div>
        <div class="hero-stat"><div class="val">{{ stats.posRate || 0 }}%</div><div class="lbl">好评率</div></div>
      </div>
    </div>
    <section class="card">
      <div class="card-header">⭐ 专属推荐 <span style="font-weight:400;font-size:12px;color:#64748b;margin-left:8px">基于您的浏览偏好与情感分析算法</span></div>
      <div class="card-body">
        <div class="phone-grid" v-if="recommendations.length">
          <PhoneCard v-for="phone in recommendations" :key="phone.id" :phone="phone" />
        </div>
        <div v-else style="text-align:center;padding:40px;color:#94a3b8">暂无推荐数据</div>
        <div style="text-align:center;margin-top:16px"><RouterLink to="/recommend" class="btn btn-outline-primary">查看完整推荐列表 →</RouterLink></div>
      </div>
    </section>
    <section class="card">
      <div class="card-header">🏆 精选好评商品 <span style="font-weight:400;font-size:12px;color:#64748b;margin-left:8px">综合情感评分最高</span></div>
      <div class="card-body"><div class="phone-grid"><PhoneCard v-for="phone in featured" :key="phone.id" :phone="phone" /></div></div>
    </section>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import PhoneCard from '../components/PhoneCard.vue'
import { api } from '../api'

const stats = ref({})
const recommendations = ref([])
const featured = ref([])

onMounted(async () => {
  stats.value = (await api.get('/analysis/home')).data || {}
  recommendations.value = (await api.get('/recommendations?limit=8')).data || []
  featured.value = (await api.get('/phones/featured?limit=4')).data || []
})
</script>
