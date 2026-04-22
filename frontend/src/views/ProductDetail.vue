<template>
  <AppLayout title="商品详情">
    <div class="card" v-if="phone">
      <div class="card-body">
        <div class="detail-layout">
          <div class="detail-img-box">
            <img v-if="phone.imageUrl" :src="phone.imageUrl" :alt="phone.title" />
            <span v-else class="img-placeholder">📱</span>
          </div>
          <div>
            <span class="card-brand">{{ phone.brand }}</span>
            <h2 style="font-size:20px;line-height:1.5;margin:12px 0">{{ phone.title }}</h2>
            <div class="detail-price">¥{{ Math.round(phone.currentPrice || 0) }}</div>
            <table class="detail-spec-table">
              <tbody>
                <tr><td>店铺</td><td>{{ phone.shopName || '-' }}</td></tr>
                <tr><td>销量</td><td>{{ phone.sales || '-' }}</td></tr>
                <tr><td>评分</td><td>{{ phone.avgRating || 0 }} / 5</td></tr>
                <tr><td>评论数</td><td>{{ phone.reviewCount || 0 }}</td></tr>
                <tr><td>情感分</td><td>{{ phone.sentimentScore || 0.5 }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
    <div class="card">
      <div class="card-header">💬 用户评论</div>
      <div class="card-body">
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>用户</th><th>评分</th><th>情感</th><th>内容</th><th>日期</th></tr></thead>
            <tbody>
              <tr v-for="comment in comments" :key="comment.id">
                <td>{{ comment.nickname }}</td>
                <td>{{ comment.rating }}</td>
                <td>{{ label(comment.sentimentLabel) }}</td>
                <td><div class="text-truncate-2">{{ comment.content }}</div></td>
                <td>{{ comment.commentDate }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
    <div class="card">
      <div class="card-header">🔗 相关商品</div>
      <div class="card-body"><div class="phone-grid"><PhoneCard v-for="item in related" :key="item.id" :phone="item" /></div></div>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import PhoneCard from '../components/PhoneCard.vue'
import { api } from '../api'

const route = useRoute()
const phone = ref(null)
const related = ref([])
const comments = ref([])
function label(v) { return ({ positive: '正面', neutral: '中性', negative: '负面' })[v] || '中性' }
onMounted(async () => {
  const detail = await api.get(`/phones/${route.params.productId}`)
  phone.value = detail.data.phone
  related.value = detail.data.related || []
  comments.value = (await api.get('/comments', { params: { productId: route.params.productId, pageSize: 5 } })).data.rows || []
})
</script>
