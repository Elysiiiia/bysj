<template>
  <AppLayout :title="title">
    <div class="card">
      <div class="card-body">
        <div class="table-toolbar">
          <div class="search-bar" style="margin-bottom:0">
            <input v-model="q" class="search-input" :placeholder="placeholder" @keyup.enter="load" />
            <button class="btn btn-primary" @click="load">🔍 搜索</button>
            <button class="btn btn-secondary" @click="reset">重置</button>
          </div>
          <div style="display:flex;align-items:center;gap:12px">
            <span class="table-total">共 {{ total }} {{ type === 'users' ? '位用户' : '条记录' }}</span>
            <button class="btn btn-success" @click="openForm()">+ 新增{{ typeName }}</button>
          </div>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th v-for="col in columns" :key="col.key">{{ col.label }}</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td v-for="col in columns" :key="col.key">
                  <span v-if="col.kind === 'brand'" class="badge badge-primary">{{ row[col.key] }}</span>
                  <span v-else-if="col.kind === 'price'" style="color:#e53e3e;font-weight:700">¥{{ Math.round(row[col.key] || 0) }}</span>
                  <span v-else-if="col.kind === 'money-muted'" style="color:#94a3b8;font-size:12px">{{ row[col.key] ? `¥${Math.round(row[col.key])}` : '-' }}</span>
                  <span v-else-if="col.kind === 'yesno'" class="badge" :class="row[col.key] === '是' ? 'badge-success' : 'badge-secondary'">{{ row[col.key] === '是' ? '是' : '否' }}</span>
                  <span v-else-if="col.kind === 'rating'" style="color:#f59e0b;font-size:12px">{{ Number(row[col.key] || 0).toFixed(1) }}</span>
                  <span v-else-if="col.kind === 'stars'" style="color:#f59e0b">{{ stars(row[col.key]) }}</span>
                  <span v-else-if="col.kind === 'sentiment'" class="sentiment-badge" :class="sentimentClass(row.sentimentScore)">{{ Math.round((row.sentimentScore || 0.5) * 100) }}%</span>
                  <span v-else-if="col.kind === 'sentimentLabel'" class="badge" :class="badgeClass(row.sentimentLabel)">{{ sentimentLabel(row.sentimentLabel) }}</span>
                  <span v-else-if="col.kind === 'role'" class="badge" :class="row.role === 'admin' ? 'badge-danger' : 'badge-primary'">{{ row.role === 'admin' ? '管理员' : '普通用户' }}</span>
                  <span v-else-if="col.kind === 'password'" style="color:#94a3b8;font-size:12px">{{ '*'.repeat(Math.min(String(row.password || '').length || 8, 8)) }}</span>
                  <div v-else :class="col.truncate === 1 ? 'text-truncate-1' : (col.truncate === 2 ? 'text-truncate-2' : '')">{{ row[col.key] }}</div>
                </td>
                <td>
                  <div class="action-btns">
                    <button class="btn-table btn-sm-edit" @click="openForm(row)">编辑</button>
                    <button class="btn-table btn-sm-delete" @click="remove(row)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination-wrap">
          <button class="btn btn-secondary" :disabled="page <= 1" @click="page--; load()">上一页</button>
          <button class="btn btn-secondary" :disabled="page * pageSize >= total" @click="page++; load()">下一页</button>
          <span class="pagination-info">共 {{ total }} 条</span>
        </div>
      </div>
    </div>
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑' : '新增'" width="720px">
      <el-form label-width="100px">
        <el-form-item v-for="field in formFields" :key="field.key" :label="field.label">
          <el-input v-if="field.type === 'textarea'" v-model="form[field.key]" type="textarea" :rows="4" />
          <el-select v-else-if="field.type === 'select'" v-model="form[field.key]" style="width:100%">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
          <el-input v-else v-model="form[field.key]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn btn-secondary" @click="dialogVisible=false">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '../../components/AppLayout.vue'
import { api } from '../../api'

const route = useRoute()
const page = ref(1), pageSize = ref(10), total = ref(0), q = ref('')
const rows = ref([])
const dialogVisible = ref(false)
const form = reactive({})
const type = computed(() => route.params.type || 'phones')
const title = computed(() => ({ phones: '商品管理', comments: '评论管理', users: '用户管理' })[type.value])
const typeName = computed(() => ({ phones: '商品', comments: '评论', users: '用户' })[type.value])
const placeholder = computed(() => ({ phones: '搜索品牌、商品名...', comments: '搜索昵称、品牌、评论内容...', users: '搜索用户名...' })[type.value])
const endpoint = computed(() => ({ phones: '/phones', comments: '/comments', users: '/users' })[type.value])
const columns = computed(() => ({
  phones: [
    { key:'brand', label:'品牌', kind:'brand' }, { key:'title', label:'商品标题', truncate:2 },
    { key:'currentPrice', label:'当前价格', kind:'price' }, { key:'originalPrice', label:'原价', kind:'money-muted' },
    { key:'sales', label:'销售量' }, { key:'shopName', label:'店铺名称', truncate:1 },
    { key:'govSubsidy', label:'政府补贴', kind:'yesno' }, { key:'selfOperated', label:'自营', kind:'yesno' },
    { key:'avgRating', label:'平均评分', kind:'rating' }, { key:'sentimentScore', label:'情感分', kind:'sentiment' }
  ],
  comments: [
    { key:'brand', label:'品牌', kind:'brand' }, { key:'nickname', label:'用户昵称' },
    { key:'rating', label:'评分', kind:'stars' }, { key:'spec', label:'商品规格' },
    { key:'content', label:'评论内容', truncate:2 }, { key:'sentimentLabel', label:'情感标签', kind:'sentimentLabel' },
    { key:'sentimentScore', label:'情感分值', kind:'sentiment' }, { key:'commentDate', label:'评论日期' }
  ],
  users: [
    { key:'username', label:'用户名' }, { key:'role', label:'角色', kind:'role' },
    { key:'password', label:'密码', kind:'password' }, { key:'createdAt', label:'注册时间' }
  ]
})[type.value])
const formFields = computed(() => ({
  phones: [
    { key:'brand', label:'品牌' }, { key:'title', label:'标题' }, { key:'currentPrice', label:'当前价格' },
    { key:'originalPrice', label:'原价' }, { key:'sales', label:'销量' }, { key:'shopName', label:'店铺' },
    { key:'imageUrl', label:'图片地址' }, { key:'productId', label:'商品ID' }, { key:'linkUrl', label:'链接地址' }
  ],
  comments: [
    { key:'productId', label:'商品ID' }, { key:'brand', label:'品牌' }, { key:'title', label:'标题' },
    { key:'nickname', label:'用户昵称' }, { key:'rating', label:'评分' }, { key:'spec', label:'规格' },
    { key:'commentDate', label:'评论日期' }, { key:'content', label:'评论内容', type:'textarea' }
  ],
  users: [{ key:'username', label:'用户名' }, { key:'password', label:'密码' }, { key:'role', label:'角色', type:'select' }]
})[type.value])

async function load() {
  const res = await api.get(endpoint.value, { params: { page: page.value, pageSize: pageSize.value, q: q.value } })
  rows.value = res.data.rows
  total.value = res.data.total
}
function reset() {
  q.value = ''
  page.value = 1
  load()
}
function openForm(row = {}) {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, row)
  if (type.value === 'users' && !form.role) form.role = 'user'
  dialogVisible.value = true
}
async function save() {
  await api.post(endpoint.value, form)
  dialogVisible.value = false
  ElMessage.success('保存成功')
  await load()
}
async function remove(row) {
  await ElMessageBox.confirm('确认删除该记录？', '提示')
  await api.delete(`${endpoint.value}/${row.id}`)
  ElMessage.success('删除成功')
  await load()
}
function stars(rating = 3) {
  const n = Math.max(0, Math.min(5, Math.round(rating || 3)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}
function sentimentClass(score = 0.5) {
  if (score >= 0.75) return 'sentiment-positive'
  if (score >= 0.5) return 'sentiment-neutral'
  return 'sentiment-negative'
}
function sentimentLabel(v) { return ({ positive: '正面', neutral: '中性', negative: '负面' })[v] || '中性' }
function badgeClass(v) { return ({ positive: 'badge-success', neutral: 'badge-warning', negative: 'badge-danger' })[v] || 'badge-warning' }
watch(type, () => { page.value = 1; load() })
onMounted(load)
</script>
