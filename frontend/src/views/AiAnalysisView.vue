<template>
  <app-shell>
    <div class="page ai-page">
      <div class="page-header ai-header">
        <div>
          <div class="page-eyebrow">AI Analysis</div>
          <h2 class="page-title">AI 波动率分析</h2>
        </div>
        <div class="ai-header-actions">
          <el-button :loading="reportDownloading" :disabled="!report || loading" @click="downloadPdfReport">
            导出 PDF 报告
          </el-button>
          <el-select v-model="analysisMode" class="analysis-mode-select">
            <el-option label="专业" value="PRO" />
            <el-option label="简洁" value="BRIEF" />
          </el-select>
          <el-button type="primary" class="kraken-button" :loading="loading" :disabled="loading" @click="generate">
            {{ loading ? '分析中...' : '重新分析' }}
          </el-button>
        </div>
      </div>
      <div class="ai-analysis-layout">
<div class="section-card followup-card">

  <div class="followup-card__header">
    <h3>追问助手</h3>
  </div>

<div class="followup-thread-wrapper">
  <div v-if="conversation.length" class="followup-thread">
    <div
      v-for="(item, index) in conversation"
      :key="`${item.role}-${index}`"
      :class="['message-bubble', item.role === 'user' ? 'message-user' : 'message-assistant']"
    >
      <div class="message-role">{{ item.role === 'user' ? '你' : 'AI 助手' }}</div>
      <div class="message-content">{{ cleanMessageText(item.content) }}</div>
    </div>
  </div>
</div>

  <div class="followup-bottom">
<!-- 智能推荐问题（可折叠） -->
<div v-if="suggestedQuestions.length" class="suggested-section">
  <div class="suggested-label" @click="recommendationsExpanded = !recommendationsExpanded">
    <span>智能推荐问题</span>
    <i :class="recommendationsExpanded ? 'arrow expanded' : 'arrow collapsed'" />
  </div>
  <div v-show="recommendationsExpanded" class="suggested-questions">
    <button
      v-for="item in suggestedQuestions"
      :key="item"
      type="button"
      @click="useSuggestedQuestion(item)"
    >
      {{ item }}
    </button>
  </div>
</div>

    <div class="input-area">
      <el-input
        v-model="question"
        type="textarea"
        :rows="4"
        resize="none"
        placeholder="接下来想进一步了解什么？"
        @keydown.enter.exact.prevent="followup"
      />
      <el-button
        type="primary"
        class="send-button"
        :loading="followupLoading"
        :disabled="followupDisabled"
        @click="followup"
      >
        {{ followupLoading ? '思考中...' : '发送追问' }}
      </el-button>
    </div>
  </div>
</div>

        <div class="section-card report-card">
          <div class="report-summary">{{ report?.summaryText }}</div>
          <pre class="report-body">{{ cleanReportMd }}</pre>
        </div>
      </div>
    </div>
  </app-shell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../components/AppShell.vue'
import { api } from '../api/services'

const props = defineProps<{ taskNo: string }>()
const analysisMode = ref('BRIEF')
const loading = ref(false)
const report = ref<any>(null)
const question = ref('')
const followupLoading = ref(false)
const reportDownloading = ref(false)
const conversation = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const suggestedQuestions = ref<string[]>([])

const recommendationsExpanded = ref(true)
const followupDisabled = computed(() => followupLoading.value || !question.value.trim())

const normalizeMarkdown = (md: string) => {
  if (!md) return ''
  return md
    .split('\n')
    .map(line => {
      let text = line.replace(/^#{1,6}\s*/, '')
      text = text.replace(/\*\*/g, '').replace(/__/g, '').replace(/\*/g, '')
      text = text.replace(/`/g, '')
      text = text.replace(/~~/g, '')
      text = text.replace(/^\s*[-*+]\s+/, '')
      return text.trimEnd()
    })
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

const cleanMessageText = (text: string) => {
  if (!text) return ''
  return text
    .replace(/\*\*/g, '')
    .replace(/__/g, '')
    .replace(/\*/g, '')
    .replace(/`/g, '')
    .replace(/#{1,6}\s*/g, '')
    .replace(/~~/g, '')
    .trim()
}
const cleanReportMd = computed(() => normalizeMarkdown(report.value?.fullReportMd))

const loadReport = async () => {
  try {
    const res = await api.getLatestAiReport(props.taskNo)
    if (res.data && res.data.fullReportMd) {
      report.value = res.data
      suggestedQuestions.value = res.data.suggestedQuestions || []
      return true
    }
    return false
  } catch {
    return false
  }
}

const generateIfNeeded = async () => {
  const existed = await loadReport()
  if (!existed) {
    await generate()
  }
}

const generate = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const response = await api.generateAi(props.taskNo, { analysisMode: analysisMode.value })
    report.value = response.data
    suggestedQuestions.value = response.data.suggestedQuestions || []
    conversation.value = []
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

const followup = async () => {
  const trimmedQuestion = question.value.trim()
  if (followupLoading.value || !trimmedQuestion) return
  followupLoading.value = true
  try {
    const history = conversation.value.map(item => ({ role: item.role, content: item.content }))
    conversation.value.push({ role: 'user', content: trimmedQuestion })
    question.value = ''
    const response = await api.followupAi(props.taskNo, { question: trimmedQuestion, history })
    conversation.value.push({ role: 'assistant', content: response.data.answer })
    suggestedQuestions.value = response.data.suggestedQuestions || []
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    followupLoading.value = false
  }
}

const useSuggestedQuestion = (value: string) => {
  question.value = value
}

const saveBlob = (blob: Blob, filename: string) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

const downloadPdfReport = async () => {
  if (reportDownloading.value) return
  if (!report.value) {
    ElMessage.warning('请先生成 AI 分析报告')
    return
  }
  reportDownloading.value = true
  try {
    const blobResponse = await api.downloadAiReportPdf(props.taskNo)
    saveBlob(blobResponse as unknown as Blob, `report-${props.taskNo}.pdf`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    reportDownloading.value = false
  }
}

onMounted(generateIfNeeded)
</script>

<style scoped>
.ai-page {
  min-height: 100vh;
  max-height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-header {
  align-items: center;
  flex-shrink: 0;
}

.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.analysis-mode-select {
  width: 126px;
}

.ai-analysis-layout {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}


.followup-card {
  position: static;
  height: 100%;
  max-height: 100%;
  overflow: hidden;
  padding: 22px;
  background: linear-gradient(145deg, rgba(113, 50, 245, 0.1), rgba(255, 255, 255, 0) 42%), #ffffff;
  display: flex;
  flex-direction: column;
}


.followup-card__header {
  flex-shrink: 0;
}


.followup-thread-wrapper {
  flex: 1;
  overflow-y: auto;
  margin: 14px 0;
  min-height: 0;
}

.followup-thread {
  display: flex;
  flex-direction: column;
  gap: 14px;
}


.followup-bottom {
  flex-shrink: 0;
}


.followup-thread {
  margin-bottom: 0;
}


.message-bubble {
  max-width: 100%;
  padding: 14px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.7;
}

.message-user {
  background: #f0f0ff;
  align-self: flex-end;
}

.message-assistant {
  background: #f5f5fa;
  border: 1px solid rgba(113, 50, 245, 0.15);
}

.message-role {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 6px;
  color: var(--kraken-purple-dark);
  text-transform: uppercase;
}

.message-user .message-role {
  color: #1e293b;
}

.suggested-section {
  margin-bottom: 18px;
}

.suggested-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--kraken-muted);
  margin-bottom: 10px;
}

.suggested-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.suggested-questions button {
  padding: 8px 14px;
  border: 1px solid rgba(113, 50, 245, 0.2);
  border-radius: 20px;
  background: white;
  color: #4b3c85;
  font-size: 13px;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s;
}

.suggested-questions button:hover {
  background: rgba(113, 50, 245, 0.06);
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.send-button {
  width: 100%;
  border-radius: 12px;
  font-weight: 600;
}

.report-card {
  height: 100%;
  max-height: 100%;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
}

.report-card {
  min-height: auto !important;
}

.report-summary {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  color: #475467;
  line-height: 1.7;
  background: #f8f8fc;
  border: 1px solid rgba(222, 222, 229, 0.86);
}

.report-body {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.9;
  color: #111827;
  font-size: 14px;
  font-family: var(--kraken-body);
}

.suggested-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.arrow {
  display: inline-block;
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  transition: transform 0.2s;
}

.arrow.expanded {
  border-bottom: 6px solid var(--kraken-muted);
}

.arrow.collapsed {
  border-top: 6px solid var(--kraken-muted);
}

@media (max-width: 1080px) {
  .ai-analysis-layout {
    grid-template-columns: 1fr;
  }

  .followup-card {
    position: static;
  }
}

@media (max-width: 768px) {
  .ai-header {
    align-items: stretch;
  }

  .ai-header-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .analysis-mode-select {
    width: 100%;
  }

  .report-card {
    min-height: auto;
    padding: 20px;
  }
}
</style>