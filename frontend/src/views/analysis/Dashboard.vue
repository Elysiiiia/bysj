<template>
  <AppLayout title="数据分析大屏">
    <div class="dashboard-grid">
      <div class="chart-panel span-row"><div class="panel-header">📊 品牌销量分布</div><div ref="brandEl" class="panel-body chart-container"></div></div>
      <div class="chart-panel"><div class="panel-header">💰 价格区间</div><div ref="priceEl" class="panel-body chart-container"></div></div>
      <div class="chart-panel"><div class="panel-header">😊 情感倾向</div><div ref="sentEl" class="panel-body chart-container"></div></div>
      <div class="chart-panel"><div class="panel-header">📈 评论趋势</div><div ref="trendEl" class="panel-body chart-container"></div></div>
      <div class="chart-panel"><div class="panel-header">🎯 品牌雷达</div><div ref="radarEl" class="panel-body chart-container"></div></div>
      <div class="chart-panel"><div class="panel-header">☁ 评论高频词云</div><div ref="wordEl" class="panel-body chart-container"></div></div>
    </div>
  </AppLayout>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import AppLayout from '../../components/AppLayout.vue'
import { api } from '../../api'

const brandEl = ref(), priceEl = ref(), sentEl = ref(), trendEl = ref(), radarEl = ref(), wordEl = ref()
onMounted(async () => {
  const data = (await api.get('/analysis/dashboard')).data || {}
  await nextTick()
  echarts.init(brandEl.value).setOption({ tooltip:{trigger:'axis'}, grid:{left:'16%',right:'8%',top:'6%',bottom:'8%'}, xAxis:{ type:'value' }, yAxis:{ type:'category', data:(data.brandSales||[]).map(i=>i.brand) }, series:[{ type:'bar', data:(data.brandSales||[]).map(i=>i.sales), itemStyle:{borderRadius:[0,4,4,0]} }] })
  echarts.init(priceEl.value).setOption({ tooltip:{}, series:[{ type:'pie', radius:'65%', data:(data.priceRanges||[]).map(i=>({ name:i.range, value:i.count })) }] })
  echarts.init(sentEl.value).setOption({ tooltip:{}, series:[{ type:'pie', radius:['45%','70%'], data:(data.sentimentDist||[]).map(i=>({ name:i.label, value:i.count })) }] })
  echarts.init(trendEl.value).setOption({ xAxis:{ type:'category', data:(data.monthlyTrend||[]).map(i=>i.month) }, yAxis:{ type:'value' }, series:[{ type:'line', smooth:true, data:(data.monthlyTrend||[]).map(i=>i.count) }] })
  echarts.init(radarEl.value).setOption({ radar:{ indicator:[{name:'情感',max:100},{name:'评分',max:100},{name:'评论',max:100}] }, series:[{ type:'radar', data:(data.radarData||[]).map(i=>({ name:i.brand, value:[i.avgSentiment, i.avgRating, i.reviewCnt] })) }] })
  echarts.init(wordEl.value).setOption({ series:[{ type:'wordCloud', shape:'circle', width:'90%', height:'88%', sizeRange:[12,44], rotationRange:[-45,45], data:data.wordcloud || [] }] })
})
</script>
