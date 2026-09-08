<template>
  <app-shell>
    <div class="page assessment-page">
      <header class="assessment-topbar">
        <div>
          <div class="page-eyebrow">评估详情</div>
          <h1 class="assessment-title">{{ stockTitle }}</h1>

        </div>
        <el-button type="primary" class="kraken-button" @click="router.push(`/forecast/detail/${taskNo}`)">
          查看预测
        </el-button>
      </header>

      <section class="assessment-hero section-card">
        <div class="assessment-hero-copy">
          <div class="assessment-hero-label">综合波动率指数</div>
        </div>
        <div class="assessment-score-block">
          <div class="assessment-score">{{ formatScore(detail?.scoreTotal) }}</div>
          <div class="assessment-score-meta" :class="riskClass">
            {{ riskText }} / {{ detail?.qualitativeLabel || '-' }}
          </div>
        </div>
      </section>

      <section v-if="detail?.insight" class="assessment-explain section-card">
        <div>
          <div class="page-eyebrow">Assessment Explanation</div>
          <h2>{{ detail.insight.title || '评估解释' }}</h2>
          <p>{{ detail.insight.summary }}</p>
        </div>
        <ul>
          <li v-for="item in detail.insight.bullets || []" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="assessment-bars">

        <div class="assessment-bar-card assessment-bar-card--risk">
          <div class="assessment-bar-head">
            <span>风险评分</span>
            <strong>{{ formatScore(detail?.scoreRisk) }}</strong>
          </div>
          <div class="assessment-track">
            <i :style="{ width: scoreWidth(detail?.scoreRisk) }" />
          </div>
          <p>{{ riskHint }}</p>
        </div>
        <div class="assessment-bar-card assessment-bar-card--stable">
          <div class="assessment-bar-head">
            <span>稳定性</span>
            <strong>{{ formatScore(detail?.scoreStability) }}</strong>
          </div>
          <div class="assessment-track">
            <i :style="{ width: scoreWidth(detail?.scoreStability) }" />
          </div>
          <p>{{ stabilityHint }}</p>
        </div>
      </section>

      <section class="donchian-card section-card">
        <div class="donchian-header">
          <div>
            <div class="page-eyebrow">Donchian Channel</div>
            <h2>唐奇安通道</h2>
            <p>上轨为窗口内最高价，中轨为收盘均值，下轨为窗口内最低价。</p>
          </div>
          <div class="donchian-period">
            <span>周期</span>
            <strong>{{ windowText }}</strong>
          </div>
        </div>

        <div v-if="hasDonchian" class="donchian-layout">
          <div class="donchian-chart-shell">
            <v-chart class="donchian-chart" :option="channelOption" autoresize />
          </div>
          <div class="channel-summary">
            <div class="channel-position">
              <div class="channel-position-head">
                <span>当前位置</span>
                <strong>{{ channelPositionText }}</strong>
              </div>
              <div class="channel-band">
                <span class="channel-band-line channel-band-line--middle" :style="{ left: markerLeft(donchianValues.middle) }" />
                <span class="channel-marker" :style="{ left: markerLeft(donchianValues.latestClose) }">
                  <i />
                </span>
              </div>
              <div class="channel-scale">
                <span>{{ formatPrice(donchianValues.lower) }}</span>
                <span>{{ formatPrice(donchianValues.middle) }}</span>
                <span>{{ formatPrice(donchianValues.upper) }}</span>
              </div>
            </div>

            <div class="channel-level-grid">
              <div v-for="item in channelLevels" :key="item.label" class="channel-level">
                <span :class="['channel-dot', item.className]" />
                <div>
                  <span>{{ item.label }}</span>
                  <strong>{{ formatPrice(item.value) }}</strong>
                </div>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-else description="暂无唐奇安通道数据" />
      </section>
    </div>
  </app-shell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useRouter } from 'vue-router'
import { api } from '../api/services'
import AppShell from '../components/AppShell.vue'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

type DonchianPoint = {
  date?: string
  upper?: number
  middle?: number
  lower?: number
  latestClose?: number
}

type Donchian = DonchianPoint & {
  series?: DonchianPoint[]
}

type AssessmentDetail = {
  taskNo?: string
  stock?: {
    stockCode?: string
    stockName?: string
  }
  snapshot?: Record<string, any>
  scoreTotal?: number
  scoreStability?: number
  scoreRisk?: number
  riskLevel?: string
  qualitativeLabel?: string
  industryAvgVol?: number
  donchian?: Donchian
  insight?: {
    title?: string
    summary?: string
    bullets?: string[]
  }
}

const props = defineProps<{ taskNo: string }>()
const router = useRouter()
const detail = ref<AssessmentDetail | null>(null)

const toNumber = (value?: number | string | null) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const clamp = (value: number, min = 0, max = 100) => Math.min(Math.max(value, min), max)

const stockTitle = computed(() => {
  const stock = detail.value?.stock
  if (!stock?.stockName && !stock?.stockCode) return '评估详情'
  return `${stock.stockName || stock.stockCode} 评估详情`
})

const riskText = computed(() => {
  const level = detail.value?.riskLevel
  if (level === 'HIGH') return '高风险'
  if (level === 'LOW') return '低风险'
  return '中风险'
})

const riskClass = computed(() => {
  const level = detail.value?.riskLevel
  if (level === 'HIGH') return 'is-high'
  if (level === 'LOW') return 'is-low'
  return 'is-medium'
})

const riskHint = computed(() => {
  const score = toNumber(detail.value?.scoreRisk)
  if (score >= 75) return '风险分值偏高，需关注放量波动与突破信号。'
  if (score >= 55) return '风险处于中位区间，适合结合趋势继续观察。'
  return '风险分值较低，当前波动压力相对可控。'
})

const stabilityHint = computed(() => {
  const score = toNumber(detail.value?.scoreStability)
  if (score >= 75) return '稳定性较强，近期波动离散度较低。'
  if (score >= 55) return '稳定性一般，短期波动仍有扩散空间。'
  return '稳定性偏弱，价格节奏容易出现快速切换。'
})

const compareIndustryText = computed(() => {
  const industry = toNumber(detail.value?.industryAvgVol)
  const score = toNumber(detail.value?.scoreTotal)
  if (!industry) return '暂无行业均值对比数据。'
  if (score >= 75) return '综合评分高于常态区间，波动关注度较高。'
  if (score >= 55) return '综合评分处于中位区间，与行业对比后继续观察。'
  return '综合评分处于较低区间，整体波动压力有限。'
})

const donchianSeries = computed(() => {
  const rawSeries = detail.value?.donchian?.series
  const source = Array.isArray(rawSeries) && rawSeries.length ? rawSeries : [detail.value?.donchian]
  return source
    .filter((item): item is DonchianPoint => Boolean(item))
    .map((item, index) => ({
      date: item.date || `窗口${index + 1}`,
      upper: toNumber(item.upper),
      middle: toNumber(item.middle),
      lower: toNumber(item.lower),
      latestClose: toNumber(item.latestClose),
    }))
    .filter((item) => item.upper > 0 && item.lower > 0 && item.upper > item.lower)
})

const latestDonchianPoint = computed(() => donchianSeries.value.at(-1))

const donchianValues = computed(() => ({
  upper: toNumber(latestDonchianPoint.value?.upper),
  middle: toNumber(latestDonchianPoint.value?.middle),
  lower: toNumber(latestDonchianPoint.value?.lower),
  latestClose: toNumber(latestDonchianPoint.value?.latestClose),
}))

const hasDonchian = computed(() => {
  return donchianSeries.value.length > 0
})

const channelPositionText = computed(() => {
  const { upper, middle, lower, latestClose } = donchianValues.value
  if (!hasDonchian.value) return '-'
  if (latestClose >= upper) return '靠近上轨'
  if (latestClose <= lower) return '靠近下轨'
  if (latestClose >= middle) return '中上轨区间'
  return '中下轨区间'
})

const windowText = computed(() => {
  const snapshot = detail.value?.snapshot || {}
  const windowSize = snapshot.windowSize ? `${snapshot.windowSize} 日` : '当前窗口'
  const start = snapshot.dateStart
  const end = snapshot.dateEnd
  if (start && end) return `${windowSize} · ${start} 至 ${end}`
  return windowSize
})

const channelLevels = computed(() => [
  { label: '上轨', value: donchianValues.value.upper, className: 'is-upper' },
  { label: '中轨', value: donchianValues.value.middle, className: 'is-middle' },
  { label: '下轨', value: donchianValues.value.lower, className: 'is-lower' },
  { label: '最新价', value: donchianValues.value.latestClose, className: 'is-price' },
])

const channelOption = computed(() => {
  const points = donchianSeries.value
  const allValues = points.flatMap((item) => [item.upper, item.middle, item.lower, item.latestClose])
  const minValue = allValues.length ? Math.min(...allValues) : 0
  const maxValue = allValues.length ? Math.max(...allValues) : 1
  const range = Math.max(maxValue - minValue, 1)
  const min = Math.max(minValue - range * 0.15, 0)
  const max = maxValue + range * 0.15

  return {
    color: ['#ef4444', '#3f7bf6', '#16b981', '#f59e0b'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      valueFormatter: (value: number | string) => formatPrice(Number(value)),
    },
    legend: {
      top: 4,
      right: 12,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#687084' },
    },
    grid: { left: 58, right: 28, top: 58, bottom: 36 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((item) => item.date),
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false },
      axisLabel: { color: '#7b8194' },
    },
    yAxis: {
      type: 'value',
      min,
      max,
      axisLabel: { color: '#7b8194', formatter: (value: number) => formatPrice(value) },
      splitLine: { lineStyle: { color: '#eef0f5' } },
    },
    series: [
      {
        name: '上轨',
        type: 'line',
        data: points.map((item) => item.upper),
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 3 },
      },
      {
        name: '中轨',
        type: 'line',
        data: points.map((item) => item.middle),
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 3 },
      },
      {
        name: '下轨',
        type: 'line',
        data: points.map((item) => item.lower),
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 3, type: 'dashed' },
      },
      {
        name: '收盘价',
        type: 'line',
        data: points.map((item) => item.latestClose),
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { width: 2 },
      },
    ],
  }
})

const formatScore = (value?: number | string | null) => {
  if (value == null || value === '') return '-'
  return toNumber(value).toFixed(2)
}



const formatPrice = (value?: number | string | null) => {
  if (value == null || value === '') return '-'
  return toNumber(value).toFixed(2)
}

const scoreWidth = (value?: number | string | null) => `${clamp(toNumber(value))}%`


const markerLeft = (value: number) => {
  const values = donchianValues.value
  const percent = ((value - values.lower) / Math.max(values.upper - values.lower, 1e-6)) * 100
  return `${clamp(percent)}%`
}

onMounted(async () => {
  const response = await api.assessmentDetail(props.taskNo)
  detail.value = response.data
})
</script>

<style scoped>
.assessment-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.assessment-page .page-eyebrow {
  letter-spacing: 0;
}

.assessment-page :deep(.el-button) {
  border-radius: 8px !important;
}

.assessment-topbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.assessment-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
  font-weight: 800;
  color: #101114;
}

.assessment-subtitle {
  margin: 10px 0 0;
  color: #687084;
  font-size: 14px;
}

.assessment-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
  border-radius: 8px;
}

.assessment-hero-label {
  font-size: 18px;
  font-weight: 800;
  color: #222636;
}

.assessment-hero-copy p {
  margin: 8px 0 0;
  color: #687084;
  line-height: 1.6;
}

.assessment-score-block {
  min-width: 220px;
  text-align: right;
}

.assessment-score {
  font-size: 52px;
  line-height: 1;
  font-weight: 800;
  color: #dc2626;
}

.assessment-score-meta {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 700;
}

.assessment-score-meta.is-high {
  color: #dc2626;
}

.assessment-score-meta.is-medium {
  color: #d97706;
}

.assessment-score-meta.is-low {
  color: #149e61;
}

.assessment-explain {
  display: grid;
  gap: 14px;
  padding: 22px 24px;
}

.assessment-explain h2 {
  margin: 0;
  font-size: 22px;
}

.assessment-explain p,
.assessment-explain li {
  color: #596174;
  line-height: 1.75;
}

.assessment-explain p {
  margin: 10px 0 0;
}

.assessment-explain ul {
  margin: 0;
  padding-left: 18px;
}

.assessment-bars {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.assessment-bar-card {
  padding: 18px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #eceef3;
  box-shadow: var(--kraken-micro-shadow);
}

.assessment-bar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #687084;
  font-weight: 700;
}

.assessment-bar-head strong {
  color: #222636;
  font-size: 18px;
}

.assessment-track {
  height: 8px;
  margin-top: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf0f4;
}

.assessment-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #8d99a8;
}

.assessment-bar-card--risk .assessment-track i {
  background: #f0646b;
}

.assessment-bar-card--stable .assessment-track i {
  background: #24c486;
}

.assessment-bar-card p {
  margin: 12px 0 0;
  min-height: 42px;
  color: #8a91a1;
  font-size: 13px;
  line-height: 1.55;
}

.donchian-card {
  padding: 24px;
  border-radius: 8px;
}

.donchian-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
}

.donchian-header h2 {
  margin: 0;
  color: #101114;
  font-size: 22px;
}

.donchian-header p {
  margin: 8px 0 0;
  color: #687084;
  font-size: 14px;
}

.donchian-period {
  min-width: 180px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f7f8fb;
  text-align: right;
}

.donchian-period span {
  display: block;
  margin-bottom: 4px;
  color: #8a91a1;
  font-size: 12px;
}

.donchian-period strong {
  color: #222636;
  font-size: 14px;
}

.donchian-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.75fr);
  gap: 20px;
  align-items: stretch;
}

.donchian-chart-shell {
  min-height: 360px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #eef0f5;
}

.donchian-chart {
  width: 100%;
  height: 360px;
}

.channel-summary {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.channel-position,
.channel-level-grid {
  padding: 18px;
  border-radius: 8px;
  background: #f8f9fb;
  border: 1px solid #eef0f5;
}

.channel-position-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  color: #687084;
  font-weight: 700;
}

.channel-position-head strong {
  color: #222636;
}

.channel-band {
  position: relative;
  height: 18px;
  border-radius: 999px;
  background: linear-gradient(90deg, #16b981 0%, #3f7bf6 52%, #ef4444 100%);
}

.channel-band-line {
  position: absolute;
  top: -5px;
  bottom: -5px;
  width: 2px;
  transform: translateX(-1px);
  background: #ffffff;
}

.channel-marker {
  position: absolute;
  top: 50%;
  width: 24px;
  height: 24px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: #ffffff;
  border: 3px solid #f59e0b;
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.24);
}

.channel-marker i {
  position: absolute;
  inset: 5px;
  border-radius: 50%;
  background: #f59e0b;
}

.channel-scale {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  color: #7b8194;
  font-size: 12px;
}

.channel-level-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.channel-level {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.channel-level span:not(.channel-dot) {
  display: block;
  color: #8a91a1;
  font-size: 12px;
}

.channel-level strong {
  display: block;
  margin-top: 3px;
  color: #222636;
  font-size: 18px;
}

.channel-dot {
  width: 10px;
  height: 10px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.channel-dot.is-upper {
  background: #ef4444;
}

.channel-dot.is-middle {
  background: #3f7bf6;
}

.channel-dot.is-lower {
  background: #16b981;
}

.channel-dot.is-price {
  background: #f59e0b;
}

@media (max-width: 1180px) {
  .assessment-bars,
  .donchian-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .assessment-topbar,
  .assessment-hero,
  .donchian-header {
    align-items: stretch;
    flex-direction: column;
  }

  .assessment-score-block,
  .donchian-period {
    min-width: 0;
    text-align: left;
  }

  .assessment-score {
    font-size: 42px;
  }

  .channel-level-grid {
    grid-template-columns: 1fr;
  }
}
</style>
