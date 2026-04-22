<template>
  <AppLayout :title="title">
    <div class="card">
      <div class="card-header">{{ title }}</div>
      <div class="card-body">
        <div ref="chartEl" style="height:560px"></div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import AppLayout from '../../components/AppLayout.vue'
import { api } from '../../api'

const route = useRoute()
const chartEl = ref()
const title = computed(() => ({ association1: '品牌情感关联', association2: '规格满意度关联', association3: '协同评论关联' })[route.params.type] || '关联分析')

async function load() {
  const data = (await api.get(`/analysis/${route.params.type}`)).data || {}
  await nextTick()
  const chart = echarts.init(chartEl.value)
  if (route.params.type === 'association1') {
    chart.setOption({ tooltip:{}, legend:{}, xAxis:{ type:'category', data:data.brands||[] }, yAxis:{ type:'value' }, series:data.series?.map(s=>({ ...s, type:'bar' })) || [] })
  } else if (route.params.type === 'association2') {
    chart.setOption({ tooltip:{}, xAxis:{ type:'category', data:(data.specData||[]).map(i=>i.spec) }, yAxis:{ type:'value' }, series:[{ type:'bar', data:(data.specData||[]).map(i=>i.avgSentimentPct) }] })
  } else {
    chart.setOption({ tooltip:{}, series:[{ type:'graph', layout:'force', roam:true, label:{ show:true }, data:data.nodes||[], links:data.links||[] }] })
  }
}
onMounted(load)
watch(() => route.params.type, load)
</script>
