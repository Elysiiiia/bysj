<template>
  <AppLayout title="数据分析大屏">
    <div class="dashboard-grid">
      <div class="chart-panel span-row">
        <div class="panel-header">📊 各品牌销售量分布</div>
        <div ref="brandEl" class="panel-body chart-container"></div>
      </div>
      <div class="chart-panel">
        <div class="panel-header">💰 价格区间分布</div>
        <div ref="priceEl" class="panel-body chart-container"></div>
      </div>
      <div class="chart-panel">
        <div class="panel-header">💬 评论情感倾向</div>
        <div ref="sentEl" class="panel-body chart-container"></div>
      </div>
      <div class="chart-panel">
        <div class="panel-header">📈 月度评论数量与评分趋势</div>
        <div ref="trendEl" class="panel-body chart-container"></div>
      </div>
      <div class="chart-panel">
        <div class="panel-header">🕸 品牌综合评价雷达图</div>
        <div ref="radarEl" class="panel-body chart-container"></div>
      </div>
      <div class="chart-panel">
        <div class="panel-header">☁ 评论高频词云</div>
        <div ref="wordEl" class="panel-body chart-container"></div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import AppLayout from '../../components/AppLayout.vue'
import { api } from '../../api'

const brandEl = ref()
const priceEl = ref()
const sentEl = ref()
const trendEl = ref()
const radarEl = ref()
const wordEl = ref()

const COLORS = ['#2563eb', '#16a34a', '#f59e0b', '#ef4444', '#7c3aed', '#0891b2', '#ec4899', '#84cc16']

onMounted(async () => {
  const data = (await api.get('/analysis/dashboard')).data || {}
  await nextTick()

  echarts.init(brandEl.value).setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '16%', right: '8%', top: '6%', bottom: '8%' },
    xAxis: { type: 'value', axisLabel: { formatter: (v) => v >= 10000 ? `${(v / 10000).toFixed(1)}万` : v } },
    yAxis: { type: 'category', data: (data.brandSales || []).map((i) => i.brand) },
    series: [{
      type: 'bar',
      data: (data.brandSales || []).map((i) => i.sales),
      barMaxWidth: 36,
      itemStyle: {
        color: (params) => COLORS[params.dataIndex % COLORS.length],
        borderRadius: [0, 4, 4, 0]
      },
      label: {
        show: true,
        position: 'right',
        formatter: (params) => params.value >= 10000 ? `${(params.value / 10000).toFixed(1)}万` : params.value
      }
    }]
  })

  echarts.init(priceEl.value).setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '8%', right: '6%', top: '10%', bottom: '18%' },
    xAxis: { type: 'category', data: (data.priceRanges || []).map((i) => i.range), axisLabel: { rotate: 15 } },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      data: (data.priceRanges || []).map((i) => i.count),
      barMaxWidth: 40,
      itemStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: '#2563eb' }, { offset: 1, color: '#93c5fd' }]
        },
        borderRadius: [4, 4, 0, 0]
      },
      label: { show: true, position: 'top', fontSize: 11 }
    }]
  })

  echarts.init(sentEl.value).setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '4%', itemWidth: 12, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['50%', '46%'],
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      data: (data.sentimentDist || []).map((i) => ({
        name: ({ positive: '正面评价', neutral: '中性评价', negative: '负面评价' })[i.label] || i.label,
        value: i.count,
        itemStyle: { color: ({ positive: '#16a34a', neutral: '#f59e0b', negative: '#ef4444' })[i.label] || '#94a3b8' }
      }))
    }]
  })

  echarts.init(trendEl.value).setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, itemWidth: 12, textStyle: { fontSize: 11 } },
    grid: { left: '10%', right: '8%', top: '22%', bottom: '12%' },
    xAxis: { type: 'category', data: (data.monthlyTrend || []).map((i) => i.month), axisLabel: { rotate: 20 } },
    yAxis: [
      { type: 'value', name: '评论数' },
      { type: 'value', name: '评分', min: 1, max: 5 }
    ],
    series: [
      { name: '评论数量', type: 'line', smooth: true, areaStyle: { opacity: 0.2 }, data: (data.monthlyTrend || []).map((i) => i.count), itemStyle: { color: '#2563eb' } },
      { name: '平均评分', type: 'line', smooth: true, yAxisIndex: 1, data: (data.monthlyTrend || []).map((i) => i.avgRating), itemStyle: { color: '#f59e0b' }, lineStyle: { type: 'dashed' } }
    ]
  })

  echarts.init(radarEl.value).setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 10, textStyle: { fontSize: 10 } },
    radar: {
      indicator: [
        { name: '情感均分', max: 100 },
        { name: '平均评分', max: 100 },
        { name: '评论热度', max: 100 },
        { name: '最高情感', max: 100 },
        { name: '最低情感', max: 100 }
      ],
      radius: '62%',
      center: ['50%', '48%']
    },
    series: [{
      type: 'radar',
      data: (data.radarData || []).map((i, index) => ({
        name: i.brand,
        value: [i.avgSentiment, i.avgRating, i.reviewCnt, i.maxSentiment, i.minSentiment],
        itemStyle: { color: COLORS[index % COLORS.length] },
        areaStyle: { opacity: 0.08 }
      }))
    }]
  })

  echarts.init(wordEl.value).setOption({
    series: [{
      type: 'wordCloud',
      shape: 'circle',
      left: 'center',
      top: 'center',
      width: '90%',
      height: '88%',
      sizeRange: [12, 44],
      rotationRange: [-45, 45],
      gridSize: 6,
      textStyle: { color: () => COLORS[Math.floor(Math.random() * COLORS.length)] },
      data: data.wordcloud || []
    }]
  })
})
</script>
