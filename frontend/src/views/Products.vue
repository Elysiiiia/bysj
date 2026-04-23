<template>
  <AppLayout title="商品列表">
    <div class="card" style="margin-bottom:16px">
      <div class="card-body" style="padding:14px 18px">
        <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap">
          <input v-model="query.q" class="search-input" style="flex:1;min-width:200px" placeholder="搜索商品名称、品牌..." @keyup.enter="load" />
          <select v-model="query.brand" class="form-control" style="width:150px" @change="load">
            <option value="">全部品牌</option>
            <option v-for="brand in brands" :key="brand" :value="brand">{{ brand }}</option>
          </select>
          <button class="btn btn-primary" @click="load">🔍 搜索</button>
          <button class="btn btn-secondary" @click="reset">重置</button>
          <span style="color:#64748b;font-size:12px;margin-left:auto">共 {{ total }} 件商品</span>
        </div>
      </div>
    </div>

    <div v-if="rows.length" class="phone-grid" style="margin-bottom:20px">
      <PhoneCard v-for="phone in rows" :key="phone.id" :phone="phone" />
    </div>
    <div v-else style="text-align:center;padding:60px;color:#94a3b8">
      <div style="font-size:48px">🔍</div>
      <p style="margin-top:12px">未找到相关商品</p>
      <button class="btn btn-primary" style="margin-top:8px" @click="reset">查看全部</button>
    </div>
    <div v-if="totalPages > 1" class="pagination-wrap">
      <button class="page-btn" :class="{ disabled: query.page <= 1 }" @click="go(query.page - 1)">‹</button>
      <button v-for="p in pages" :key="p" class="page-btn" :class="{ active: p === query.page }" @click="go(p)">{{ p }}</button>
      <button class="page-btn" :class="{ disabled: query.page >= totalPages }" @click="go(query.page + 1)">›</button>
      <span class="pagination-info">第 {{ query.page }}/{{ totalPages }} 页，共 {{ total }} 件</span>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import PhoneCard from '../components/PhoneCard.vue'
import { api } from '../api'

const query = reactive({ page: 1, pageSize: 8, q: '', brand: '' })
const rows = ref([])
const total = ref(0)
const brands = ref([])
const totalPages = computed(() => Math.ceil(total.value / query.pageSize))
const pages = computed(() => {
  const start = Math.max(1, query.page - 2)
  const end = Math.min(totalPages.value, query.page + 2)
  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
})

async function load() {
  const res = await api.get('/phones', { params: query })
  rows.value = res.data.rows
  total.value = res.data.total
}
function go(page) {
  if (page < 1 || page > totalPages.value) return
  query.page = page
  load()
}
function reset() {
  query.page = 1
  query.q = ''
  query.brand = ''
  load()
}

onMounted(async () => {
  brands.value = (await api.get('/phones/brands')).data || []
  await load()
})
</script>
