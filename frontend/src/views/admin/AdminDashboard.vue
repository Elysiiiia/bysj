<template>
  <AppLayout title="控制台">
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon">📱</div><div><div class="stat-label">商品总数</div><div class="stat-value">{{ stats.phones || 0 }}</div></div></div>
      <div class="stat-card"><div class="stat-icon">💬</div><div><div class="stat-label">评论总数</div><div class="stat-value">{{ stats.comments || 0 }}</div></div></div>
      <div class="stat-card"><div class="stat-icon">👥</div><div><div class="stat-label">用户总数</div><div class="stat-value">{{ stats.users || 0 }}</div></div></div>
      <div class="stat-card"><div class="stat-icon">👣</div><div><div class="stat-label">行为记录</div><div class="stat-value">{{ stats.behaviors || 0 }}</div></div></div>
    </div>
    <div class="card">
      <div class="card-header">后台管理入口</div>
      <div class="card-body" style="display:flex;gap:12px">
        <RouterLink class="btn btn-primary" to="/admin/phones">商品管理</RouterLink>
        <RouterLink class="btn btn-primary" to="/admin/comments">评论管理</RouterLink>
        <RouterLink class="btn btn-primary" to="/admin/users">用户管理</RouterLink>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppLayout from '../../components/AppLayout.vue'
import { api } from '../../api'
const stats = ref({})
onMounted(async () => { stats.value = (await api.get('/analysis/admin')).data || {} })
</script>
