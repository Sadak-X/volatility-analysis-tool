<template>
  <app-shell>
    <div class="page import-page">
      <div class="page-header">
        <div>
          <div class="page-eyebrow">Data Import</div>
          <h2 class="page-title">数据导入</h2>
        </div>
      </div>


      <div class="import-tabs">
        <button
          :class="['import-tab', { active: activeTab === 'AKSHARE' }]"
          @click="activeTab = 'AKSHARE'"
        >
          股票池搜索导入
        </button>
        <button
          :class="['import-tab', { active: activeTab === 'EXCEL' }]"
          @click="activeTab = 'EXCEL'"
        >
          上传 Excel 导入
        </button>
      </div>


      <div v-if="activeTab === 'AKSHARE'" class="section-card import-card">
        <div class="import-card-head">
          <div>
            <div class="page-eyebrow">Market Source</div>
            <h3 class="import-card-title">AKShare 股票池导入</h3>
          </div>
          <span class="import-chip">实时检索</span>
        </div>

        <el-form :model="marketForm" label-width="110px">
          <el-form-item label="股票搜索">
            <el-select
              v-model="marketForm.stockCodes"
              multiple
              filterable
              remote
              reserve-keyword
              clearable
              placeholder="输入代码或名称搜索真实股票池"
              :remote-method="searchStocks"
              :loading="searchLoading"
              style="width: 100%"
            >
              <el-option
                v-for="item in stockOptions"
                :key="item.stockCode"
                :label="`${item.stockName} (${item.stockCode}${item.marketType ? ` / ${item.marketType}` : ''})`"
                :value="item.stockCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="kraken-button"
              :loading="marketLoading"
              :disabled="!marketForm.stockCodes.length"
              @click="submitMarket"
            >
              导入股票
            </el-button>
          </el-form-item>
        </el-form>
      </div>


      <div v-if="activeTab === 'EXCEL'" class="section-card import-card">
        <div class="import-card-head">
          <div>
            <div class="page-eyebrow">Excel Source</div>
            <h3 class="import-card-title">Excel 上传导入</h3>
          </div>
          
          <span class="import-chip import-chip--soft">文件校验</span>
        </div>
                  <div class="template-bar">
            <span class="template-hint">建议先下载模板，按格式填写后上传</span>
            <el-button type="primary" class="kraken-button" @click="downloadTemplate">
              <el-icon><Download /></el-icon> 下载模板 Excel
            </el-button>
          </div>

        <el-upload
          drag
          :show-file-list="false"
          :http-request="customUpload"
          accept=".xls,.xlsx"
          style="width: 100%; margin-bottom: 16px"
        >
          <div class="upload-copy">
            <div class="upload-title">拖拽或点击上传 Excel</div>
            <div class="upload-subtitle">支持 xls / xlsx，最大 20MB</div>
          </div>
        </el-upload>

        <div v-if="uploadedFile" class="uploaded-file">
          已上传：{{ uploadedFile.originalFileName }}（{{ uploadedFile.fileId }}）
        </div>
      </div>
    </div>

    <task-create-dialog
      v-model="parameterDialogVisible"
      :stock-codes="analysisStockCodes"
      :data-source-type="analysisSource"
      :file-id="analysisFileId"
      @created="goToTask"
    />
  </app-shell>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import dayjs from 'dayjs'
import AppShell from '../components/AppShell.vue'
import TaskCreateDialog from '../components/TaskCreateDialog.vue'
import { api } from '../api/services'
import templateUrl from '../doc/import_template.xls?url'

const router = useRouter()
const activeTab = ref<'AKSHARE' | 'EXCEL'>('AKSHARE')
const marketLoading = ref(false)
const searchLoading = ref(false)
const uploadedFile = ref<null | { fileId: string; originalFileName: string }>(null)
const stockOptions = ref<Array<{ stockCode: string; stockName: string; marketType?: string }>>([])
const parameterDialogVisible = ref(false)
const analysisSource = ref<'AKSHARE' | 'EXCEL'>('AKSHARE')
const analysisStockCodes = ref<string[]>([])
const analysisFileId = ref('')

const marketForm = reactive({
  stockCodes: [],
})

const searchStocks = async (keyword: string) => {
  searchLoading.value = true
  try {
    const response = await api.stockSearch(keyword)
    stockOptions.value = response.data
  } finally {
    searchLoading.value = false
  }
}

const submitMarket = async () => {
  if (!marketForm.stockCodes.length) {
    ElMessage.warning('请至少选择一只股票')
    return
  }
  marketLoading.value = true
  try {
    await api.importStocks({
      taskType: 'ANALYSIS',
      dataSourceType: 'AKSHARE',
      stockMode: marketForm.stockCodes.length > 1 ? 'MULTI' : 'SINGLE',
      stockCodes: marketForm.stockCodes,
      dateStart: dayjs().subtract(1, 'year').format('YYYY-MM-DD'),
      dateEnd: dayjs().format('YYYY-MM-DD'),
    })


    const confirmAnalysis = await ElMessageBox.confirm(
      '股票已导入，是否立即创建分析任务？',
      '导入成功',
      { confirmButtonText: '去创建任务', cancelButtonText: '稍后再说', type: 'success' }
    ).catch(() => false)

    if (confirmAnalysis) {
      openAnalysisDialog('AKSHARE')
    }
    marketForm.stockCodes = []
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    marketLoading.value = false
  }
}

const customUpload = async (options: UploadRequestOptions) => {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const response = await api.uploadExcel(formData)
    const fileId = response.data.fileId
    uploadedFile.value = response.data
    ElMessage.success('文件上传成功')

    const confirmImport = await ElMessageBox.confirm(
      '文件已上传，是否立即导入其中的股票数据？',
      '确认导入',
      { confirmButtonText: '立即导入', cancelButtonText: '暂不导入', type: 'info' }
    ).catch(() => false)

    if (confirmImport) {
      try {
        const importResult = await api.importStocksFromExcel({
          fileId: fileId,
          taskType: 'ANALYSIS',
          dateStart: dayjs().subtract(1, 'year').format('YYYY-MM-DD'),
          dateEnd: dayjs().format('YYYY-MM-DD'),
        })
        const importedCount = importResult.data?.importedCount || 0
        const errorCount = importResult.data?.errorCount || 0

        const confirmAnalysis = await ElMessageBox.confirm(
          '股票数据已导入，是否立即创建分析任务？',
          '导入成功',
          { confirmButtonText: '去创建任务', cancelButtonText: '稍后再说', type: 'success' }
        ).catch(() => false)

        if (confirmAnalysis) {
          openAnalysisDialog('EXCEL')
        }
      } catch (error) {
        ElMessage.error((error as Error).message)
      }
    }
    options.onSuccess?.(response.data as any)
  } catch (error) {
    ElMessage.error((error as Error).message)
    options.onError?.(error as any)
  }
}
const downloadTemplate = () => {
  const link = document.createElement('a')
  link.href = templateUrl
  link.download = '数据导入模板.xls'
  link.click()
}

const openAnalysisDialog = (source: 'AKSHARE' | 'EXCEL') => {
  analysisSource.value = source
  analysisStockCodes.value = source === 'AKSHARE' ? [...marketForm.stockCodes] : []
  analysisFileId.value = source === 'EXCEL' ? uploadedFile.value?.fileId || '' : ''
  parameterDialogVisible.value = true
}

const goToTask = (taskNo: string, taskType: string) => {
  if (taskType === 'FORECAST') {
    router.push(`/forecast/detail/${taskNo}`)
    return
  }
  router.push(`/analysis/detail/${taskNo}`)
}

searchStocks('')
</script>

<style scoped>
.import-page {
  display: grid;
  gap: 24px;
}


.import-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.import-tab {
  padding: 10px 22px;
  border: 1px solid rgba(113, 50, 245, 0.2);
  border-radius: 12px;
  background: #ffffff;
  color: var(--kraken-muted);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.template-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.template-hint {
  font-size: 12px;
  color: #6c7289;
}

.import-tab:hover {
  background: rgba(113, 50, 245, 0.04);
}

.import-tab.active {
  background: var(--kraken-purple);
  color: #ffffff;
  border-color: var(--kraken-purple);
}

.import-card {
  padding: 24px;
}

.import-card-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
  margin-bottom: 18px;
}

.import-card-title {
  margin: 0;
  font-family: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  font-size: 28px;
  line-height: 1.2;
  letter-spacing: -0.03em;
  font-weight: 700;
}

.import-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(113, 50, 245, 0.12);
  color: #7132f5;
  font-size: 12px;
  font-weight: 600;
}

.import-chip--soft {
  background: rgba(20, 158, 97, 0.14);
  color: #026b3f;
}

.upload-copy {
  padding: 24px 0;
}

.upload-title {
  font-size: 16px;
  font-weight: 600;
  color: #101114;
}

.upload-subtitle {
  margin-top: 8px;
  color: #686b82;
}

.uploaded-file {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(113, 50, 245, 0.06);
  border: 1px solid rgba(113, 50, 245, 0.1);
  color: #4b3c85;
  font-size: 14px;
}

@media (max-width: 980px) {
  .import-card {
    padding: 18px;
  }
  .import-card-title {
    font-size: 24px;
  }
}
</style>