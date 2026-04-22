<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-logo">
        <span class="logo-icon">📱</span>
        <h3>商品个性化推荐系统</h3>
        <p>基于用户评论情感分析</p>
      </div>
      <div v-if="error" class="alert alert-danger">⚠ {{ error }}</div>
      <div class="login-tabs">
        <div class="login-tab" :class="{ active: role === 'user' }" @click="switchTab('user')">👤 用户登录</div>
        <div class="login-tab" :class="{ active: role === 'admin' }" @click="switchTab('admin')">🛡 管理员登录</div>
      </div>
      <form @submit.prevent="submit">
        <div class="login-form-group">
          <label>用户名</label>
          <input v-model="form.username" placeholder="请输入用户名" autocomplete="username" required />
        </div>
        <div class="login-form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" required />
        </div>
        <button class="login-btn" type="submit">登 录</button>
      </form>
      <div class="login-hint" v-if="role === 'user'">测试账号：<code>Test</code> / <code>123456</code></div>
      <div class="login-hint" v-else>管理员账号：<code>admin</code> / <code>admin123</code></div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const role = ref('user')
const error = ref('')
const form = reactive({ username: 'Test', password: '123456' })

function switchTab(nextRole) {
  role.value = nextRole
  form.username = nextRole === 'admin' ? 'admin' : 'Test'
  form.password = nextRole === 'admin' ? 'admin123' : '123456'
}

async function submit() {
  error.value = ''
  const res = await api.post('/auth/login', { ...form, role: role.value })
  if (!res.ok) {
    error.value = res.message
    return
  }
  auth.setAuth(res.data)
  router.push(res.data.role === 'admin' ? '/admin' : '/')
}
</script>
