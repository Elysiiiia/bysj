<template>
  <AppLayout title="商品列表">
    <div class="card">
      <div class="card-body">
        <div class="search-bar">
          <input v-model="query.q" class="search-input" placeholder="搜索商品或品牌" @keyup.enter="load" />
          <select v-model="query.brand" class="search-input" style="max-width:180px" @change="load">
            <option value="">全部品牌</option>
            <option v-for="brand in brands" :key="brand" :value="brand">{{ brand }}</option>
          </select>
          <button class="btn btn-primary" @click="load">搜索</button>
        </div>
        <div class="phone-grid"><PhoneCard v-for="phone in rows" :key="phone.id" :phone="phone" /></div>
        <div class="pagination-wrap">
          <button class="btn btn-secondary" :disabled="query.page <= 1" @click="query.page--; load()">上一页</button>
          <button class="btn btn-secondary" :disabled="query.page * query.pageSize >= total" @click="query.page++; load()">下一页</button>
          <span class="pagination-info">第 {{ query.page }} 页，共 {{ total }} 条</span>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import PhoneCard from '../components/PhoneCard.vue'
import { api } from '../api'

const query = reactive({ page: 1, pageSize: 8, q: '', brand: '' })
const rows = ref([])
const total = ref(0)
const brands = ref([])

async function load() {
  const res = await api.get('/phones', { params: query })
  rows.value = res.data.rows
  total.value = res.data.total
}

onMounted(async () => {
  brands.value = (await api.get('/phones/brands')).data || []
  await load()
})
</script>
