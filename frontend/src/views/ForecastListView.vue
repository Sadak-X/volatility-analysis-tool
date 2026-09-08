<template>
  <app-shell>
    <div class="page forecast-market-page">
      <section class="section-card forecast-summary">
        <div class="forecast-summary-main">
          <div class="page-eyebrow">Forecast List</div>
          <h1 class="forecast-page-title">预测结果列表</h1>
          <p class="forecast-page-subtitle">
            汇总展示预测波动率、置信区间与风险等级，方便对不同股票任务进行横向比较。
          </p>

          <div class="forecast-hero-metrics">
            <div class="forecast-hero-pill forecast-hero-pill--primary">
              <span>预测结果</span>
              <strong>{{ total }}</strong>
            </div>
            <div class="forecast-hero-pill">
              <span>高风险任务</span>
              <strong>{{ countByRisk('HIGH') }}</strong>
            </div>
            <div class="forecast-hero-pill">
              <span>低风险任务</span>
              <strong>{{ countByRisk('LOW') }}</strong>
            </div>
          </div>
        </div>

        <div class="forecast-summary-side">
          <div class="forecast-summary-head">
            <div>
              <div class="forecast-summary-label">风险分布</div>
              <div class="forecast-summary-caption">基于当前筛选条件下全部预测结果实时汇总</div>
            </div>
            <div class="forecast-summary-badge">LIVE</div>
          </div>

          <div class="forecast-risk-strip">
            <div class="forecast-risk-item forecast-risk-item--high">
              <div class="forecast-risk-top">
                <span>高风险</span>
                <em>{{ formatRatio('HIGH') }}</em>
              </div>
              <strong>{{ countByRisk('HIGH') }}</strong>
              <div class="forecast-risk-bar">
                <i :style="{ width: ratioWidth('HIGH') }"></i>
              </div>
            </div>

            <div class="forecast-risk-item forecast-risk-item--medium">
              <div class="forecast-risk-top">
                <span>中风险</span>
                <em>{{ formatRatio('MEDIUM') }}</em>
              </div>
              <strong>{{ countByRisk('MEDIUM') }}</strong>
              <div class="forecast-risk-bar">
                <i :style="{ width: ratioWidth('MEDIUM') }"></i>
              </div>
            </div>

            <div class="forecast-risk-item forecast-risk-item--low">
              <div class="forecast-risk-top">
                <span>低风险</span>
                <em>{{ formatRatio('LOW') }}</em>
              </div>
              <strong>{{ countByRisk('LOW') }}</strong>
              <div class="forecast-risk-bar">
                <i :style="{ width: ratioWidth('LOW') }"></i>
              </div>
            </div>

            <div class="forecast-risk-item forecast-risk-item--total">
              <div class="forecast-risk-top">
                <span>结果总数</span>
                <em>100%</em>
              </div>
              <strong>{{ total }}</strong>
              <div class="forecast-risk-bar">
                <i style="width: 100%"></i>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="section-card forecast-main-table">
        <div class="forecast-table-head">
          <div>
            <div class="page-eyebrow">Forecast Records</div>
            <h2 class="page-title forecast-table-title">预测明细</h2>
          </div>
          <div class="forecast-toolbar">
            <el-input v-model="keyword" placeholder="按股票代码筛选" class="forecast-search" clearable @change="handleSearch" />
          </div>
        </div>

        <div class="forecast-table-shell">
          <el-table :data="records" stripe>
            <el-table-column prop="taskNo" label="任务编号" width="190" />
            <el-table-column prop="stockCode" label="股票代码" width="110" />
            <el-table-column prop="forecastType" label="预测类型" width="110">
              <template #default="{ row }">{{ forecastTypeLabel(row.forecastType) }}</template>
            </el-table-column>
            <el-table-column prop="predictVolatility" label="预测均值">
              <template #default="{ row }">{{ formatPercent(row.predictVolatility) }}</template>
            </el-table-column>
            <el-table-column prop="ciLower" label="置信区间下界">
              <template #default="{ row }">{{ formatPercent(row.ciLower) }}</template>
            </el-table-column>
            <el-table-column prop="ciUpper" label="置信区间上界">
              <template #default="{ row }">{{ formatPercent(row.ciUpper) }}</template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险等级" width="100">
              <template #default="{ row }">
                <span class="forecast-chip" :class="riskClass(row.riskLevel)">{{ riskLabel(row.riskLevel) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="router.push(`/forecast/detail/${row.taskNo}`)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="forecast-pagination">
          <span class="forecast-pagination-summary">共 {{ total }} 条预测结果</span>
          <el-pagination
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            background
            layout="sizes, prev, pager, next, jumper"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            @size-change="handlePageSizeChange"
            @current-change="loadList"
          />
        </div>
      </section>
    </div>
  </app-shell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/services'
import AppShell from '../components/AppShell.vue'

const router = useRouter()
const keyword = ref('')
const records = ref<any[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const riskCounts = ref<Record<string, number>>({ HIGH: 0, MEDIUM: 0, LOW: 0 })

const loadList = async () => {
  const response = await api.forecastPage({ stockCode: keyword.value, pageNo: pageNo.value, pageSize: pageSize.value })
  records.value = response.data.records
  total.value = Number(response.data.total ?? 0)
  riskCounts.value = resolveRiskCounts(response.data.riskCounts, records.value)
}

const handleSearch = async () => {
  pageNo.value = 1
  await loadList()
}

const handlePageSizeChange = async () => {
  pageNo.value = 1
  await loadList()
}

const formatPercent = (value?: number) => `${(Number(value || 0) * 100).toFixed(2)}%`

const countByRisk = (riskLevel: string) => riskCounts.value[riskLevel] ?? 0

const resolveRiskCounts = (counts: Record<string, unknown> | undefined, currentRecords: any[]) => {
  if (counts) {
    return {
      HIGH: Number(counts.HIGH ?? 0),
      MEDIUM: Number(counts.MEDIUM ?? 0),
      LOW: Number(counts.LOW ?? 0),
    }
  }
  return currentRecords.reduce(
    (summary, item) => {
      if (item.riskLevel === 'HIGH' || item.riskLevel === 'MEDIUM' || item.riskLevel === 'LOW') {
        summary[item.riskLevel] += 1
      }
      return summary
    },
    { HIGH: 0, MEDIUM: 0, LOW: 0 } as Record<string, number>,
  )
}

const riskRatio = (riskLevel: string) => {
  if (!total.value) return 0
  return countByRisk(riskLevel) / total.value
}

const ratioWidth = (riskLevel: string) => {
  const ratio = riskRatio(riskLevel)
  return ratio === 0 ? '0%' : `${Math.max(ratio * 100, 6)}%`
}

const formatRatio = (riskLevel: string) => `${(riskRatio(riskLevel) * 100).toFixed(0)}%`

const riskClass = (riskLevel: string) => {
  if (riskLevel === 'HIGH') return 'forecast-chip-high'
  if (riskLevel === 'LOW') return 'forecast-chip-low'
  return 'forecast-chip-medium'
}

const riskLabel = (riskLevel: string) => {
  if (riskLevel === 'HIGH') return '高风险'
  if (riskLevel === 'LOW') return '低风险'
  return '中风险'
}

const forecastTypeLabel = (forecastType: string) => {
  if (forecastType === 'DAY') return '日频'
  if (forecastType === 'WEEK') return '周频'
  if (forecastType === 'MONTH') return '月频'
  return forecastType || '预测'
}

onMounted(loadList)
</script>

<style scoped>
.forecast-market-page {
  display: grid;
  gap: 20px;
}

.forecast-summary {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(380px, 0.9fr);
  gap: 22px;
  padding: 28px;
  overflow: hidden;
  background:
    radial-gradient(circle at left top, rgba(113, 50, 245, 0.08), transparent 30%),
    radial-gradient(circle at right top, rgba(20, 158, 97, 0.06), transparent 22%),
    rgba(255, 255, 255, 0.96);
}

.forecast-summary-main {
  display: grid;
  align-content: start;
  gap: 18px;
}

.forecast-page-title {
  margin: 0;
  font-family: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  font-size: 34px;
  line-height: 1.18;
  letter-spacing: -0.03em;
  font-weight: 700;
  color: #101114;
}

.forecast-page-subtitle {
  margin: 0;
  max-width: 760px;
  font-size: 15px;
  line-height: 1.7;
  color: #686b82;
}

.forecast-hero-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.forecast-hero-pill {
  min-width: 150px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(113, 50, 245, 0.1);
  box-shadow: 0 10px 24px rgba(16, 24, 40, 0.04);
}

.forecast-hero-pill--primary {
  background: linear-gradient(135deg, rgba(113, 50, 245, 0.12), rgba(113, 50, 245, 0.04));
  border-color: rgba(113, 50, 245, 0.18);
}

.forecast-hero-pill span {
  display: block;
  font-size: 12px;
  color: #686b82;
}

.forecast-hero-pill strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  line-height: 1;
  letter-spacing: -0.03em;
  color: #101114;
}

.forecast-summary-side {
  padding: 22px;
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(248, 248, 253, 0.94), rgba(255, 255, 255, 0.92)),
    #fbfbfe;
  border: 1px solid rgba(222, 222, 229, 0.94);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.forecast-summary-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.forecast-summary-label {
  font-size: 13px;
  font-weight: 600;
  color: #3c4060;
}

.forecast-summary-caption {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #8a8ea3;
}

.forecast-summary-badge {
  display: inline-flex;
  align-items: center;
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(113, 50, 245, 0.1);
  color: #7132f5;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.forecast-risk-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.forecast-risk-item {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(222, 222, 229, 0.94);
  box-shadow: 0 8px 20px rgba(16, 24, 40, 0.04);
}

.forecast-risk-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.forecast-risk-top em {
  font-style: normal;
  font-size: 12px;
  font-weight: 700;
  color: #686b82;
}

.forecast-risk-item span {
  display: block;
  font-size: 12px;
  color: #5e6278;
}

.forecast-risk-item strong {
  display: block;
  margin-top: 12px;
  font-size: 30px;
  line-height: 1.2;
  font-weight: 700;
  color: #101114;
}

.forecast-risk-bar {
  position: relative;
  height: 8px;
  margin-top: 14px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.06);
  overflow: hidden;
}

.forecast-risk-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(113, 50, 245, 0.9), rgba(113, 50, 245, 0.55));
}

.forecast-risk-item--high {
  background: linear-gradient(180deg, rgba(113, 50, 245, 0.09), rgba(255, 255, 255, 0.96));
  border-color: rgba(113, 50, 245, 0.16);
}

.forecast-risk-item--high .forecast-risk-bar i {
  background: linear-gradient(90deg, #7c3aed, #8b5cf6);
}

.forecast-risk-item--medium {
  background: linear-gradient(180deg, rgba(59, 130, 246, 0.08), rgba(255, 255, 255, 0.96));
  border-color: rgba(59, 130, 246, 0.14);
}

.forecast-risk-item--medium .forecast-risk-bar i {
  background: linear-gradient(90deg, #2563eb, #60a5fa);
}

.forecast-risk-item--low {
  background: linear-gradient(180deg, rgba(20, 158, 97, 0.08), rgba(255, 255, 255, 0.96));
  border-color: rgba(20, 158, 97, 0.16);
}

.forecast-risk-item--low .forecast-risk-bar i {
  background: linear-gradient(90deg, #059669, #34d399);
}

.forecast-risk-item--total {
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.05), rgba(255, 255, 255, 0.96));
}

.forecast-risk-item--total .forecast-risk-bar i {
  background: linear-gradient(90deg, #111827, #4b5563);
}

.forecast-main-table {
  padding: 24px;
}

.forecast-table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.forecast-table-title {
  font-size: 28px;
}

.forecast-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.forecast-search {
  width: 260px;
}

.forecast-table-shell {
  overflow: hidden;
  margin-top: 18px;
  border-radius: 18px;
  border: 1px solid rgba(222, 222, 229, 0.94);
}

.forecast-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(248, 248, 253, 0.9);
  border: 1px solid rgba(222, 222, 229, 0.78);
}

.forecast-pagination-summary {
  color: #686b82;
  font-size: 13px;
  white-space: nowrap;
}

.forecast-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}

.forecast-chip-high {
  background: rgba(113, 50, 245, 0.16);
  color: #7132f5;
}

.forecast-chip-medium {
  background: rgba(113, 50, 245, 0.1);
  color: #7132f5;
}

.forecast-chip-low {
  background: rgba(20, 158, 97, 0.16);
  color: #026b3f;
}

@media (max-width: 1200px) {
  .forecast-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .forecast-summary,
  .forecast-main-table {
    padding: 18px;
  }

  .forecast-hero-metrics {
    display: grid;
    grid-template-columns: 1fr;
  }

  .forecast-risk-strip {
    grid-template-columns: 1fr;
  }

  .forecast-table-head {
    flex-direction: column;
    align-items: stretch;
  }

  .forecast-toolbar,
  .forecast-search {
    width: 100%;
  }

  .forecast-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
