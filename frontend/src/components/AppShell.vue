<template>
  <div class="shell">
    <aside class="shell-sidebar">
      <div class="shell-sidebar-top">
        <div class="shell-brand-block">
          <div class="shell-brand-eyebrow">Vola Scope Platform</div>
          <div class="shell-brand-title">股票波动率分析工具</div>
          <p class="shell-brand-description">
            使用统一的任务流，展示真实股票行情、波动率分析、评估、预测与 AI 报告
          </p>
        </div>
      </div>

      <el-menu
        :default-active="active"
        background-color="transparent"
        text-color="rgba(255,255,255,0.72)"
        active-text-color="#ffffff"
        class="shell-menu"
      >
 <el-menu-item index="/dashboard" @click="navigateTo('/dashboard')">市场概览</el-menu-item>
  <el-menu-item index="/data/import" @click="navigateTo('/data/import')">数据导入</el-menu-item>
  <el-menu-item index="/task/list" @click="navigateTo('/task/list')">任务中心</el-menu-item>

        <!-- 任务流程（当从任务进入详情后显示） -->
        <div v-if="taskFlowVisible" class="shell-menu-section">
          <div class="shell-menu-label">任务流程</div>
          <el-menu-item
            v-for="step in taskFlowSteps"
            :key="step.index"
            :index="step.index"
            :disabled="step.disabled"
            @click="goToStep(step)"
          >
            <span class="step-indicator" :class="step.statusClass"></span>
            {{ step.label }}
          </el-menu-item>
        </div>
      </el-menu>

      <div class="shell-profile">
        <div class="shell-profile-label">当前用户</div>
        <div class="shell-profile-name">{{ userLabel }}</div>
        <el-button class="kraken-button kraken-button--soft shell-logout" @click="logout">退出登录</el-button>
      </div>
    </aside>

    <main class="shell-main">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const active = computed(() => {
  const path = route.path
  

  if (path.startsWith('/ai/analysis')) return `/ai/analysis/${route.params.taskNo}`
  if (path.startsWith('/analysis/detail')) return `/analysis/detail/${route.params.taskNo}`
  if (path.startsWith('/assessment/detail')) return `/assessment/detail/${route.params.taskNo}`
  if (path.startsWith('/forecast/detail')) return `/forecast/detail/${route.params.taskNo}`
  if (path.startsWith('/task') || path.startsWith('/forecast')) return '/task/list'
  if (path.startsWith('/data')) return '/data/import'
  return '/dashboard'
})

const userLabel = computed(() => authStore.user?.nickname || authStore.user?.username || '未登录')

const logout = () => {
  authStore.clear()
  router.push('/login')
}


const taskFlowVisible = computed(() => {
  return route.path.startsWith('/analysis/') ||
         route.path.startsWith('/assessment/') ||
         route.path.startsWith('/forecast/') ||
         route.path.startsWith('/ai/')
})

const currentTaskNo = computed(() => {
  return route.params.taskNo as string || ''
})

const taskFlowSteps = computed(() => {
  const taskNo = currentTaskNo.value
  return [
    {
      index: `/analysis/detail/${taskNo}`,
      label: '波动率计算',
      disabled: false,
      statusClass: getStepStatus('analysis'),
    },
    {
      index: `/assessment/detail/${taskNo}`,
      label: '波动率评估',
      disabled: false,
      statusClass: getStepStatus('assessment'),
    },
    {
      index: `/forecast/detail/${taskNo}`,
      label: '波动率预测',
      disabled: false,
      statusClass: getStepStatus('forecast'),
    },
    {
      index: `/ai/analysis/${taskNo}`,
      label: 'AI 分析报告',
      disabled: false,
      statusClass: getStepStatus('ai'),
    },
  ]
})

const navigateTo = (path: string) => {
  router.push(path)
}

const getStepStatus = (type: string) => {
  const path = route.path
  if (type === 'ai' && path.startsWith('/ai/analysis')) return 'step-active'
  if (type === 'analysis' && path.startsWith('/analysis/detail')) return 'step-active'
  if (type === 'assessment' && path.startsWith('/assessment/detail')) return 'step-active'
  if (type === 'forecast' && path.startsWith('/forecast/detail')) return 'step-active'
  return 'step-pending'
}

const goToStep = (step: any) => {
  router.push(step.index)
}
</script>


<style scoped>
.shell-sidebar-top {
  display: grid;
  gap: 18px;
}

.shell-brand-chip {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.shell-brand-block {
  display: grid;
  gap: 10px;
}

.shell-brand-eyebrow {
  font-size: 12px;
  line-height: 1.33;
  color: rgba(255, 255, 255, 0.6);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.shell-brand-title {
  font-family: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  font-size: 30px;
  line-height: 1.1;
  letter-spacing: -0.04em;
  font-weight: 700;
  color: #ffffff;
}

.shell-brand-description {
  margin: 0;
  color: rgba(255, 255, 255, 0.68);
  font-size: 14px;
  line-height: 1.6;
}

.shell-menu {
  flex: 1;
}

.shell-profile {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 20px 18px 18px;
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.075), rgba(255, 255, 255, 0.035)),
    rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.09);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 18px 36px rgba(0, 0, 0, 0.22);
  overflow: hidden;
}

.shell-profile::before {
  content: "";
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.22), transparent 72%);
}

.shell-profile::after {
  content: "";
  position: absolute;
  top: -42px;
  right: -36px;
  width: 120px;
  height: 120px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(113, 50, 245, 0.24), transparent 68%);
  pointer-events: none;
}

.shell-profile-label {
  font-size: 12px;
  line-height: 1.33;
  color: rgba(255, 255, 255, 0.54);
  letter-spacing: 0.06em;
}

.shell-profile-name {
  font-size: 28px;
  line-height: 1.2;
  letter-spacing: -0.04em;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 6px 20px rgba(0, 0, 0, 0.18);
}

.shell-profile-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  color: rgba(220, 255, 235, 0.88);
  font-size: 12px;
  background: rgba(20, 158, 97, 0.14);
  border: 1px solid rgba(91, 214, 147, 0.18);
}

.shell-profile-meta::before {
  content: "";
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #34d399;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.14);
}

.shell-logout {
  width: 100%;
  min-height: 48px;
  margin-top: 10px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #ffffff;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.12), rgba(255, 255, 255, 0.08)),
    rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition:
    transform 180ms ease,
    background 180ms ease,
    box-shadow 180ms ease;
}

.shell-logout:hover {
  color: #ffffff;
  background:
    linear-gradient(180deg, rgba(113, 50, 245, 0.46), rgba(113, 50, 245, 0.34)),
    rgba(255, 255, 255, 0.08);
  box-shadow:
    0 10px 24px rgba(76, 36, 173, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  transform: translateY(-1px);
}


.shell-menu-section {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.shell-menu-label {
  padding: 0 20px 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.42);
}
.shell-sidebar {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
  padding: 28px 20px 22px;
  background:
    radial-gradient(circle at top, rgba(113, 50, 245, 0.32), transparent 28%),
    linear-gradient(180deg, #141320 0%, #101114 100%);
  color: #ffffff;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.step-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 10px;
  background: rgba(255, 255, 255, 0.2);
  transition: all 0.2s;
}

.step-indicator.step-active {
  background: #21b77a;
  box-shadow: 0 0 6px rgba(33, 183, 122, 0.5);
}

.step-indicator.step-pending {
  background: rgba(255, 255, 255, 0.2);
}
</style>
