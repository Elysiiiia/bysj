<template>
  <AppLayout title="个性化推荐">
    <div class="card">
      <div class="card-header">⭐ 个性化推荐列表</div>
      <div class="card-body">
        <div class="phone-grid"><PhoneCard v-for="phone in rows" :key="phone.id" :phone="phone" /></div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import PhoneCard from '../components/PhoneCard.vue'
import { api } from '../api'

const rows = ref([])
onMounted(async () => { rows.value = (await api.get('/recommendations?limit=16')).data || [] })
</script>
