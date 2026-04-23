<template>
  <AppLayout :title="title">
    <div class="assoc-layout">
      <div class="assoc-main-chart">
        <div class="panel-header" style="padding:12px 18px;font-size:13px;font-weight:700">
          {{ header }}
          <span style="font-size:11px;font-weight:400;color:#64748b;margin-left:10px">{{ subHeader }}</span>
        </div>

        <div v-if="type === 'association1'" style="flex:1;padding:16px;overflow:hidden">
          <div ref="chartA" style="width:100%;height:100%"></div>
        </div>

        <div v-else-if="type === 'association2'" style="flex:1;display:grid;grid-template-rows:1fr 1fr;gap:0;overflow:hidden">
          <div style="border-bottom:1px solid #f1f5f9;padding:8px">
            <div style="font-size:12px;font-weight:600;color:#64748b;margin-bottom:4px;padding:0 8px">各存储规格平均情感得分（%）</div>
            <div ref="chartA" style="height:calc(100% - 26px)"></div>
          </div>
          <div style="padding:8px">
            <div style="font-size:12px;font-weight:600;color:#64748b;margin-bottom:4px;padding:0 8px">价格区间 × 情感均分关联</div>
            <div ref="chartB" style="height:calc(100% - 26px)"></div>
          </div>
        </div>

        <div v-else style="flex:1;display:grid;grid-template-rows:3fr 2fr;overflow:hidden">
          <div style="border-bottom:1px solid #f1f5f9;position:relative">
            <div style="font-size:12px;font-weight:600;color:#64748b;padding:5px 14px;position:absolute;top:0;left:0;z-index:1">
              商品共现关联网络（节点大小=共现频次，连线粗细=关联强度）
            </div>
            <div ref="chartA" style="width:100%;height:100%"></div>
          </div>
          <div>
            <div style="font-size:12px;font-weight:600;color:#64748b;padding:5px 14px">品牌协同评论热力图</div>
            <div ref="chartB" style="width:100%;height:calc(100% - 26px)"></div>
          </div>
        </div>
      </div>

      <div class="assoc-sidebar">
        <div class="card">
          <div class="card-header">📌 分析说明</div>
          <div class="card-body" style="font-size:12px;color:#64748b;line-height:1.8">
            <template v-if="type === 'association1'">
              本模块通过挖掘用户评论情感标签与商品品牌之间的关联关系：
              <ul style="padding-left:16px;margin:8px 0"><li>统计各品牌的正面/中性/负面评论占比</li><li>挖掘品牌满意度分布规律</li><li>辅助用户了解品牌口碑差异</li></ul>
            </template>
            <template v-else-if="type === 'association2'">
              本模块挖掘商品规格参数与用户满意度的关联：
              <ul style="padding-left:16px;margin:8px 0"><li>分析不同存储配置对应的情感得分差异</li><li>揭示价格区间与用户好评率的关联规律</li><li>为消费者选购提供数据参考</li></ul>
            </template>
            <template v-else>
              通过挖掘同一用户评论过的商品对，发现：
              <ul style="padding-left:16px;margin:8px 0"><li>哪些商品被同一批用户共同关注</li><li>哪些品牌之间用户群体重叠度高</li><li>强关联商品对的支持度与频次</li></ul>
              <b>方法：</b>基于共现矩阵，类 Apriori 频繁项集挖掘。
            </template>
          </div>
        </div>

        <div v-if="type === 'association1'" class="card" style="flex:1;overflow:hidden">
          <div class="card-header">🏆 品牌好评率排名</div>
          <div class="card-body" style="padding:12px;overflow-y:auto;max-height:calc(100% - 44px)">
            <div v-for="(brand, index) in brandRank" :key="brand.brand" style="margin-bottom:10px">
              <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:3px">
                <span><b>{{ index + 1 }}.</b> {{ brand.brand }}</span>
                <span :style="{ color: rankColor(brand.posRate), fontWeight:700 }">{{ brand.posRate }}%</span>
              </div>
              <div style="height:6px;background:#e2e8f0;border-radius:3px;overflow:hidden">
                <div :style="{ height:'100%', width:`${brand.posRate}%`, background:rankColor(brand.posRate), borderRadius:'3px' }"></div>
              </div>
              <div style="font-size:10px;color:#94a3b8;margin-top:2px">共 {{ brand.total }} 条评论</div>
            </div>
          </div>
        </div>

        <div v-else-if="type === 'association2'" class="card" style="flex:1;overflow:hidden">
          <div class="card-header">🔵 评分 vs 情感分散点图</div>
          <div class="card-body" style="padding:8px;height:calc(100% - 44px)"><div ref="chartC" style="width:100%;height:100%"></div></div>
        </div>

        <div v-else class="card" style="flex:1;overflow:hidden;min-height:0">
          <div class="card-header">📋 强关联品牌规则</div>
          <div class="card-body" style="padding:10px;overflow-y:auto;height:calc(100% - 44px)">
            <div v-if="!rules.length" style="color:#94a3b8;text-align:center;padding:20px">暂无关联规则数据</div>
            <div v-for="(rule, index) in rules" :key="index" style="margin-bottom:10px;padding-bottom:8px;border-bottom:1px solid #f1f5f9">
              <div style="font-size:12px;font-weight:600;margin-bottom:4px"><span style="color:#2563eb">{{ rule.antecedent }}</span><span style="color:#94a3b8;margin:0 5px">⇄</span><span style="color:#16a34a">{{ rule.consequent }}</span></div>
              <div style="font-size:10px;color:#94a3b8;margin-top:2px">支持度 {{ ((rule.support || 0) * 100).toFixed(2) }}% · 共现 {{ rule.count }} 次</div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">{{ type === 'association1' ? '🎨 图例说明' : type === 'association2' ? '📊 规格数据排名' : '🔢 指标说明' }}</div>
          <div class="card-body" style="padding:12px;font-size:12px;color:#64748b;line-height:1.8">
            <template v-if="type === 'association1'">
              <div style="display:flex;gap:8px;margin-bottom:6px;align-items:center"><span style="width:14px;height:14px;background:#16a34a;border-radius:3px"></span><span>正面评价：情感分≥0.75</span></div>
              <div style="display:flex;gap:8px;margin-bottom:6px;align-items:center"><span style="width:14px;height:14px;background:#f59e0b;border-radius:3px"></span><span>中性评价：情感分 0.40~0.75</span></div>
              <div style="display:flex;gap:8px;align-items:center"><span style="width:14px;height:14px;background:#ef4444;border-radius:3px"></span><span>负面评价：情感分＜0.40</span></div>
            </template>
            <template v-else-if="type === 'association2'">
              <div v-for="(item, index) in specRank" :key="item.spec" style="display:flex;justify-content:space-between;padding:4px 0;border-bottom:1px solid #f1f5f9"><span>{{ index + 1 }}. {{ item.spec }}</span><span :style="{ color:rankColor(item.avgSentimentPct), fontWeight:700 }">{{ item.avgSentimentPct }}%</span></div>
            </template>
            <template v-else>
              <div><b>支持度</b>：商品对同时被评论的用户比例</div><div style="margin-top:4px"><b>共现次数</b>：同一用户同时评论的绝对次数</div><div style="margin-top:4px">连线越粗、节点越大 → 关联越强</div>
            </template>
          </div>
        </div>
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
const chartA = ref(), chartB = ref(), chartC = ref()
const data = ref({})
const type = computed(() => route.params.type)
const title = computed(() => ({ association1: '品牌情感关联分析', association2: '规格满意度关联分析', association3: '协同评论关联分析' })[type.value] || '关联分析')
const header = computed(() => ({ association1: '🔗 品牌 × 情感倾向关联矩阵', association2: '📈 商品规格 × 用户满意度关联分析', association3: '🛒 商品协同评论关联网络图' })[type.value])
const subHeader = computed(() => ({ association1: '分析各品牌用户评论的情感分布关联关系', association2: '挖掘存储规格、价格区间与情感分数的关联规律', association3: '同一用户评论过的商品对 · 类 Apriori 共现挖掘 · 拖拽可缩放' })[type.value])
const brandRank = computed(() => [...(data.value.brandStats || [])].sort((a, b) => b.posRate - a.posRate))
const specRank = computed(() => [...(data.value.specData || [])].sort((a, b) => b.avgSentimentPct - a.avgSentimentPct).slice(0, 8))
const rules = computed(() => data.value.rules || [])
function rankColor(v) { return v >= 70 ? '#16a34a' : v >= 50 ? '#f59e0b' : '#ef4444' }

async function load() {
  data.value = (await api.get(`/analysis/${type.value}`)).data || {}
  await nextTick()
  render()
}

function render() {
  if (type.value === 'association1') {
    echarts.init(chartA.value).setOption({
      tooltip:{trigger:'axis',axisPointer:{type:'shadow'}}, legend:{top:4,itemWidth:12,textStyle:{fontSize:12}}, grid:{left:'12%',right:'4%',top:'12%',bottom:'8%'},
      xAxis:{type:'value'}, yAxis:{type:'category',data:data.value.brands || []},
      series:(data.value.series || []).map(s => ({ name:s.name, type:'bar', stack:'total', data:s.data, itemStyle:{ color:{'正面':'#16a34a','中性':'#f59e0b','负面':'#ef4444'}[s.name] } }))
    })
  } else if (type.value === 'association2') {
    const specs = [...(data.value.specData || [])].sort((a, b) => b.avgSentimentPct - a.avgSentimentPct)
    echarts.init(chartA.value).setOption({ tooltip:{trigger:'axis'}, grid:{left:'14%',right:'6%',top:'4%',bottom:'18%'}, xAxis:{type:'category',data:specs.map(i=>i.spec),axisLabel:{fontSize:10,rotate:30}}, yAxis:{type:'value',min:0,max:100,axisLabel:{formatter:v=>v+'%'}}, series:[{type:'bar',data:specs.map(i=>i.avgSentimentPct),label:{show:true,position:'top',formatter:'{c}%'}}] })
    echarts.init(chartB.value).setOption({ tooltip:{trigger:'axis'}, grid:{left:'12%',right:'6%',top:'8%',bottom:'18%'}, xAxis:{type:'category',data:(data.value.priceSentiment||[]).map(i=>i.range)}, yAxis:{type:'value',min:0,max:1,axisLabel:{formatter:v=>Math.round(v*100)+'%'}}, series:[{type:'line',smooth:true,data:(data.value.priceSentiment||[]).map(i=>i.avg),areaStyle:{opacity:.2}}] })
    echarts.init(chartC.value).setOption({ tooltip:{}, xAxis:{type:'value',name:'评分',min:1,max:5}, yAxis:{type:'value',name:'情感分',min:0,max:1}, series:[{type:'scatter',data:(data.value.scatter||[]).map(i=>[i.x,i.y])}] })
  } else {
    echarts.init(chartA.value).setOption({ tooltip:{}, series:[{ type:'graph', layout:'force', roam:true, draggable:true, label:{show:true,fontSize:10}, data:data.value.nodes || [], links:data.value.links || [], force:{repulsion:400,edgeLength:[80,200]} }] })
    echarts.init(chartB.value).setOption({ tooltip:{}, grid:{left:'16%',right:'10%',top:'6%',bottom:'18%'}, xAxis:{type:'category',data:data.value.brands || []}, yAxis:{type:'category',data:data.value.brands || []}, visualMap:{min:0,max:10,orient:'horizontal',left:'center',bottom:0}, series:[{type:'heatmap',data:data.value.heatData || []}] })
  }
}

onMounted(load)
watch(type, load)
</script>
