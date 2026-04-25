<template>
  <AppLayout title="商品详情">
    <div v-if="phone" class="detail-layout" style="margin-bottom:20px">
      <div>
        <div class="detail-img-box">
            <img v-if="phone.imageUrl" :src="phone.imageUrl" :alt="phone.title" />
            <span v-else style="font-size:80px">📱</span>
        </div>
        <div class="card" style="margin-top:14px">
          <div class="card-header">📊 情感分析</div>
          <div class="card-body">
            <div class="sentiment-meter">
              <div class="label">情感分 {{ sentPct }}%</div>
              <div class="meter-track"><div class="meter-fill" :class="meterClass" :style="{ width: `${sentPct}%` }"></div></div>
            </div>
            <div style="margin-top:12px;font-size:12px;color:#64748b">
              <div class="rating-bar-wrap">
                <span class="label" style="color:#16a34a">正面</span>
                <div class="rating-bar"><div class="rating-bar-fill" :style="{ width: distWidth('positive'), background:'#16a34a' }"></div></div>
                <span class="count">{{ sentMap.positive || 0 }}</span>
              </div>
              <div class="rating-bar-wrap">
                <span class="label" style="color:#d97706">中性</span>
                <div class="rating-bar"><div class="rating-bar-fill" :style="{ width: distWidth('neutral'), background:'#d97706' }"></div></div>
                <span class="count">{{ sentMap.neutral || 0 }}</span>
              </div>
              <div class="rating-bar-wrap">
                <span class="label" style="color:#dc2626">负面</span>
                <div class="rating-bar"><div class="rating-bar-fill" :style="{ width: distWidth('negative'), background:'#dc2626' }"></div></div>
                <span class="count">{{ sentMap.negative || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div>
        <div class="card">
          <div class="card-body">
            <span class="badge badge-primary" style="margin-bottom:10px">{{ phone.brand }}</span>
            <h3 style="font-size:18px;font-weight:700;margin:0 0 12px;line-height:1.5">{{ phone.title }}</h3>
            <div class="detail-price">¥{{ Math.round(phone.currentPrice || 0) }}</div>
            <div v-if="phone.originalPrice" class="detail-original">
              原价 ¥{{ Math.round(phone.originalPrice) }}
              <span v-if="phone.discountPrice">，立减 ¥{{ Math.round(phone.discountPrice) }}</span>
            </div>
            <div class="detail-tags" style="margin-top:14px">
              <span v-if="phone.govSubsidy === '是'" class="badge badge-success">政府补贴</span>
              <span v-if="phone.selfOperated === '是'" class="badge badge-primary">自营</span>
              <span class="badge badge-secondary">销量 {{ phone.sales }}</span>
              <span class="badge badge-secondary">{{ phone.shopName }}</span>
            </div>
            <table class="detail-spec-table" style="margin-top:16px">
              <tbody>
                <tr><td>平均评分</td><td><span style="color:#f59e0b">{{ stars(phone.avgRating) }}</span> {{ Number(phone.avgRating || 0).toFixed(1) }} 分</td></tr>
                <tr><td>评论数量</td><td>{{ totalComments }} 条评论</td></tr>
                <tr><td>情感倾向</td><td><span class="sentiment-badge" :class="sentimentClass(phone.sentimentScore)">{{ detailSentimentText }}</span></td></tr>
                <tr><td>商品编号</td><td style="color:#94a3b8;font-size:12px">{{ phone.productId }}</td></tr>
              </tbody>
            </table>
            <div style="margin-top:18px;display:flex;gap:10px">
              <button class="btn btn-primary" @click="like">👍 标记喜欢</button>
              <a v-if="phone.linkUrl" :href="phone.linkUrl" target="_blank" class="btn btn-secondary">🔗 查看原链接</a>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="card" style="margin-bottom:20px">
      <div class="card-header">✍ 发表评价</div>
      <div class="card-body">
        <form class="comment-form" @submit.prevent="submitComment">
          <div class="comment-form-grid">
            <div class="form-group" style="margin-bottom:0">
              <label class="comment-form-label">用户名</label>
              <input :value="auth.user?.username || ''" type="text" class="form-control" disabled />
            </div>
            <div class="form-group" style="margin-bottom:0">
              <label class="comment-form-label">评分</label>
              <select v-model.number="commentForm.rating" class="form-control" required>
                <option v-for="i in 5" :key="i" :value="i">{{ i }} 星 {{ stars(i) }}</option>
              </select>
            </div>
          </div>
          <div class="comment-form-grid">
            <div class="form-group" style="margin-bottom:0">
              <label class="comment-form-label">规格</label>
              <input v-model.trim="commentForm.spec" type="text" class="form-control" placeholder="如 12GB+256GB" />
            </div>
            <div class="form-group" style="margin-bottom:0">
              <label class="comment-form-label">评论日期</label>
              <input :value="commentDatePreview" type="text" class="form-control" disabled />
            </div>
          </div>
          <div class="form-group" style="margin-bottom:0">
            <label class="comment-form-label">评论内容</label>
            <textarea
              v-model.trim="commentForm.content"
              class="form-control comment-form-textarea"
              rows="4"
              maxlength="300"
              placeholder="说说你的真实使用感受"
              required
            />
            <div class="comment-form-hint">提交后会更新该商品评论区和后续个性化推荐权重。</div>
          </div>
          <div class="comment-form-actions">
            <button type="submit" class="btn btn-primary" :disabled="submittingComment">
              {{ submittingComment ? '提交中...' : '提交评论' }}
            </button>
          </div>
        </form>
      </div>
    </div>
    <div class="card" style="margin-bottom:20px">
      <div class="card-header">💬 用户评论 <span style="font-size:12px;font-weight:400;color:#64748b">({{ totalComments }} 条)</span></div>
      <div class="card-body">
        <div v-for="comment in comments" :key="comment.id" class="comment-card">
          <div class="comment-header">
            <span class="comment-user">{{ comment.nickname }}</span>
            <span class="star-rating">{{ stars(comment.rating) }}</span>
            <span class="sentiment-badge" :class="labelClass(comment.sentimentLabel)">{{ label(comment.sentimentLabel) }}</span>
            <span class="comment-date" style="margin-left:auto">{{ (comment.commentDate || '').slice(0, 10) }}</span>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <div v-if="comment.spec" class="comment-spec">规格：{{ comment.spec }}</div>
        </div>
        <div v-if="!comments.length" style="text-align:center;padding:30px;color:#94a3b8">暂无评论</div>
      </div>
    </div>
    <div class="card">
      <div class="card-header">🔍 相似商品推荐</div>
      <div class="card-body"><div class="phone-grid"><PhoneCard v-for="item in related" :key="item.id" :phone="item" /></div></div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import PhoneCard from '../components/PhoneCard.vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const phone = ref(null)
const related = ref([])
const comments = ref([])
const sentMap = ref({})
const totalComments = ref(0)
const submittingComment = ref(false)
const commentForm = reactive({
  rating: 5,
  spec: '',
  content: ''
})
function label(v) { return ({ positive: '正面', neutral: '中性', negative: '负面' })[v] || '中性' }
function labelClass(v) { return ({ positive: 'sentiment-positive', neutral: 'sentiment-neutral', negative: 'sentiment-negative' })[v] || 'sentiment-neutral' }
function stars(rating = 3) {
  const n = Math.max(0, Math.min(5, Math.round(rating || 3)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}
function sentimentClass(score = 0.5) {
  if (score >= 0.75) return 'sentiment-positive'
  if (score >= 0.5) return 'sentiment-neutral'
  return 'sentiment-negative'
}
const sentPct = computed(() => Math.round((phone.value?.sentimentScore || 0.5) * 100))
const meterClass = computed(() => sentPct.value >= 65 ? 'meter-positive' : sentPct.value >= 40 ? 'meter-neutral' : 'meter-negative')
const detailSentimentText = computed(() => sentPct.value >= 75 ? '好评如潮' : sentPct.value >= 50 ? '评价一般' : '差评较多')
const commentDatePreview = computed(() => formatNow())
function distWidth(key) {
  const total = Object.values(sentMap.value).reduce((sum, item) => sum + Number(item || 0), 0)
  return total ? `${Math.round((sentMap.value[key] || 0) / total * 100)}%` : '0%'
}
function formatNow() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}
async function loadDetail() {
  const detail = await api.get(`/phones/${route.params.productId}`)
  phone.value = detail.data.phone
  related.value = detail.data.related || []
  sentMap.value = detail.data.sentMap || {}
  totalComments.value = detail.data.totalComments || 0
}
async function loadComments() {
  comments.value = (await api.get('/comments', { params: { productId: route.params.productId, pageSize: 5 } })).data.rows || []
}
async function like() {
  await api.post('/phones/track', { productId: route.params.productId, action: 'like' })
  alert('已标记喜欢，推荐算法将根据您的偏好更新！')
}
async function submitComment() {
  if (!commentForm.content) {
    return
  }
  submittingComment.value = true
  try {
    await api.post('/comments', {
      productId: route.params.productId,
      brand: phone.value?.brand || '',
      title: phone.value?.title || '',
      nickname: auth.user?.username || '匿名用户',
      rating: commentForm.rating,
      spec: commentForm.spec,
      commentDate: formatNow(),
      content: commentForm.content
    })
    commentForm.rating = 5
    commentForm.spec = ''
    commentForm.content = ''
    await Promise.all([loadDetail(), loadComments()])
    alert('评论已提交，推荐偏好已更新。')
  } finally {
    submittingComment.value = false
  }
}
onMounted(async () => {
  await Promise.all([loadDetail(), loadComments()])
})
</script>
