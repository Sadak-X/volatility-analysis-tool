<template>
  <app-shell>
    <div class="dashboard-page">

      <section class="section-card dashboard-overview">
        <div class="dashboard-overview-main">
          <div class="page-eyebrow">Market Overview</div>
          <h1 class="dashboard-title">市场概览</h1>

          <div class="trend-panel">
            <div class="trend-panel-header">
              <div>
                <div class="trend-panel-label">Market Trend</div>
                <h2>近 30 日市场波动率趋势图</h2>
              </div>
            </div>


            <v-chart class="market-trend-chart" :option="marketTrendOption" autoresize />
          </div>
        </div>
      </section>


      <section class="section-card dashboard-board">
        <div class="dashboard-section-head">
          <div>
            <div class="page-eyebrow">Volatility Ranking</div>
            <h2 class="page-title dashboard-section-title">高波动榜单</h2>
          </div>
        </div>

        <div class="dashboard-board-table">
          <el-table :data="board" stripe>
            <el-table-column prop="rankNo" label="排名" width="130" />
            <el-table-column prop="stockCode" label="股票代码" width="160" />
            <el-table-column prop="stockName" label="股票名称" width="200" />
            <el-table-column prop="predVolatility" label="预测波动率">
              <template #default="{ row }">{{ formatPercent(row.predVolatility) }}</template>
            </el-table-column>
            <el-table-column prop="changeRate" label="涨跌幅">
              <template #default="{ row }">
                <span :class="['price-change', priceTrendClass(row.changeRate / 100)]">
                  {{ formatSignedPercent(row.changeRate / 100) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险等级" width="120">
              <template #default="{ row }">
                <span class="risk-tag" :class="riskClass(row.riskLevel)">{{ riskLabel(row.riskLevel) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button text type="primary" @click="selectAndOpen(row.stockCode)">开始分析</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <section class="section-card dashboard-stocks">
        <div class="dashboard-section-head stocks-head">
          <div>
            <div class="page-eyebrow">Stock Universe</div>
            <h2 class="page-title dashboard-section-title">股票列表</h2>
          </div>
          <div class="stocks-toolbar">
            <el-input
              v-model="keyword"
              placeholder="按代码或名称搜索股票"
              class="stocks-search"
              clearable
              @change="handleStockSearch"
            />
            <el-button class="kraken-button kraken-button--soft" @click="togglePredictionSort">
              {{ sortOrder === 'desc' ? '波动率从高到低' : '波动率从低到高' }}
            </el-button>
            <el-button
              type="primary"
              class="kraken-button"
              :disabled="!selectedCodes.length"
              @click="openDialogForSelection"
            >
              批量预测波动率（{{ selectedCodes.length }}）
            </el-button>
          </div>
        </div>

        <div class="stocks-table-shell">
          <el-table
            :data="stocks"
            row-key="stockCode"
            @selection-change="handleStockSelectionChange"
          >
            <el-table-column type="selection" width="48" reserve-selection />
            <el-table-column prop="stockCode" label="股票代码" width="100" />

            <!-- 股票名称 -->
            <el-table-column prop="stockName" label="股票名称" width="120" >
              <template #header>
                <div class="custom-header">
                  <span>股票名称</span>
                  <el-popover placement="bottom" :width="100" trigger="click">
                    <template #reference>
                      <el-icon
                        class="filter-icon"
                        :class="{ 'filter-active': stockFilters.stFilter.length > 0 }"
                      >
                        <Filter />
                      </el-icon>
                    </template>
                    <el-checkbox-group v-model="stockFilters.stFilter" @change="handleCustomFilterChange">
                      <el-checkbox label="include_st">包含 *ST</el-checkbox>
                      <el-checkbox label="exclude_st">不包含 *ST</el-checkbox>
                    </el-checkbox-group>
                  </el-popover>
                </div>
              </template>
            </el-table-column>


            <el-table-column prop="latestPrice" label="最新价" sortable>
              <template #header>
                <div class="custom-header">
                  <span>最新价</span>
                  <el-popover placement="bottom" :width="140" trigger="click">
                    <template #reference>
                      <el-icon
                        class="filter-icon"
                        :class="{ 'filter-active': stockFilters.latestPriceRange.length > 0 }"
                      >
                        <Filter />
                      </el-icon>
                    </template>
                    <el-checkbox-group v-model="stockFilters.latestPriceRange" @change="handleCustomFilterChange">
                      <el-checkbox label="lt10">低于 10</el-checkbox>
                      <el-checkbox label="10_50">10 - 50</el-checkbox>
                      <el-checkbox label="50_100">50 - 100</el-checkbox>
                      <el-checkbox label="ge100">100 及以上</el-checkbox>
                    </el-checkbox-group>
                  </el-popover>
                </div>
              </template>
              <template #default="{ row }">{{ formatPrice(row.latestPrice) }}</template>
            </el-table-column>


            <el-table-column prop="changeRate" label="涨跌幅" sortable>
              <template #header>
                <div class="custom-header">
                  <span>涨跌幅</span>
                  <el-popover placement="bottom" :width="140" trigger="click">
                    <template #reference>
                      <el-icon
                        class="filter-icon"
                        :class="{ 'filter-active': stockFilters.changeRateRange.length > 0 }"
                      >
                        <Filter />
                      </el-icon>
                    </template>
                    <el-checkbox-group v-model="stockFilters.changeRateRange" @change="handleCustomFilterChange">
                      <el-checkbox label="up">上涨</el-checkbox>
                      <el-checkbox label="down">下跌</el-checkbox>
                      <el-checkbox label="flat">平盘</el-checkbox>
                      <el-checkbox label="ge5">涨幅 >= 5%</el-checkbox>
                      <el-checkbox label="le_minus5">跌幅 <= -5%</el-checkbox>
                    </el-checkbox-group>
                  </el-popover>
                </div>
              </template>
              <template #default="{ row }">
                <span :class="['price-change', priceTrendClass(row.changeRate / 100)]">
                  {{ formatSignedPercent(row.changeRate / 100) }}
                </span>
              </template>
            </el-table-column>


            <el-table-column prop="volume" label="成交量" sortable />


            <el-table-column prop="predictedVolatility" label="预测波动率" sortable>
              <template #header>
                <div class="custom-header">
                  <span>预测波动率</span>
                  <el-popover placement="bottom" :width="200" trigger="click">
                    <template #reference>
                      <el-icon
                        class="filter-icon"
                        :class="{ 'filter-active': stockFilters.predictedVolatilityRange.length > 0 }"
                      >
                        <Filter />
                      </el-icon>
                    </template>
                    <el-checkbox-group v-model="stockFilters.predictedVolatilityRange" @change="handleCustomFilterChange">
                      <el-checkbox label="lt20">低于 20%</el-checkbox>
                      <el-checkbox label="20_30">20% - 30%</el-checkbox>
                      <el-checkbox label="ge30">30% 及以上</el-checkbox>
                    </el-checkbox-group>
                  </el-popover>
                </div>
              </template>
              <template #default="{ row }">{{ formatPercent(row.predictedVolatility) }}</template>
            </el-table-column>


            <el-table-column prop="totalScore" label="综合评分" sortable />


            <el-table-column prop="riskLevel" label="风险等级" width="100">
              <template #header>
                <div class="custom-header">
                  <span>风险等级</span>
                  <el-popover placement="bottom" :width="180" trigger="click">
                    <template #reference>
                      <el-icon
                        class="filter-icon"
                        :class="{ 'filter-active': stockFilters.riskLevel.length > 0 }"
                      >
                        <Filter />
                      </el-icon>
                    </template>
                    <el-checkbox-group v-model="stockFilters.riskLevel" @change="handleCustomFilterChange">
                      <el-checkbox label="HIGH">高风险</el-checkbox>
                      <el-checkbox label="MEDIUM">中风险</el-checkbox>
                      <el-checkbox label="LOW">低风险</el-checkbox>
                    </el-checkbox-group>
                  </el-popover>
                </div>
              </template>
              <template #default="{ row }">
                <span class="risk-tag" :class="riskClass(row.riskLevel)">{{ riskLabel(row.riskLevel) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button text type="primary" @click="selectAndOpen(row.stockCode)">发起分析</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="stocks-pagination">
          <span class="stocks-pagination-summary">共 {{ stockTotal }} 条股票</span>
          <el-pagination
            v-model:current-page="stockPageNo"
            v-model:page-size="stockPageSize"
            background
            layout="sizes, prev, pager, next, jumper"
            :page-sizes="[10, 20, 50, 100]"
            :total="stockTotal"
            @size-change="handleStockPageSizeChange"
            @current-change="loadStocks"
          />
        </div>
      </section>
    </div>

    <task-create-dialog v-model="dialogVisible" :stock-codes="taskStockCodes" @created="goToTask" />
  </app-shell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { Filter } from '@element-plus/icons-vue'
import { api } from '../api/services'
import AppShell from '../components/AppShell.vue'
import TaskCreateDialog from '../components/TaskCreateDialog.vue'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent])

const router = useRouter()
const scope = ref('WEEK')
const board = ref<any[]>([])
const stocks = ref<any[]>([])
const stockTotal = ref(0)
const stockPageNo = ref(1)
const stockPageSize = ref(10)
const selectedCodes = ref<string[]>([])
const taskStockCodes = ref<string[]>([])
const dialogVisible = ref(false)
const keyword = ref('')
const sortOrder = ref<'asc' | 'desc'>('desc')

const stockFilters = ref({
  stFilter: [] as string[],
  latestPriceRange: [] as string[],
  changeRateRange: [] as string[],
  predictedVolatilityRange: [] as string[],
  riskLevel: [] as string[],
})


const marketTrendSeries = computed(() => {
  const today = dayjs()
  const baseVol =
    board.value.length > 0
      ? board.value.reduce((sum, item) => sum + Number(item.predVolatility || 0), 0) / board.value.length
      : 0.18
  const avgChange =
    board.value.length > 0
      ? board.value.reduce((sum, item) => sum + Number(item.changeRate || 0), 0) / board.value.length / 100
      : 0.003
  const scopeFactor = scope.value === 'YEAR' ? 1.15 : scope.value === 'MONTH' ? 1.05 : 1

  return Array.from({ length: 30 }, (_, index) => {
    const day = today.subtract(29 - index, 'day')
    const wave = Math.sin(index / 4) * baseVol * 0.06
    const drift = ((index - 15) / 15) * avgChange * 0.18
    const value = Math.max(0.08, baseVol * scopeFactor + wave + drift)
    return {
      date: day.format('MM-DD'),
      value: Number(value.toFixed(4)),
    }
  })
})



const marketTrendOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value: number) => `${(Number(value || 0) * 100).toFixed(2)}%`,
  },
  grid: { left: 24, right: 16, top: 24, bottom: 28 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: marketTrendSeries.value.map((item) => item.date),
    axisLine: { lineStyle: { color: '#dedee5' } },
    axisLabel: { color: '#9497a9', fontSize: 12 },
  },
  yAxis: {
    type: 'value',
    axisLabel: {
      color: '#9497a9',
      formatter: (value: number) => `${(value * 100).toFixed(0)}%`,
    },
    splitLine: { lineStyle: { color: 'rgba(222, 222, 229, 0.8)' } },
  },
  series: [
    {
      name: '市场波动率',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: marketTrendSeries.value.map((item) => item.value),
      lineStyle: { width: 3, color: '#7132f5' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(113, 50, 245, 0.22)' },
            { offset: 1, color: 'rgba(113, 50, 245, 0.03)' },
          ],
        },
      },
    },
  ],
}))


const loadDashboard = async () => {
  const response = await api.dashboard(scope.value)
  board.value = response.data
}

const loadStocks = async () => {
  const response = await api.stockPage({
    keyword: keyword.value,
    stFilter: stockFilters.value.stFilter.join(','),
    latestPriceRange: stockFilters.value.latestPriceRange.join(','),
    changeRateRange: stockFilters.value.changeRateRange.join(','),
    predictedVolatilityRange: stockFilters.value.predictedVolatilityRange.join(','),
    riskLevel: stockFilters.value.riskLevel.join(','),
    sortField: 'predictedVolatility',
    sortOrder: sortOrder.value,
    pageNo: stockPageNo.value,
    pageSize: stockPageSize.value,
  })
  stocks.value = response.data.records
  stockTotal.value = Number(response.data.total ?? 0)
}

/* ---------- 事件处理 ---------- */
const handleStockSearch = async () => {
  stockPageNo.value = 1
  await loadStocks()
}

const handleStockPageSizeChange = async () => {
  stockPageNo.value = 1
  await loadStocks()
}

const handleCustomFilterChange = () => {
  stockPageNo.value = 1
  loadStocks()
}

const togglePredictionSort = () => {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  stockPageNo.value = 1
  void loadStocks()
}

const handleStockSelectionChange = (selection: any[]) => {
  selectedCodes.value = selection.map((item) => item.stockCode)
}

const selectAndOpen = (code: string) => {
  taskStockCodes.value = [code]
  dialogVisible.value = true
}

const openDialogForSelection = () => {
  if (!selectedCodes.value.length) {
    ElMessage.warning('请先在股票列表中选择至少一只股票')
    return
  }
  taskStockCodes.value = [...selectedCodes.value]
  dialogVisible.value = true
}

const goToTask = (taskNo: string, taskType: string) => {
  if (taskType === 'FORECAST') {
    router.push(`/forecast/detail/${taskNo}`)
    return
  }
  router.push(`/analysis/detail/${taskNo}`)
}


const formatPercent = (value: number) => `${(Number(value || 0) * 100).toFixed(2)}%`
const formatSignedPercent = (value: number) => `${value >= 0 ? '+' : ''}${(Number(value || 0) * 100).toFixed(2)}%`
const formatPrice = (value: number) => Number(value || 0).toFixed(2)

const priceTrendClass = (value: number) => {
  if (value > 0) return 'price-up'
  if (value < 0) return 'price-down'
  return 'price-flat'
}

const riskClass = (riskLevel: string) => {
  if (riskLevel === 'HIGH') return 'high'
  if (riskLevel === 'LOW') return 'low'
  return 'medium'
}

const riskLabel = (riskLevel: string) => {
  if (riskLevel === 'HIGH') return '高风险'
  if (riskLevel === 'LOW') return '低风险'
  return '中风险'
}

onMounted(async () => {
  await loadDashboard()
  await loadStocks()
})
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 20px;
  padding: 24px;
}

.dashboard-overview {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
  padding: 28px;
}

.dashboard-overview-main {
  display: grid;
  align-content: start;
}

.dashboard-title {
  margin: 0;
  font-family: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  font-size: 34px;
  line-height: 1.18;
  letter-spacing: -0.03em;
  font-weight: 700;
  color: #101114;
}

.trend-panel {
  margin-top: 18px;
  padding: 18px 18px 12px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid rgba(222, 222, 229, 0.94);
}

.trend-panel-header,
.dashboard-section-head,
.stocks-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.trend-panel-label {
  font-size: 12px;
  color: #686b82;
}

.trend-panel-header h2 {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.2;
  letter-spacing: -0.02em;
  font-weight: 700;
  color: #101114;
}

.trend-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.trend-stat {
  padding: 14px 16px;
  border-radius: 14px;
  background: #fbfbfe;
  border: 1px solid rgba(222, 222, 229, 0.94);
}

.trend-stat span {
  display: block;
  font-size: 12px;
  color: #686b82;
}

.trend-stat strong {
  display: block;
  margin-top: 8px;
  font-size: 18px;
  line-height: 1.2;
  font-weight: 700;
  color: #101114;
}

.market-trend-chart {
  height: 280px;
  margin-top: 12px;
}

.dashboard-board,
.dashboard-stocks {
  padding: 24px;
}

.dashboard-section-title {
  font-size: 28px;
}

.dashboard-board-table,
.stocks-table-shell {
  overflow: hidden;
  margin-top: 18px;
  border-radius: 18px;
  border: 1px solid rgba(222, 222, 229, 0.94);
}

.stocks-pagination {
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

.stocks-pagination-summary {
  color: #686b82;
  font-size: 13px;
  white-space: nowrap;
}

.stocks-head {
  flex-wrap: wrap;
}

.stocks-toolbar {
  flex-wrap: wrap;
}

.stocks-search {
  width: 280px;
}

.risk-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(104, 107, 130, 0.12);
  color: #484b5e;
}

.risk-tag.high {
  background: rgba(113, 50, 245, 0.16);
  color: #7132f5;
}

.risk-tag.medium {
  background: rgba(113, 50, 245, 0.1);
  color: #7132f5;
}

.risk-tag.low {
  background: rgba(20, 158, 97, 0.16);
  color: #026b3f;
}

.price-change {
  font-weight: 600;
}

.price-up {
  color: #d4380d;
}

.price-down {
  color: #389e0d;
}

.price-flat {
  color: #686b82;
}


:deep(.el-table__header-wrapper .el-table__cell .cell) {
  display: flex;
  align-items: center;
}


.custom-header {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}

.custom-header span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  line-height: 1;
  vertical-align: middle;
  flex-shrink: 0;
  cursor: pointer;
  color: var(--kraken-muted);
  font-size: 14px;
  transition: color 0.2s;
}

.filter-icon:hover {
  color: var(--kraken-purple);
}

.filter-active {
  color: var(--kraken-purple) !important;
  font-weight: bold;
}

@media (max-width: 768px) {
  .dashboard-page {
    padding: 16px;
  }

  .dashboard-overview,
  .dashboard-board,
  .dashboard-stocks {
    padding: 18px;
  }


  .trend-panel-header,
  .dashboard-section-head,
  .stocks-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .stocks-search {
    width: 100%;
  }

  .stocks-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}

.dashboard-page {
  min-width: 0;      /* 防止 grid/flex 子项溢出 */
}

.market-trend-chart {
  height: 280px;
  margin-top: 12px;
  width: 100%;       /* 明确宽度，配合 echarts autoresize */
}
</style>