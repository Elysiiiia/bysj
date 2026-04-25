<template>
  <AppLayout title="新增评论" breadcrumb="后台管理 / 评论管理 / 新增">
    <div class="admin-form-card card">
      <div class="card-header admin-form-header">➕ 新增评论</div>
      <div class="card-body">
        <form @submit.prevent="save">
          <div class="admin-form-grid admin-form-grid-2">
            <div class="form-group">
              <label class="admin-form-label">品牌</label>
              <input v-model="form.brand" type="text" class="form-control admin-form-control" />
            </div>
            <div class="form-group">
              <label class="admin-form-label">商品编号</label>
              <input v-model="form.productId" type="text" class="form-control admin-form-control" />
            </div>
          </div>

          <div class="form-group">
            <label class="admin-form-label">商品标题</label>
            <input v-model="form.title" type="text" class="form-control admin-form-control" />
          </div>

          <div class="admin-form-grid admin-form-grid-2">
            <div class="form-group">
              <label class="admin-form-label">用户昵称 <span class="admin-required">*</span></label>
              <input v-model="form.nickname" type="text" class="form-control admin-form-control" required />
            </div>
            <div class="form-group">
              <label class="admin-form-label">评分 (1-5) <span class="admin-required">*</span></label>
              <select v-model.number="form.rating" class="form-control admin-form-control" required>
                <option v-for="i in 5" :key="i" :value="i">{{ i }} 星 {{ '★'.repeat(i) }}</option>
              </select>
            </div>
          </div>

          <div class="admin-form-grid admin-form-grid-2">
            <div class="form-group">
              <label class="admin-form-label">商品规格</label>
              <input v-model="form.spec" type="text" class="form-control admin-form-control" />
            </div>
            <div class="form-group">
              <label class="admin-form-label">评论日期</label>
              <input
                v-model="form.commentDate"
                type="text"
                class="form-control admin-form-control"
                placeholder="2024-01-01 12:00:00"
              />
            </div>
          </div>

          <div class="form-group">
            <label class="admin-form-label">评论内容 <span class="admin-required">*</span></label>
            <textarea
              v-model="form.content"
              class="form-control admin-form-control admin-form-textarea"
              rows="6"
              required
            />
            <div class="admin-form-hint">保存时将自动重新计算情感分析分值</div>
          </div>

          <div class="admin-form-actions">
            <button type="submit" class="btn btn-primary" :disabled="saving">
              {{ saving ? '保存中...' : '💾 保存' }}
            </button>
            <button type="button" class="btn btn-secondary" @click="router.push('/admin/comments')">取消</button>
          </div>
        </form>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppLayout from '../../components/AppLayout.vue'
import { api } from '../../api'

const router = useRouter()
const saving = ref(false)
const form = reactive({
  brand: '',
  productId: '',
  title: '',
  nickname: '',
  rating: 1,
  spec: '',
  commentDate: '',
  content: ''
})

async function save() {
  saving.value = true
  try {
    await api.post('/comments', form)
    ElMessage.success('评论保存成功')
    router.push('/admin/comments')
  } finally {
    saving.value = false
  }
}
</script>
