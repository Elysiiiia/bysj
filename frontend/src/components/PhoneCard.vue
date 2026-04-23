<template>
  <div class="phone-card" @click="$router.push(`/product/${phone.productId}`)">
    <div class="card-img-wrap">
      <img v-if="phone.imageUrl && !failed" :src="phone.imageUrl" :alt="phone.title" @error="failed = true" />
      <span v-else class="img-placeholder">📱</span>
    </div>
    <div class="card-content">
      <span class="card-brand">{{ phone.brand || '未知品牌' }}</span>
      <div class="card-title">{{ phone.title || '未命名商品' }}</div>
      <div class="card-price">¥{{ Math.round(phone.currentPrice || 0) }}</div>
      <div v-if="phone.originalPrice && phone.originalPrice > (phone.currentPrice || 0)" class="card-price-ori">
        原价 ¥{{ Math.round(phone.originalPrice) }}
      </div>
      <div class="card-meta">
        <span class="card-rating">{{ stars(phone.avgRating) }} <small style="color:#94a3b8;font-size:11px">({{ phone.reviewCount || 0 }})</small></span>
        <span class="sentiment-badge" :class="sentimentClass(phone.sentimentScore)">{{ sentimentLabel(phone.sentimentScore) }}</span>
      </div>
      <div v-if="phone.sales" style="margin-top:6px;font-size:11px;color:#94a3b8">销量 {{ phone.sales }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
defineProps({ phone: { type: Object, required: true } })
const failed = ref(false)
function stars(rating = 3) {
  const n = Math.max(0, Math.min(5, Math.round(rating || 3)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}
function sentimentClass(score = 0.5) {
  if (score >= 0.75) return 'sentiment-positive'
  if (score >= 0.5) return 'sentiment-neutral'
  return 'sentiment-negative'
}
function sentimentLabel(score = 0.5) {
  if (score >= 0.75) return '好评'
  if (score >= 0.5) return '一般'
  return '差评'
}
</script>
