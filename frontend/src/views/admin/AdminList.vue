<template>
  <AppLayout :title="title">
    <div class="card">
      <div class="card-body">
        <div class="table-toolbar">
          <div class="search-bar" style="margin-bottom:0">
            <input v-model="q" class="search-input" placeholder="关键词搜索" @keyup.enter="load" />
            <button class="btn btn-primary" @click="load">搜索</button>
          </div>
          <button class="btn btn-primary" @click="openForm()">新增</button>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th v-for="col in columns" :key="col.key">{{ col.label }}</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td v-for="col in columns" :key="col.key"><div class="text-truncate-2">{{ row[col.key] }}</div></td>
                <td>
                  <button class="btn btn-secondary" style="padding:4px 10px" @click="openForm(row)">编辑</button>
                  <button class="btn" style="padding:4px 10px;background:#fee2e2;color:#dc2626;margin-left:6px" @click="remove(row)">删除</button>
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
const endpoint = computed(() => ({ phones: '/phones', comments: '/comments', users: '/users' })[type.value])
const columns = computed(() => ({
  phones: [{ key:'id', label:'ID' }, { key:'brand', label:'品牌' }, { key:'title', label:'标题' }, { key:'currentPrice', label:'价格' }, { key:'sales', label:'销量' }],
  comments: [{ key:'id', label:'ID' }, { key:'nickname', label:'用户' }, { key:'brand', label:'品牌' }, { key:'rating', label:'评分' }, { key:'content', label:'评论内容' }],
  users: [{ key:'id', label:'ID' }, { key:'username', label:'用户名' }, { key:'role', label:'角色' }, { key:'createdAt', label:'创建时间' }]
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
watch(type, () => { page.value = 1; load() })
onMounted(load)
</script>
