<template>
  <AppLayout title="控制台">
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon">📱</div><div><div class="stat-label">商品总数</div><div class="stat-value">{{ stats.phones || 0 }}</div></div></div>
      <div class="stat-card"><div class="stat-icon">💬</div><div><div class="stat-label">评论总数</div><div class="stat-value">{{ stats.comments || 0 }}</div></div></div>
      <div class="stat-card"><div class="stat-icon">👥</div><div><div class="stat-label">用户总数</div><div class="stat-value">{{ stats.users || 0 }}</div></div></div>
      <div class="stat-card"><div class="stat-icon">🔄</div><div><div class="stat-label">行为记录</div><div class="stat-value">{{ stats.behaviors || 0 }}</div></div></div>
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:20px">
      <div class="card">
        <div class="card-header">📊 各品牌商品数量</div>
        <div class="card-body" style="padding:14px">
          <div v-for="brand in brands" :key="brand.brand" style="display:flex;align-items:center;gap:10px;margin-bottom:10px">
            <span style="font-size:12px;width:70px;flex-shrink:0">{{ brand.brand }}</span>
            <div style="flex:1;height:8px;background:#e2e8f0;border-radius:4px;overflow:hidden">
              <div :style="{ height:'100%', width: brandWidth(brand), background:'#2563eb', borderRadius:'4px' }"></div>
            </div>
            <span style="font-size:12px;color:#64748b;width:30px;text-align:right">{{ brand.cnt }}</span>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="card-header">💬 最近评论</div>
        <div class="card-body" style="padding:0">
          <table class="data-table">
            <thead><tr><th>用户昵称</th><th>品牌</th><th>评分</th><th>情感</th><th>日期</th></tr></thead>
            <tbody>
              <tr v-for="comment in recentComments" :key="comment.id">
                <td>{{ comment.nickname }}</td>
                <td>{{ comment.brand }}</td>
                <td><span style="color:#f59e0b">{{ stars(comment.rating) }}</span></td>
                <td><span class="badge" :class="badgeClass(comment.sentimentLabel)">{{ label(comment.sentimentLabel) }}</span></td>
                <td style="font-size:11px;color:#94a3b8">{{ (comment.commentDate || '').slice(0, 10) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
    <div class="card" style="margin-top:20px">
      <div class="card-header">⚡ 快捷操作</div>
      <div class="card-body" style="display:flex;gap:12px;flex-wrap:wrap;padding:16px">
        <RouterLink to="/admin/phones" class="btn btn-primary">+ 添加商品</RouterLink>
        <RouterLink to="/admin/comments" class="btn btn-success">+ 添加评论</RouterLink>
        <RouterLink to="/admin/users" class="btn btn-secondary">+ 添加用户</RouterLink>
        <RouterLink to="/admin/phones" class="btn btn-outline-primary">管理商品</RouterLink>
        <RouterLink to="/admin/comments" class="btn btn-outline-primary">管理评论</RouterLink>
        <RouterLink to="/admin/users" class="btn btn-outline-primary">管理用户</RouterLink>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../../components/AppLayout.vue'
import { api } from '../../api'
const stats = ref({})
const brands = computed(() => stats.value.brands || [])
const recentComments = computed(() => stats.value.recentComments || [])
function brandWidth(brand) {
  const max = brands.value[0]?.cnt || 1
  return `${Math.min(brand.cnt / max * 100, 100)}%`
}
function stars(rating = 3) {
  const n = Math.max(0, Math.min(5, Math.round(rating || 3)))
  return '★'.repeat(n)
}
function label(v) { return ({ positive: '正面', neutral: '中性', negative: '负面' })[v] || '中性' }
function badgeClass(v) { return ({ positive: 'badge-success', neutral: 'badge-warning', negative: 'badge-danger' })[v] || 'badge-warning' }
onMounted(async () => { stats.value = (await api.get('/analysis/admin')).data || {} })
</script>
