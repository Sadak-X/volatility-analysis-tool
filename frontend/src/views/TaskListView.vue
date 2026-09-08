<template>
  <app-shell>
    <div class="page task-page">
      <section class="section-card summary-card">
        <div>
          <div class="page-eyebrow">Task Center</div>
          <h1 class="page-title">任务列表</h1>
        </div>
        <div class="summary-grid">
          <div class="metric-box">
            <span>总任务</span>
            <strong>{{ summaryTotal }}</strong>
          </div>
          <div class="metric-box">
            <span>已完成</span>
            <strong>{{ countByGroup('completed') }}</strong>
          </div>
          <div class="metric-box">
            <span>运行中</span>
            <strong>{{ countByGroup('running') }}</strong>
          </div>
          <div class="metric-box">
            <span>失败</span>
            <strong>{{ countByGroup('failed') }}</strong>
          </div>
        </div>
      </section>

      <section class="section-card table-card">
        <div class="toolbar">
          <div>
            <div class="page-eyebrow">Task Records</div>
            <h2 class="table-title">任务记录</h2>
          </div>
          <div>
            <el-button type="danger" :disabled="selectedTaskNos.length === 0" @click="batchDelete">
              批量删除 ({{ selectedTaskNos.length }})
            </el-button> <el-button class="kraken-button kraken-button--secondary" @click="loadTasks(true)">
              刷新
            </el-button>
          </div>
        </div>

        <div class="table-shell">
          <el-table :data="tasks" @selection-change="handleSelectionChange" row-key="taskNo">
            <el-table-column type="selection" width="55" />
            <el-table-column prop="taskNo" label="任务编号" width="230" />
            <el-table-column prop="taskStatus" label="状态" width="120">
              <template #default="{ row }">
                <span class="tag" :class="statusClass(row.taskStatus)">{{ statusLabel(row.taskStatus) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="stockCode" label="股票代码" width="140">
              <template #default="{ row }">{{ taskStockCodeText(row) }}</template>
            </el-table-column>
            <el-table-column prop="stockName" label="股票名称" width="130">
              <template #default="{ row }">{{ taskStockNameText(row) }}</template>
            </el-table-column>
            <el-table-column prop="progress" label="进度" width="170">
              <template #default="{ row }">
                <div class="progress-cell">
                  <el-progress :percentage="Number(row.progress ?? 0)" :stroke-width="8" :show-text="false"
                    :color="progressColor(row.taskStatus)" />
                  <span>{{ row.progress ?? 0 }}%</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="resultSummary" label="结果摘要" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <div class="actions">
                  <el-button type="primary" size="small" @click="viewTask(row)">结果</el-button>
                  <el-button text size="small" @click="rerun(row.taskNo)">重跑</el-button>
                  <el-button text size="small" @click="deleteTask(row.taskNo)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination">
          <span>共 {{ total }} 条任务</span>
          <el-pagination v-model:current-page="pageNo" v-model:page-size="pageSize" background
            layout="sizes, prev, pager, next, jumper" :page-sizes="[10, 20, 50, 100]" :total="total"
            @size-change="handlePageSizeChange" @current-change="loadTasks" />
        </div>
      </section>

      <el-drawer v-model="drawerVisible" title="任务详情" size="70%" class="task-detail-drawer">
        <div v-loading="detailLoading" class="drawer-body">
          <template v-if="drawerTask.taskNo">
            <section class="drawer-hero">
              <div class="drawer-hero-copy">
                <div class="drawer-label">{{ drawerTypeLabel }}</div>
                <h3>{{ drawerTitleText }}</h3>
                <p class="drawer-summary">{{ drawerSummaryText }}</p>
              </div>
              <div class="drawer-hero-side">
                <span class="tag drawer-status-tag" :class="statusClass(drawerStatus)">{{ statusLabel(drawerStatus)
                  }}</span>
                <div class="drawer-status-note">{{ drawerStatusNote }}</div>
              </div>
            </section>

            <div class="overview-grid overview-grid--top">
              <div class="metric-box compact">
                <span>任务编号</span>
                <strong>{{ drawerTask.taskNo }}</strong>
              </div>
              <div class="metric-box compact">
                <span>进度</span>
                <strong>{{ drawerTask.progress ?? 0 }}%</strong>
              </div>
              <div class="metric-box compact">
                <span>股票范围</span>
                <strong>{{ taskStockCodeText(drawerTask) }}</strong>
              </div>
            </div>

            <el-tabs v-model="drawerActiveTab" class="drawer-tabs">
              <el-tab-pane label="波动率分析" name="analysis">
                <section class="drawer-panel">
                  <div class="section-head">
                    <div>
                      <span class="section-title">分析结果</span>
                    </div>
                    <el-button text @click="router.push(`/analysis/detail/${drawerTask.taskNo}`)">完整页</el-button>
                  </div>
                  <div class="overview-grid overview-grid--compact">
                    <div class="metric-box compact">
                      <span>历史波动率</span>
                      <strong>{{ formatPercent(drawerAnalysis?.historicalVolatility) }}</strong>
                    </div>
                    <div class="metric-box compact">
                      <span>隐含波动率</span>
                      <strong>{{ formatPercent(drawerAnalysis?.impliedVolatility) }}</strong>
                    </div>
                    <div class="metric-box compact">
                      <span>置信度</span>
                      <strong>{{ formatPercent(drawerSnapshot.confidenceLevel) }}</strong>
                    </div>
                  </div>

                  <!-- 分析结论 -->
                  <div v-if="drawerAnalysis?.insight" class="insight-card">
                    <div class="insight-kicker">Analysis Conclusion</div>
                    <h3>{{ drawerAnalysis.insight.title || '分析结论' }}</h3>
                    <p>{{ drawerAnalysis.insight.summary }}</p>
                    <ul>
                      <li v-for="item in drawerAnalysis.insight.bullets || []" :key="item">{{ item }}</li>
                    </ul>
                  </div>

                  <el-empty v-if="!drawerAnalysisReady && !drawerAnalysis?.insight" :description="drawerSummaryText" />
                </section>
              </el-tab-pane>

              <el-tab-pane label="评估结果" name="assessment" :disabled="!drawerCanOpenAssessment">
                <section class="drawer-panel">
                  <div class="section-head">
                    <div>
                      <span class="section-title">评估结果</span>
                    </div>
                    <el-button text :disabled="!drawerHasAssessment"
                      @click="router.push(`/assessment/detail/${drawerTask.taskNo}`)">
                      完整页
                    </el-button>
                  </div>
                  <template v-if="drawerHasAssessment">
                    <div class="overview-grid overview-grid--compact">
                      <div class="metric-box compact">
                        <span>综合评分</span>
                        <strong>{{ formatScore(drawerAssessment.scoreTotal) }}</strong>
                      </div>
                      <div class="metric-box compact">
                        <span>稳定性评分</span>
                        <strong>{{ formatScore(drawerAssessment.scoreStability) }}</strong>
                      </div>
                      <div class="metric-box compact">
                        <span>风险评分</span>
                        <strong>{{ formatScore(drawerAssessment.scoreRisk) }}</strong>
                      </div>
                      <div class="metric-box compact">
                        <span>风险等级</span>
                        <strong>{{ riskLabel(drawerAssessment.riskLevel) }}</strong>
                      </div>
                      <div class="metric-box compact">
                        <span>定性标签</span>
                        <strong>{{ drawerAssessment.qualitativeLabel || '-' }}</strong>
                      </div>
                    </div>

                    <div v-if="drawerAssessment.insight" class="insight-card">
                      <div class="insight-kicker">Assessment Explanation</div>
                      <h3>{{ drawerAssessment.insight.title || '评估解释' }}</h3>
                      <p>{{ drawerAssessment.insight.summary }}</p>
                      <ul>
                        <li v-for="item in drawerAssessment.insight.bullets || []" :key="item">{{ item }}</li>
                      </ul>
                    </div>
                  </template>
                  <el-empty v-else :description="drawerSummaryText" />
                </section>
              </el-tab-pane>

              <el-tab-pane label="预测结果" name="forecast" :disabled="!drawerCanOpenForecast">
                <section class="drawer-panel">
                  <div class="section-head">
                    <div>
                      <span class="section-title">预测结果</span>
                    </div>
                    <el-button text :disabled="!drawerHasForecast"
                      @click="router.push(`/forecast/detail/${drawerTask.taskNo}`)">
                      完整页
                    </el-button>
                  </div>
                  <template v-if="drawerHasForecast">
                    <div class="overview-grid overview-grid--compact">
                      <div class="metric-box compact">
                        <span>{{ drawerIsBatchForecast ? '平均预测波动率' : '预测波动率' }}</span>
                        <strong>{{ formatPercent(drawerForecast.predictVolatility) }}</strong>
                      </div>
                      <div class="metric-box compact">
                        <span>置信区间</span>
                        <strong>{{ formatRange(drawerForecast.ciLower, drawerForecast.ciUpper) }}</strong>
                      </div>
                      <div class="metric-box compact">
                        <span>风险等级</span>
                        <strong>{{ riskLabel(drawerForecast.riskLevel) }}</strong>
                      </div>

                    </div>

                    <div v-if="drawerForecast.insight" class="insight-card">
                      <div class="insight-kicker">Forecast Explanation</div>
                      <h3>{{ drawerForecast.insight.title || '预测说明' }}</h3>
                      <p>{{ drawerForecast.insight.summary }}</p>
                      <ul>
                        <li v-for="item in drawerForecast.insight.bullets || []" :key="item">{{ item }}</li>
                      </ul>
                    </div>
                  </template>
                  <el-empty v-else :description="drawerSummaryText" />
                </section>
              </el-tab-pane>
            </el-tabs>
          </template>
        </div>
      </el-drawer>
    </div>
  </app-shell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/services'
import AppShell from '../components/AppShell.vue'
import TrendChart from '../components/TrendChart.vue'

const router = useRouter()
const tasks = ref<any[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const drawerVisible = ref(false)
const detailLoading = ref(false)
const drawerTaskDetail = ref<any>(null)
const drawerAnalysis = ref<any>(null)
const drawerAssessment = ref<any>(null)
const drawerForecast = ref<any>(null)
const drawerActiveTab = ref<DrawerTab>('analysis')
const drawerSelectedSpanMode = ref<ChartSpanMode>('YEAR')
const selectedForecastSpan = ref<ForecastSpanMode>('MONTH')
let pollTimer: number | undefined

type ChartSpanMode = 'YEAR' | 'MONTH' | 'WEEK'
type ForecastSpanMode = 'DAY' | 'MONTH' | 'YEAR'
type DrawerTab = 'analysis' | 'assessment' | 'forecast'
type TrendPoint = { date: string; value?: number; impliedValue?: number | null; predValue?: number; ciLower?: number; ciUpper?: number }
type TaskGroup = 'running' | 'completed' | 'failed' | 'pending'
type TaskStatusSummary = {
  total: number
  groups: Partial<Record<TaskGroup | 'other', number>>
}

const runningStatuses = ['FETCHING', 'CLEANING', 'CALCULATED', 'ASSESSED']
const completedStatuses = ['FORECASTED', 'AI_DONE']
const pendingStatuses = ['PENDING']
const failedStatuses = ['FAILED']
const assessmentReadyStatuses = ['ASSESSED', 'FORECASTED', 'AI_DONE']
const forecastReadyStatuses = ['FORECASTED', 'AI_DONE']
const chartSpanLabels: Record<ChartSpanMode, string> = { YEAR: '年', MONTH: '月', WEEK: '周' }
const forecastSpanOptions = [
  { value: 'DAY', label: '日' },
  { value: 'MONTH', label: '月' },
  { value: 'YEAR', label: '年' },
] as const
const forecastSpanLabels: Record<ForecastSpanMode, string> = { DAY: '日', MONTH: '月', YEAR: '年' }

const statusSummary = ref<TaskStatusSummary>({ total: 0, groups: {} })
const summaryTotal = computed(() => Number(statusSummary.value.total || total.value || 0))
const hasSelection = computed(() => selectedTaskNos.value.length > 0)


const selectedTaskNos = ref<string[]>([])

const handleSelectionChange = (selection: any[]) => {
  selectedTaskNos.value = selection.map(item => item.taskNo)
  if (selectedTaskNos.value.length > 0) {
    clearPollTimer()
  } else {
    schedulePoll()
  }
}

const batchDelete = async () => {
  if (selectedTaskNos.value.length === 0) {
    ElMessage.warning('请先选择要删除的任务')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedTaskNos.value.length} 个任务吗？删除后所有分析数据将无法恢复。`,
      '批量删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await api.batchDeleteTasks(selectedTaskNos.value)
    ElMessage.success('批量删除成功')
    selectedTaskNos.value = []   // 清空选中
    await loadTasks(true)        // 强制刷新
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message || '批量删除失败')
    }
  }
}
const loadTasks = async (ignoreSelection = false) => {

  if (!ignoreSelection && selectedTaskNos.value.length > 0) {
    const response = await api.taskPage({ pageNo: pageNo.value, pageSize: pageSize.value })
    const data = response.data || {}
    statusSummary.value = normalizeStatusSummary(data.statusSummary, total.value)
    schedulePoll()
    return
  }


  clearPollTimer()
  const response = await api.taskPage({ pageNo: pageNo.value, pageSize: pageSize.value })
  const data = response.data || {}
  tasks.value = Array.isArray(data.records) ? data.records : []
  total.value = Number(data.total ?? 0)
  statusSummary.value = normalizeStatusSummary(data.statusSummary, total.value)
  schedulePoll()
}

const handlePageSizeChange = async () => {
  pageNo.value = 1
  await loadTasks()
}

const resolveInitialDrawerTab = (taskType?: string): DrawerTab => {
  if (taskType === 'FORECAST') return 'forecast'
  if (taskType === 'ASSESS') return 'assessment'
  return 'analysis'
}

const resetDrawer = (tab: DrawerTab) => {
  drawerActiveTab.value = tab
  drawerSelectedSpanMode.value = 'YEAR'
  selectedForecastSpan.value = 'MONTH'
  drawerTaskDetail.value = null
  drawerAnalysis.value = null
  drawerAssessment.value = null
  drawerForecast.value = null
}

const syncDrawerTab = () => {
  if (drawerActiveTab.value === 'forecast' && !drawerCanOpenForecast.value) {
    drawerActiveTab.value = drawerCanOpenAssessment.value ? 'assessment' : 'analysis'
    return
  }
  if (drawerActiveTab.value === 'assessment' && !drawerCanOpenAssessment.value) {
    drawerActiveTab.value = 'analysis'
  }
}

const viewTask = async (row: any) => {
  drawerVisible.value = true
  detailLoading.value = true
  resetDrawer(resolveInitialDrawerTab(row.taskType))
  try {
    const taskNo = row.taskNo
    const taskResponse = await api.taskDetail(taskNo)
    drawerTaskDetail.value = taskResponse.data
    const taskStatus = String(drawerTaskDetail.value?.task?.taskStatus || row.taskStatus || '')
    const [analysisResponse, assessmentResponse, forecastResponse] = await Promise.all([
      api.analysisOverview(taskNo),
      assessmentReadyStatuses.includes(taskStatus) ? api.assessmentDetail(taskNo).catch(() => null) : Promise.resolve(null),
      forecastReadyStatuses.includes(taskStatus) ? api.forecastDetail(taskNo).catch(() => null) : Promise.resolve(null),
    ])
    drawerAnalysis.value = analysisResponse.data
    drawerAssessment.value = assessmentResponse?.data ?? null
    drawerForecast.value = forecastResponse?.data ?? null
    syncDrawerSelectedSpanMode()
    syncSelectedForecastSpan()
    syncDrawerTab()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    detailLoading.value = false
  }
}
let singleTaskPollTimer: number | undefined

const pollTaskUntilDone = async (taskNo: string) => {
  if (singleTaskPollTimer) clearTimeout(singleTaskPollTimer)
  const poll = async () => {
    try {
      const detail = await api.taskDetail(taskNo)
      const status = detail.data?.task?.taskStatus || ''
      if (completedStatuses.includes(status) || failedStatuses.includes(status)) {

        clearTimeout(singleTaskPollTimer)
        singleTaskPollTimer = undefined
        await loadTasks()
        return
      }

      singleTaskPollTimer = window.setTimeout(poll, 2000)
    } catch (e) {
      console.error('轮询任务状态失败', e)
      singleTaskPollTimer = window.setTimeout(poll, 2000)
    }
  }
  poll()
}
const rerun = async (taskNo: string) => {
  await api.rerunTask(taskNo)
  ElMessage.success('任务已重新执行')
  await loadTasks()
  pollTaskUntilDone(taskNo)
}

const deleteTask = async (taskNo: string) => {
  try {
    await ElMessageBox.confirm('确定要删除该任务吗？删除后所有分析数据将无法恢复。', '提示', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await api.deleteTask(taskNo)
    ElMessage.success('任务已删除')

    await loadTasks(true)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message || '删除失败')
    }
  }
}

const normalizeStatusSummary = (value: any, fallbackTotal: number): TaskStatusSummary => {
  if (value?.groups) {
    return {
      total: Number(value.total ?? fallbackTotal ?? 0),
      groups: {
        running: Number(value.groups.running ?? 0),
        completed: Number(value.groups.completed ?? 0),
        failed: Number(value.groups.failed ?? 0),
        pending: Number(value.groups.pending ?? 0),
        other: Number(value.groups.other ?? 0),
      },
    }
  }
  return {
    total: fallbackTotal,
    groups: {
      running: tasks.value.filter((item) => runningStatuses.includes(item.taskStatus)).length,
      completed: tasks.value.filter((item) => completedStatuses.includes(item.taskStatus)).length,
      failed: tasks.value.filter((item) => failedStatuses.includes(item.taskStatus)).length,
      pending: tasks.value.filter((item) => pendingStatuses.includes(item.taskStatus)).length,
    },
  }
}

const countByGroup = (group: TaskGroup) => Number(statusSummary.value.groups[group] ?? 0)

const statusClass = (status: string) => {
  if (completedStatuses.includes(status)) return 'tag-success'
  if (failedStatuses.includes(status)) return 'tag-danger'
  if (runningStatuses.includes(status)) return 'tag-running'
  return 'tag-neutral'
}

const statusLabel = (status: string) => {
  if (completedStatuses.includes(status)) return '已完成'
  if (failedStatuses.includes(status)) return '失败'
  if (runningStatuses.includes(status)) return '运行中'
  if (pendingStatuses.includes(status)) return '待执行'
  return status || '未知'
}

const taskTypeLabel = (taskType: string) => {
  if (taskType === 'ANALYSIS') return '分析'
  if (taskType === 'ASSESS') return '评估'
  if (taskType === 'FORECAST') return '预测'
  return taskType || '任务'
}

const taskStockCodeText = (task: any) => {
  const count = Number(task?.stockCount || 0)
  if (count > 1) return `${task?.stockCode || '-'} 等 ${count} 只`
  return task?.stockCode || '-'
}

const taskStockNameText = (task: any) => {
  const count = Number(task?.stockCount || 0)
  if (count > 1) return `${task?.stockName || '-'} 等 ${count} 只`
  return task?.stockName || '-'
}

const progressColor = (status: string) => {
  if (completedStatuses.includes(status)) return '#149e61'
  if (failedStatuses.includes(status)) return '#d4380d'
  if (runningStatuses.includes(status)) return '#7132f5'
  return '#9497a9'
}

const clearPollTimer = () => {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

const schedulePoll = () => {
  if (countByGroup('running') > 0) {
    pollTimer = window.setTimeout(loadTasks, 3000)
  }
}

const drawerTask = computed(() => drawerTaskDetail.value?.task || {})
const drawerStatus = computed(() => String(drawerTask.value?.taskStatus || drawerAnalysis.value?.taskStatus || drawerForecast.value?.taskStatus || ''))
const drawerSnapshot = computed(() => drawerTaskDetail.value?.snapshot || drawerAnalysis.value?.snapshot || drawerAssessment.value?.snapshot || {})
const drawerTypeLabel = computed(() => `${taskTypeLabel(String(drawerTask.value?.taskType || ''))}任务`)
const drawerForecastResults = computed(() => (Array.isArray(drawerForecast.value?.results) ? drawerForecast.value.results : []))
const drawerCanOpenAssessment = computed(() => assessmentReadyStatuses.includes(drawerStatus.value))
const drawerCanOpenForecast = computed(() => forecastReadyStatuses.includes(drawerStatus.value))
const drawerHasAssessment = computed(() => Boolean(drawerAssessment.value))
const drawerHasForecast = computed(() => Boolean(drawerForecast.value?.forecastReady))
const drawerAnalysisReady = computed(() => Boolean(drawerAnalysis.value?.analysisReady))
const drawerIsBatchForecast = computed(() => drawerHasForecast.value && (Boolean(drawerForecast.value?.isBatch) || drawerForecastResults.value.length > 1))
const drawerTitleText = computed(() => {
  const stock = drawerAnalysis.value?.stock || drawerAssessment.value?.stock || drawerForecast.value?.stock || {}
  if (drawerIsBatchForecast.value) return `批量波动率预测对比（${drawerForecastResults.value.length} 只）`
  return `${stock.stockName || drawerTask.value?.stockName || '处理中'} / ${stock.stockCode || drawerTask.value?.stockCode || drawerTask.value?.taskNo || ''}`
})
const drawerSummaryText = computed(() => {
  return drawerTask.value?.errorMsg
    || drawerTask.value?.resultSummary
    || drawerAnalysis.value?.errorMsg
    || drawerAnalysis.value?.resultSummary
    || drawerForecast.value?.errorMsg
    || drawerForecast.value?.resultSummary
    || '任务处理中，暂未生成结果。'
})
const drawerStatusNote = computed(() => {
  if (failedStatuses.includes(drawerStatus.value)) return '本次任务执行失败，请查看结果摘要并按需重跑。'
  if (completedStatuses.includes(drawerStatus.value)) return '任务执行完成，可以在当前页面内直接查看分析、评估和预测。'
  if (runningStatuses.includes(drawerStatus.value)) return `任务正在执行中，当前进度 ${drawerTask.value?.progress ?? 0}% 。`
  return '任务已创建，等待进入执行流程。'
})



const drawerSpanCharts = computed(() => {
  const aggregations = drawerAnalysis.value?.aggregations || {}
  return ['YEAR', 'MONTH', 'WEEK']
    .map((mode) => ({
      mode: mode as ChartSpanMode,
      label: chartSpanLabels[mode as ChartSpanMode],
      series: Array.isArray(aggregations[mode]) ? (aggregations[mode] as TrendPoint[]) : [],
    }))
    .filter((item) => item.series.length > 0)
})


const syncDrawerSelectedSpanMode = () => {
  if (!drawerSpanCharts.value.some((item) => item.mode === drawerSelectedSpanMode.value) && drawerSpanCharts.value.length) {
    drawerSelectedSpanMode.value = drawerSpanCharts.value[0].mode
  }
}

const syncSelectedForecastSpan = () => {
  const seriesByType = drawerForecast.value?.seriesByType || {}
  const fallback = String(drawerForecast.value?.results?.[0]?.forecastType || drawerForecast.value?.forecastType || 'MONTH').toUpperCase()
  if ((fallback === 'DAY' || fallback === 'MONTH' || fallback === 'YEAR') && Array.isArray(seriesByType[fallback]) && seriesByType[fallback].length) {
    selectedForecastSpan.value = fallback
  }
}

const toNumber = (value?: number | string | null) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

const formatPercent = (value?: number | string | null) => {
  if (value == null || value === '') return '-'
  return `${(toNumber(value) * 100).toFixed(2)}%`
}

const formatScore = (value?: number | string | null) => {
  if (value == null || value === '') return '-'
  return toNumber(value).toFixed(2)
}

const formatPrice = (value?: number | string | null) => {
  if (value == null || value === '') return '-'
  return toNumber(value).toFixed(2)
}

const formatRange = (start?: number | string | null, end?: number | string | null) => `${formatPercent(start)} ~ ${formatPercent(end)}`

const riskLabel = (riskLevel?: string) => {
  if (riskLevel === 'HIGH') return '高风险'
  if (riskLevel === 'LOW') return '低风险'
  if (riskLevel === 'MEDIUM') return '中风险'
  return riskLevel || '-'
}

onMounted(loadTasks)
onBeforeUnmount(clearPollTimer)
</script>

<style scoped>
.task-page,
.drawer-body {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.drawer-body {
  padding-right: 4px;
}

.summary-card,
.table-card {
  padding: 24px;
  min-width: 0;
}

.summary-card {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 22px;
  align-items: stretch;
  background:
    radial-gradient(circle at left top, rgba(113, 50, 245, 0.08), transparent 34%),
    radial-gradient(circle at right top, rgba(20, 158, 97, 0.07), transparent 26%),
    rgba(255, 255, 255, 0.97);
}

.page-title,
.table-title {
  margin: 0;
  color: #101114;
}

.page-title {
  font-size: 32px;
}

.table-title {
  font-size: 26px;
}

.page-subtitle,
.drawer-summary {
  margin: 0;
  color: #686b82;
  line-height: 1.7;
  max-width: 100%;
}

.drawer-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  padding: 22px 24px;
  border: 1px solid rgba(222, 222, 229, 0.94);
  border-radius: 20px;
  background:
    radial-gradient(circle at top right, rgba(113, 50, 245, 0.12), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 248, 253, 0.96));
  box-shadow: 0 12px 28px rgba(16, 24, 40, 0.06);
}

.drawer-hero-copy {
  display: grid;
  gap: 10px;
}

.drawer-hero-side {
  display: grid;
  align-content: space-between;
  justify-items: end;
  gap: 12px;
  text-align: right;
}

.drawer-status-tag {
  min-height: 32px;
  padding-inline: 12px;
}

.drawer-status-note {
  max-width: 260px;
  color: #686b82;
  font-size: 13px;
  line-height: 1.6;
}

.table-shell {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}

.summary-grid,
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-grid--top {
  gap: 14px;
}

.overview-grid--compact {
  gap: 14px;
}

.summary-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
}

.metric-box {
  padding: 16px;
  border: 1px solid rgba(222, 222, 229, 0.94);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(248, 248, 253, 0.96)),
    #fbfbfe;
  box-shadow: 0 10px 24px rgba(16, 24, 40, 0.05);
  display: grid;
  gap: 8px;
  min-height: 112px;
  align-content: space-between;
}

.metric-box.compact {
  padding: 14px;
  min-height: 0;
  border-radius: 14px;
  box-shadow: none;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 248, 253, 0.94));
}

.summary-grid .metric-box:first-child {
  background:
    linear-gradient(135deg, rgba(113, 50, 245, 0.14), rgba(113, 50, 245, 0.05)),
    #ffffff;
  border-color: rgba(113, 50, 245, 0.18);
}

.metric-box span {
  display: block;
  color: #686b82;
  font-size: 12px;
}

.metric-box strong {
  display: block;
  margin-top: 8px;
  color: #101114;
  font-size: 18px;
}

.toolbar,
.drawer-header,
.section-head,
.chart-toolbar,
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.drawer-tabs {
  display: grid;
  gap: 16px;
}

.drawer-panel {
  display: grid;
  gap: 18px;
  padding: 20px;
  border: 1px solid rgba(222, 222, 229, 0.88);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
}

.section-title {
  display: block;
  color: #101114;
  font-size: 16px;
  font-weight: 700;
}

.section-caption {
  margin-top: 6px;
  color: #686b82;
  font-size: 13px;
  line-height: 1.5;
}

.chart-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 16px;
  background: rgba(248, 248, 253, 0.8);
  border: 1px solid rgba(222, 222, 229, 0.84);
}

.pagination {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(248, 248, 253, 0.92);
  border: 1px solid rgba(222, 222, 229, 0.78);
}

.insight-card {
  margin-top: 18px;
  padding: 18px 20px;
  border: 1px solid rgba(222, 222, 229, 0.94);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 248, 253, 0.94));
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

.progress-cell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.progress-cell span {
  color: #686b82;
  font-size: 12px;
}

.actions {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
}

.tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(104, 107, 130, 0.12);
  color: #484b5e;
  font-size: 12px;
  font-weight: 600;
}

.tag-type {
  background: rgba(113, 50, 245, 0.12);
  color: #7132f5;
}

.tag-success {
  background: rgba(20, 158, 97, 0.16);
  color: #026b3f;
}

.tag-danger {
  background: rgba(212, 56, 13, 0.12);
  color: #d4380d;
}

.tag-running {
  background: rgba(113, 50, 245, 0.14);
  color: #7132f5;
}

.tag-neutral {
  background: rgba(104, 107, 130, 0.12);
  color: #484b5e;
}

.drawer-label {
  color: #686b82;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.table-card :deep(.el-table) {
  margin-top: 18px;
  width: 100%;
  border: 1px solid rgba(222, 222, 229, 0.94);
  border-radius: 18px;
}

.table-card :deep(.el-table__inner-wrapper) {
  min-width: 0;
}

.drawer-header h3 {
  margin: 8px 0 0;
  font-size: 24px;
  color: #101114;
}

.forecast-table {
  margin-top: 0;
}

.task-detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding-bottom: 8px;
}

.task-detail-drawer :deep(.el-drawer__body) {
  padding-top: 12px;
  background:
    radial-gradient(circle at top right, rgba(113, 50, 245, 0.05), transparent 22%),
    #fcfcff;
}

.drawer-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.drawer-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: rgba(222, 222, 229, 0.9);
}

.drawer-tabs :deep(.el-tabs__item) {
  height: 40px;
  padding: 0 16px;
  font-weight: 700;
}

.drawer-tabs :deep(.el-tabs__item.is-active) {
  color: #7132f5;
}

.forecast-table :deep(.el-table) {
  border: 1px solid rgba(222, 222, 229, 0.92);
  border-radius: 16px;
}

@media (max-width: 960px) {
  .summary-card {
    grid-template-columns: 1fr;
  }

  .summary-grid,
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .drawer-hero {
    grid-template-columns: 1fr;
  }

  .drawer-hero-side {
    justify-items: start;
    text-align: left;
  }
}

@media (max-width: 768px) {

  .summary-card,
  .table-card {
    padding: 18px;
  }

  .summary-grid,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .toolbar,
  .drawer-header,
  .section-head,
  .chart-toolbar,
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }


  .drawer-panel,
  .chart-card,
  .drawer-hero {
    padding: 16px;
  }
}
</style>
