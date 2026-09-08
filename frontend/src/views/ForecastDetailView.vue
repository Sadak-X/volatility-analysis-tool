<template>
  <app-shell>
    <div class="page">
      <div class="page-header">
        <div>
          <div style="font-size: 13px; color: #6b7280">预测详情</div>
          <h2 style="margin: 8px 0 0">{{ detailTitle }}</h2>
        </div>
        <div style="display: flex; gap: 10px">
          <el-button type="primary" :disabled="!detail?.forecastReady" @click="router.push(`/ai/analysis/${taskNo}`)">AI 分析</el-button>
        </div>
      </div>

      <div v-if="!detail?.forecastReady" class="section-card" style="padding: 20px; margin-bottom: 20px">
        <div style="display: flex; justify-content: space-between; gap: 16px; align-items: center; flex-wrap: wrap">
          <div>
            <div style="font-size: 14px; color: #6b7280; margin-bottom: 8px">任务状态</div>
            <div style="font-size: 24px; font-weight: 700">{{ statusText }}</div>
          </div>
          <el-tag :type="statusTagType">{{ detail?.taskStatus || 'PENDING' }}</el-tag>
        </div>
        <p style="margin: 16px 0 0; color: #4b5563">
          {{ detail?.errorMsg || detail?.resultSummary || '批量预测任务已提交，正在生成波动率预测结果。' }}
        </p>
        <p v-if="typeof detail?.progress === 'number'" style="margin: 12px 0 0; color: #6b7280">
          当前进度：{{ detail.progress }}%
        </p>
      </div>

      <template v-else>
        <div class="metric-grid" style="margin-bottom: 20px">
          <div class="metric-card">
            <div class="label">{{ detail?.isBatch ? '平均预测波动率' : '预测均值' }}</div>
            <div class="value">{{ formatPercent(detail?.predictVolatility) }}</div>
          </div>
          <div class="metric-card">
            <div class="label">{{ detail?.isBatch ? '整体置信区间' : '置信区间' }}</div>
            <div class="value" style="font-size: 18px">{{ formatPercent(detail?.ciLower) }} ~ {{ formatPercent(detail?.ciUpper) }}</div>
          </div>
          <div class="metric-card">
            <div class="label">风险等级</div>
            <div class="value" style="font-size: 18px">{{ riskLabel(detail?.riskLevel) }}</div>
          </div>
          <div class="metric-card">
            <div class="label">{{ detail?.isBatch ? '对比股票' : '预测跨度' }}</div>
            <div class="value" style="font-size: 18px">{{ detail?.isBatch ? `${detail?.results?.length || 0} 只` : forecastSpanLabel }}</div>
          </div>
        </div>
        <div v-if="detail?.insight" class="section-card forecast-explain">
          <div class="forecast-explain-kicker">Forecast Explanation</div>
          <h3>{{ detail.insight.title || '预测说明' }}</h3>
          <p>{{ detail.insight.summary }}</p>
          <ul>
            <li v-for="item in detail.insight.bullets || []" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div class="section-card" style="padding: 20px; margin-bottom: 20px">
          <div class="forecast-chart-toolbar">
            <div>
              <div class="forecast-chart-label">预测趋势</div>
              <div class="forecast-chart-title">{{ forecastSpanLabel }}跨度</div>
            </div>
            <el-radio-group v-model="selectedForecastSpan" size="small">
              <el-radio-button v-for="option in forecastSpanOptions" :key="option.value" :label="option.value">
                {{ option.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
          <trend-chart
            :series="activeForecastSeries"
            :comparison-series="activeComparisonSeries"
          />
        </div>
        <div class="section-card" style="padding: 20px">
          <el-table v-if="detail?.isBatch" :data="detail?.results || []">
            <el-table-column prop="stockCode" label="股票代码" width="120" />
            <el-table-column prop="stockName" label="股票名称" width="140" />
            <el-table-column prop="predictVolatility" label="预测波动率">
              <template #default="{ row }">{{ formatPercent(row.predictVolatility) }}</template>
            </el-table-column>
            <el-table-column label="置信区间">
              <template #default="{ row }">{{ formatPercent(row.ciLower) }} ~ {{ formatPercent(row.ciUpper) }}</template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险等级" width="110">
              <template #default="{ row }">{{ riskLabel(row.riskLevel) }}</template>
            </el-table-column>
            <el-table-column prop="modelName" label="模型" width="120" />
          </el-table>
          <el-table v-else :data="activeForecastSeries">
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column prop="predValue" label="预测值" />
            <el-table-column prop="ciLower" label="下界" />
            <el-table-column prop="ciUpper" label="上界" />
            <el-table-column prop="serValue" label="SER" />
          </el-table>
        </div>
      </template>
    </div>
  </app-shell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppShell from '../components/AppShell.vue'
import TrendChart from '../components/TrendChart.vue'
import { api } from '../api/services'

const props = defineProps<{ taskNo: string }>()
const router = useRouter()
const detail = ref<any>(null)
let pollTimer: number | undefined
const selectedForecastSpan = ref<'DAY' | 'MONTH' | 'YEAR'>('MONTH')

const formatPercent = (value?: number) => `${(Number(value || 0) * 100).toFixed(2)}%`
const terminalStatuses = ['FORECASTED', 'AI_DONE', 'FAILED', 'ARCHIVED']
const forecastSpanOptions = [
  { value: 'DAY', label: '日' },
  { value: 'MONTH', label: '月' },
  { value: 'YEAR', label: '年' },
] as const
const forecastSpanLabels = {
  DAY: '日',
  MONTH: '月',
  YEAR: '年',
}

const detailTitle = computed(() => {
  if (!detail.value) return `${props.taskNo} 预测详情`
  if (detail.value.isBatch || Number(detail.value.stockCount || 0) > 1) return '批量波动率预测对比'
  return `${detail.value.stock?.stockName || detail.value.stock?.stockCode || props.taskNo} 预测详情`
})

const forecastSpanLabel = computed(() => forecastSpanLabels[selectedForecastSpan.value])

const activeForecastSeries = computed(() => {
  const seriesByType = detail.value?.seriesByType || {}
  const series = seriesByType[selectedForecastSpan.value]
  return Array.isArray(series) && series.length ? series : detail.value?.series || []
})

const activeComparisonSeries = computed(() => {
  if (!detail.value?.isBatch) return []
  const seriesByType = detail.value?.comparisonSeriesByType || {}
  const series = seriesByType[selectedForecastSpan.value]
  return Array.isArray(series) && series.length ? series : detail.value?.comparisonSeries || []
})

const statusText = computed(() => {
  switch (detail.value?.taskStatus) {
    case 'FAILED':
      return '任务失败'
    case 'ARCHIVED':
      return '任务已归档'
    default:
      return '预测生成中'
  }
})

const statusTagType = computed(() => {
  switch (detail.value?.taskStatus) {
    case 'FAILED':
      return 'danger'
    case 'FORECASTED':
    case 'AI_DONE':
      return 'success'
    default:
      return 'warning'
  }
})

const riskLabel = (riskLevel?: string) => {
  if (riskLevel === 'HIGH') return '高风险'
  if (riskLevel === 'LOW') return '低风险'
  if (riskLevel === 'MEDIUM') return '中风险'
  return riskLevel || '-'
}

const clearPollTimer = () => {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

const schedulePoll = () => {
  clearPollTimer()
  if (!detail.value?.forecastReady && !terminalStatuses.includes(detail.value?.taskStatus)) {
    pollTimer = window.setTimeout(loadDetail, 3000)
  }
}

const loadDetail = async () => {
  clearPollTimer()
  try {
    const response = await api.forecastDetail(props.taskNo)
    detail.value = response.data
    syncSelectedForecastSpan()
    schedulePoll()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

const syncSelectedForecastSpan = () => {
  const available = detail.value?.seriesByType || {}
  const fallback = String(detail.value?.results?.[0]?.forecastType || detail.value?.forecastType || 'MONTH').toUpperCase()
  if ((fallback === 'DAY' || fallback === 'MONTH' || fallback === 'YEAR') && Array.isArray(available[fallback]) && available[fallback].length) {
    selectedForecastSpan.value = fallback
    return
  }
  if (Array.isArray(available[selectedForecastSpan.value]) && available[selectedForecastSpan.value].length) return
}

onMounted(() => {
  void loadDetail()
})

onBeforeUnmount(() => {
  clearPollTimer()
})
</script>

<style scoped>
.forecast-chart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.forecast-explain {
  margin-bottom: 20px;
  padding: 20px;
}

.forecast-explain-kicker {
  color: #7132f5;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.forecast-explain h3 {
  margin: 8px 0 10px;
  font-size: 20px;
}

.forecast-explain p,
.forecast-explain li {
  color: #4b5563;
  line-height: 1.75;
}

.forecast-explain ul {
  margin: 12px 0 0;
  padding-left: 18px;
}

.forecast-chart-label {
  color: #6b7280;
  font-size: 12px;
}

.forecast-chart-title {
  margin-top: 6px;
  color: #101114;
  font-size: 14px;
  font-weight: 700;
}
</style>
