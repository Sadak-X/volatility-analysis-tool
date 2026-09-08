<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="640px" @closed="resetForm">
    <el-form :model="form" label-width="110px">



      <el-form-item>
        <template #label>
          <span class="form-label">
            <el-tooltip content="选择股票数据的分析起止日期，区间长度至少需要覆盖窗口期所需的交易日数量" placement="top">
              <el-icon class="label-icon">
                <WarningFilled />
              </el-icon>
            </el-tooltip>
            分析区间
          </span>
        </template>
        <el-date-picker v-model="dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期"
          style="width: 100%" />
      </el-form-item>

      <el-form-item>
        <template #label>
          <span class="form-label">
            <el-tooltip content="选择波动率图表的聚合周期，自定义跨度以交易日为单位，范围2-252天" placement="top">
              <el-icon class="label-icon">
                <WarningFilled />
              </el-icon>
            </el-tooltip>
            图表跨度

          </span>
        </template>
        <div class="span-options">
          <el-checkbox-group v-model="baseChartSpanModes">
            <el-checkbox label="YEAR">年</el-checkbox>
            <el-checkbox label="MONTH">月</el-checkbox>
            <el-checkbox label="WEEK">周</el-checkbox>
          </el-checkbox-group>
          <div class="custom-span-row">
            <el-checkbox v-model="customSpanEnabled">自定义</el-checkbox>
            <el-input-number v-model="customSpanDays" :min="2" :max="252" :disabled="!customSpanEnabled"
              controls-position="right" />
            <span class="field-suffix">交易日</span>
          </div>
        </div>
      </el-form-item>


      <el-form-item>
        <template #label>
          <span class="form-label">
            <el-tooltip content="置信度越高，预测区间越宽；支持直接输入百分比或小数" placement="top">
              <el-icon class="label-icon">
                <WarningFilled />
              </el-icon>
            </el-tooltip>
            预测置信度

          </span>
        </template>
        <el-select v-model="confidenceInput" filterable allow-create default-first-option placeholder="选择或输入置信度">
          <el-option v-for="item in confidenceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>

      <!-- 窗口期 -->
      <el-form-item>
        <template #label>
          <span class="form-label">
            <el-tooltip content="用于计算波动率的滚动窗口大小，单位为交易日，范围10-120天" placement="top">
              <el-icon class="label-icon">
                <WarningFilled />
              </el-icon>
            </el-tooltip>
            窗口期

          </span>
        </template>
        <el-input-number v-model="form.windowSize" :min="10" :max="120" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">创建任务</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { api } from '../api/services'
import { WarningFilled } from '@element-plus/icons-vue'

type DataSourceType = 'AKSHARE' | 'EXCEL'
type ChartSpanMode = string

const props = withDefaults(defineProps<{
  modelValue: boolean
  stockCodes?: string[]
  dataSourceType?: DataSourceType
  fileId?: string
}>(), {
  stockCodes: () => [],
  dataSourceType: 'AKSHARE',
  fileId: '',
})

const emit = defineEmits<{
  'update:modelValue': [boolean]
  created: [string, string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const form = reactive({
  taskType: 'ANALYSIS',
  dataSourceType: 'AKSHARE' as DataSourceType,
  stockMode: 'SINGLE',
  timeGranularity: 'MONTH',
  windowSize: 20,
  forecastType: 'MONTH',
})
const baseChartSpanModes = ref<ChartSpanMode[]>(['YEAR', 'MONTH', 'WEEK'])
const customSpanEnabled = ref(false)
const customSpanDays = ref(20)

const dateRange = ref<[Date, Date]>([
  dayjs().subtract(1, 'year').toDate(),
  dayjs().toDate(),
])

const isExcelSource = computed(() => props.dataSourceType === 'EXCEL')
const isBatchForecast = computed(() => !isExcelSource.value && props.stockCodes.length > 1)
const dialogTitle = computed(() => {
  if (isBatchForecast.value) return '批量预测参数配置'
  if (isExcelSource.value) return 'Excel 分析参数配置'
  return '分析参数配置'
})
const chartSpanModes = computed(() => {
  const modes = [...baseChartSpanModes.value]
  if (customSpanEnabled.value) {
    modes.push(`CUSTOM_${customSpanDays.value}`)
  }
  return modes
})

watch(
  () => [props.stockCodes, props.dataSourceType] as const,
  ([codes, dataSourceType]) => {
    form.dataSourceType = dataSourceType
    form.stockMode = codes.length > 1 ? 'MULTI' : 'SINGLE'
    if (isExcelSource.value) {
      form.taskType = 'ANALYSIS'
    } else if (codes.length > 1) {
      form.taskType = 'FORECAST'
    } else {
      form.taskType = 'ANALYSIS'
    }
  },
  { immediate: true },
)

const countWeekdays = (range: [Date, Date]) => {
  let count = 0
  const end = dayjs(range[1]).startOf('day')
  for (let current = dayjs(range[0]).startOf('day'); current.isBefore(end, 'day') || current.isSame(end, 'day'); current = current.add(1, 'day')) {
    const day = current.day()
    if (day !== 0 && day !== 6) count += 1
  }
  return count
}

const validateAnalysisRange = () => {
  if (!chartSpanModes.value.length) {
    ElMessage.warning('请至少选择一个图表跨度')
    return false
  }
  if (customSpanEnabled.value && (!customSpanDays.value || customSpanDays.value < 2)) {
    ElMessage.warning('自定义图表跨度至少为 2 个交易日')
    return false
  }
  if (confidenceLevel.value <= 0 || confidenceLevel.value >= 1) {
    ElMessage.warning('置信度需大于 0% 且小于 100%')
    return false
  }
  const minDays = Math.max(form.windowSize + 3, 31)
  if (countWeekdays(dateRange.value) < minDays) {
    ElMessage.warning(`分析区间过短，当前窗口期至少需要约 ${minDays} 个交易日`)
    return false
  }
  return true
}

const submit = async () => {
  if (!isExcelSource.value && props.stockCodes.length === 0) {
    ElMessage.warning('请先选择股票')
    return
  }
  if (isExcelSource.value && !props.fileId) {
    ElMessage.warning('请先上传 Excel 文件')
    return
  }
  if (!validateAnalysisRange()) {
    return
  }
  loading.value = true
  try {
    const payload = {
      ...form,
      stockCodes: props.stockCodes,
      dateStart: dayjs(dateRange.value[0]).format('YYYY-MM-DD'),
      dateEnd: dayjs(dateRange.value[1]).format('YYYY-MM-DD'),
      chartSpanModes: chartSpanModes.value,
      confidenceLevel: confidenceLevel.value,
      useDefaultParams: false,
    }
    const response = isExcelSource.value
      ? await api.importExcel({
        fileId: props.fileId,
        taskType: payload.taskType,
        dateStart: payload.dateStart,
        dateEnd: payload.dateEnd,
        chartSpanModes: payload.chartSpanModes,
        confidenceLevel: payload.confidenceLevel,
        useDefaultParams: payload.useDefaultParams,
        timeGranularity: payload.timeGranularity,
        windowSize: payload.windowSize,
        forecastType: payload.forecastType,
      })
      : await api.createTask(payload)
    ElMessage.success(isBatchForecast.value ? '批量预测任务创建成功' : '任务创建成功')
    emit('created', response.data.taskNo, payload.taskType)
    visible.value = false
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

const confidenceOptions = [
  { label: '90%', value: '0.90' },
  { label: '95%', value: '0.95' },
  { label: '99%', value: '0.99' },
]


const confidenceInput = ref('0.90')


const confidenceLevel = computed(() => {
  const raw = confidenceInput.value.trim()
  if (raw === '') return 0.9

  const num = parseFloat(raw.replace('%', ''))
  if (isNaN(num)) return 0.9
  const value = num > 1 ? num / 100 : num
  return Math.min(0.99, Math.max(0.01, value))
})

const resetForm = () => {
  form.taskType = isBatchForecast.value ? 'FORECAST' : 'ANALYSIS'
}
</script>

<style scoped>
.span-options {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;

}

.custom-span-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-suffix {
  color: #686b82;
  font-size: 13px;
}
.form-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.label-icon {
  font-size: 14px;
  color: var(--kraken-muted);
  cursor: help;
  transition: color 0.2s;
}

.label-icon:hover {
  color: #f59e0b;
}
</style>
