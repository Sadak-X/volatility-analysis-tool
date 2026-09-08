<template>
  <app-shell>
    <div class="page">
      <div class="page-header">
        <div>
          <div style="font-size: 13px; color: #6b7280">分析详情</div>
          <h2 style="margin: 8px 0 0">
            {{ overview?.stock?.stockName || '处理中' }} / {{ overview?.stock?.stockCode || taskNo }}
          </h2>
        </div>
          <el-button type="primary" class="kraken-button" @click="router.push(`/assessment/detail/${taskNo}`)">
            查看评估
          </el-button>
      </div>

      <div v-if="!isAnalysisReady" class="section-card" style="padding: 20px; margin-bottom: 20px">
        <div style="display: flex; justify-content: space-between; gap: 16px; align-items: center; flex-wrap: wrap">
          <div>
            <div style="font-size: 14px; color: #6b7280; margin-bottom: 8px">任务状态</div>
            <div style="font-size: 24px; font-weight: 700">{{ statusText }}</div>
          </div>
          <el-tag :type="statusTagType">{{ overview?.taskStatus || 'PENDING' }}</el-tag>
        </div>
        <p style="margin: 16px 0 0; color: #4b5563">
          {{ overview?.errorMsg || overview?.resultSummary || waitingMessage }}
        </p>
        <p v-if="typeof overview?.progress === 'number'" style="margin: 12px 0 0; color: #6b7280">
          当前进度：{{ overview.progress }}%
        </p>
      </div>

      <template v-else>
        <div class="metric-grid" style="margin-bottom: 20px">
          <div class="metric-card">
            <div class="label">历史波动率</div>
            <div class="value">{{ formatPercent(overview?.historicalVolatility) }}</div>
          </div>
          <div class="metric-card">
            <div class="label">隐含波动率</div>
            <div class="value">{{ formatPercent(overview?.impliedVolatility) }}</div>
          </div>
          <div class="metric-card">
            <div class="label">置信度</div>
            <div class="value" style="font-size: 18px">{{ formatPercent(overview?.snapshot?.confidenceLevel) }}</div>
          </div>
          
        </div>
        <div v-if="overview?.insight" class="section-card insight-card">
          <div class="insight-kicker">Analysis Conclusion</div>
          <h3>{{ overview.insight.title || '分析结论' }}</h3>
          <p>{{ overview.insight.summary }}</p>
          <ul>
            <li v-for="item in overview.insight.bullets || []" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div class="section-card" style="padding: 20px">
          <div v-if="spanCharts.length" class="span-chart-panel">
            <div class="span-chart-toolbar">
              <div>
                <div class="span-chart-label">波动率趋势</div>
                <div class="span-chart-title">{{ activeSpanChart?.label }}跨度</div>
              </div>
              <el-radio-group v-model="selectedSpanMode" size="small">
                <el-radio-button v-for="chart in spanCharts" :key="chart.mode" :label="chart.mode">
                  {{ chart.label }}
                </el-radio-button>
              </el-radio-group>
            </div>
            <trend-chart :series="activeSpanChart?.series || []" />
          </div>
          <el-empty v-else description="暂无图表数据" />
        </div>
      </template>
    </div>
  </app-shell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/services'
import AppShell from '../components/AppShell.vue'
import TrendChart from '../components/TrendChart.vue'
import { useRouter } from 'vue-router'

const props = defineProps<{ taskNo: string }>()
const overview = ref<any>(null)
let pollTimer: number | undefined
const selectedSpanMode = ref<ChartSpanMode>('YEAR')
const router = useRouter()

type ChartSpanMode = string
type TrendPoint = { date: string; value?: number; impliedValue?: number | null; predValue?: number; ciLower?: number; ciUpper?: number }

const chartSpanLabels: Record<ChartSpanMode, string> = {
  YEAR: '年',
  MONTH: '月',
  WEEK: '周',
}
const defaultChartSpanModes: ChartSpanMode[] = ['YEAR', 'MONTH', 'WEEK']
const terminalStatuses = ['FORECASTED', 'AI_DONE', 'FAILED', 'ARCHIVED']

const formatPercent = (value?: number) => `${(Number(value || 0) * 100).toFixed(2)}%`

const isAnalysisReady = computed(() => Boolean(overview.value?.analysisReady))

const normalizeChartSpanModes = (value: unknown): ChartSpanMode[] => {
  if (!Array.isArray(value)) return defaultChartSpanModes
  const modes = value
    .map((mode) => String(mode || '').toUpperCase())
    .filter((mode) => defaultChartSpanModes.includes(mode) || /^CUSTOM_\d{1,3}$/.test(mode))
  return modes.length ? modes : defaultChartSpanModes
}

const spanModeLabel = (mode: ChartSpanMode) => {
  if (chartSpanLabels[mode]) return chartSpanLabels[mode]
  const match = /^CUSTOM_(\d{1,3})$/.exec(mode)
  return match ? `${match[1]}日` : mode
}

const selectedSpanModes = computed(() => normalizeChartSpanModes(overview.value?.snapshot?.chartSpanModes))

const spanCharts = computed(() => {
  const aggregations = overview.value?.aggregations || {}
  return selectedSpanModes.value
    .map((mode) => ({
      mode,
      label: spanModeLabel(mode),
      series: Array.isArray(aggregations[mode]) ? (aggregations[mode] as TrendPoint[]) : [],
    }))
    .filter((chart) => chart.series.length > 0)
})
const activeSpanChart = computed(() => {
  return spanCharts.value.find((chart) => chart.mode === selectedSpanMode.value) || spanCharts.value[0]
})

const syncSelectedSpanMode = () => {
  if (spanCharts.value.length && !spanCharts.value.some((chart) => chart.mode === selectedSpanMode.value)) {
    selectedSpanMode.value = spanCharts.value[0].mode
  }
}

const statusText = computed(() => {
  switch (overview.value?.taskStatus) {
    case 'FAILED':
      return '任务失败'
    case 'ARCHIVED':
      return '任务已归档'
    case 'FORECASTED':
    case 'AI_DONE':
      return '分析已完成'
    default:
      return '任务处理中'
  }
})

const statusTagType = computed(() => {
  switch (overview.value?.taskStatus) {
    case 'FAILED':
      return 'danger'
    case 'FORECASTED':
    case 'AI_DONE':
      return 'success'
    default:
      return 'warning'
  }
})

const waitingMessage = computed(() => {
  const code = overview.value?.stock?.stockCode || props.taskNo
  return `任务已提交，正在为 ${code} 获取行情并生成分析结果，页面会自动刷新。`
})

const clearPollTimer = () => {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

const schedulePoll = () => {
  clearPollTimer()
  if (!terminalStatuses.includes(overview.value?.taskStatus)) {
    pollTimer = window.setTimeout(loadOverview, 3000)
  }
}

const loadOverview = async () => {
  clearPollTimer()
  try {
    const response = await api.analysisOverview(props.taskNo)
    overview.value = response.data
    syncSelectedSpanMode()
    schedulePoll()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

onMounted(() => {
  void loadOverview()
})

onBeforeUnmount(() => {
  clearPollTimer()
})
</script>

<style scoped>
.span-chart-panel {
  display: grid;
  gap: 12px;
}

.insight-card {
  margin-bottom: 20px;
  padding: 20px;
}

.insight-kicker {
  color: #7132f5;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.insight-card h3 {
  margin: 8px 0 10px;
  font-size: 20px;
}

.insight-card p,
.insight-card li {
  color: #4b5563;
  line-height: 1.75;
}

.insight-card ul {
  margin: 12px 0 0;
  padding-left: 18px;
}

.span-chart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.span-chart-label {
  color: #686b82;
  font-size: 12px;
}

.span-chart-title {
  margin-top: 6px;
  color: #101114;
  font-size: 14px;
  font-weight: 700;
}
</style>
