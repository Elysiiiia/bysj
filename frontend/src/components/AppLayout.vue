<template>
  <div class="layout-wrapper">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-icon">{{ admin ? '🛡' : '📱' }}</span>
        <h4>{{ admin ? '后台管理系统' : '商品个性化推荐系统' }}</h4>
        <small>{{ admin ? '商品推荐系统管理端' : '情感分析驱动推荐' }}</small>
      </div>

      <nav class="sidebar-nav">
        <ul v-if="!admin">
          <li :class="{ active: route.path === '/' }">
            <RouterLink to="/"><span class="nav-icon">🏠</span> 首页</RouterLink>
          </li>
          <li :class="{ active: route.path === '/products' }">
            <RouterLink to="/products"><span class="nav-icon">📦</span> 商品列表</RouterLink>
          </li>
          <li :class="{ active: route.path === '/recommend' }">
            <RouterLink to="/recommend"><span class="nav-icon">⭐</span> 个性化推荐</RouterLink>
          </li>
          <li><div class="nav-section">数据分析</div></li>
          <li :class="{ active: route.path === '/analysis/dashboard' }">
            <RouterLink to="/analysis/dashboard"><span class="nav-icon">📊</span> 数据分析大屏</RouterLink>
          </li>
          <li><div class="nav-section">关联分析</div></li>
          <li :class="{ active: route.path === '/analysis/association1' }">
            <RouterLink to="/analysis/association1"><span class="nav-icon">🔗</span> 品牌情感关联</RouterLink>
          </li>
          <li :class="{ active: route.path === '/analysis/association2' }">
            <RouterLink to="/analysis/association2"><span class="nav-icon">🧩</span> 规格满意度关联</RouterLink>
          </li>
          <li :class="{ active: route.path === '/analysis/association3' }">
            <RouterLink to="/analysis/association3"><span class="nav-icon">🗂</span> 协同评论关联</RouterLink>
          </li>
        </ul>

        <ul v-else>
          <li :class="{ active: route.path === '/admin' }">
            <RouterLink to="/admin"><span class="nav-icon">📊</span> 控制台</RouterLink>
          </li>
          <li><div class="nav-section">数据管理</div></li>
          <li :class="{ active: route.path === '/admin/phones' }">
            <RouterLink to="/admin/phones"><span class="nav-icon">📱</span> 商品管理</RouterLink>
          </li>
          <li :class="{ active: route.path.startsWith('/admin/comments') }">
            <RouterLink to="/admin/comments"><span class="nav-icon">💬</span> 评论管理</RouterLink>
          </li>
          <li :class="{ active: route.path === '/admin/users' }">
            <RouterLink to="/admin/users"><span class="nav-icon">👥</span> 用户管理</RouterLink>
          </li>
          <li><div class="nav-section">快捷入口</div></li>
          <li>
            <RouterLink to="/"><span class="nav-icon">🌐</span> 前台首页</RouterLink>
          </li>
        </ul>
      </nav>

      <div class="sidebar-user">
        <div class="user-name">{{ admin ? '🛡' : '👤' }} {{ auth.user?.username }}</div>
        <div class="user-meta">{{ admin ? '管理员' : '普通用户' }}</div>
        <button class="btn-logout" @click="logout">退出登录</button>
      </div>
    </aside>

    <main class="main-content">
      <div class="content-header">
        <h2>{{ title }}</h2>
        <div class="breadcrumb-mini">
          <slot name="breadcrumb">
            <span>{{ breadcrumb || title }}</span>
          </slot>
        </div>
      </div>
      <div class="content-body">
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

defineProps({
  title: { type: String, default: '' },
  breadcrumb: { type: String, default: '' }
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const admin = computed(() => route.meta.layout === 'admin')

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
