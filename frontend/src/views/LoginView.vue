<template>
  <div class="login-page">
    <section class="login-showcase">
      <div class="login-eyebrow">Volatility Analysis Platform</div>
      <h1 class="login-title">股票波动率分析工具</h1>
      <p class="login-copy">
        基于真实股票行情数据、波动率计算模型、评估模型与预测结果，提供统一的分析与结果查看入口。
      </p>

      <div class="login-summary">
        <div class="login-summary-item">
          <span>数据源</span>
          <strong>AKShare / Excel</strong>
        </div>
        <div class="login-summary-item">
          <span>工具功能</span>
          <strong>导入 / 计算 / 评估 / 预测 / AI 报告</strong>
        </div>
      </div>
    </section>

    <section class="login-card section-card">
      <div class="login-card-header">
        <el-segmented v-model="mode" :options="modeOptions" class="login-mode" />
        <h2 class="login-card-title">{{ isRegisterMode ? '创建账号' : '欢迎登录' }}</h2>

      </div>

      <el-form :model="form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item v-if="isRegisterMode">
          <el-input v-model="form.nickname" placeholder="昵称" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-form-item v-if="isRegisterMode">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="确认密码" />
        </el-form-item>
        <el-button type="primary" class="kraken-button login-submit" :loading="loading" @click="submit">
          {{ isRegisterMode ? '注册并进入' : '登录系统' }}
        </el-button>
      </el-form>

    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api/services'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const mode = ref<'login' | 'register'>('login')
const modeOptions = [
  { label: '登录', value: 'login' },
  { label: '注册', value: 'register' },
]
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: '',
})
const isRegisterMode = computed(() => mode.value === 'register')

const getRedirect = () => (
  typeof route.query.redirect === 'string'
    && route.query.redirect.startsWith('/')
    && !route.query.redirect.startsWith('/login')
    ? route.query.redirect
    : '/dashboard'
)

const validateForm = () => {
  const username = form.username.trim()
  if (!username) {
    ElMessage.warning('请输入用户名')
    return false
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return false
  }
  if (isRegisterMode.value && username.length < 3) {
    ElMessage.warning('用户名至少 3 个字符')
    return false
  }
  if (isRegisterMode.value && form.password.length < 6) {
    ElMessage.warning('密码至少 6 个字符')
    return false
  }
  if (isRegisterMode.value && form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return false
  }
  return true
}

const submit = async () => {
  if (!validateForm()) {
    return
  }

  loading.value = true
  try {
    const payload = {
      username: form.username.trim(),
      password: form.password,
    }
    const response = isRegisterMode.value
      ? await api.register({ ...payload, nickname: form.nickname.trim() || undefined })
      : await api.login(payload)
    authStore.setSession(response.data.token, response.data.user)
    ElMessage.success(isRegisterMode.value ? '注册成功' : '登录成功')
    router.push(getRedirect())
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(360px, 440px);
  gap: 24px;
  align-items: stretch;
  padding: 28px;
}

.login-showcase {
  display: grid;
  align-content: center;
  gap: 18px;
  padding: 44px 48px;
  border-radius: 32px;
  background:
    radial-gradient(circle at top left, rgba(113, 50, 245, 0.2), transparent 26%),
    linear-gradient(180deg, #161422 0%, #101114 100%);
  color: #ffffff;
  box-shadow: var(--kraken-shadow);
}

.login-eyebrow {
  font-size: 12px;
  line-height: 1.33;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.72);
}

.login-title {
  margin: 0;
  font-family: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  font-size: clamp(32px, 4vw, 42px);
  line-height: 1.14;
  letter-spacing: -0.03em;
  font-weight: 700;
}

.login-copy {
  margin: 0;
  max-width: 520px;
  font-size: 16px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.72);
}

.login-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 8px;
}

.login-summary-item {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.login-summary-item span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.62);
}

.login-summary-item strong {
  font-size: 16px;
  line-height: 1.5;
  font-weight: 600;
  color: #ffffff;
}

.login-card {
  display: grid;
  align-content: center;
  padding: 38px;
}

.login-card-header {
  margin-bottom: 18px;
}

.login-mode {
  width: 100%;
  margin-bottom: 22px;
}

.login-card-title {
  margin: 0;
  font-family: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  font-size: 36px;
  line-height: 1.12;
  letter-spacing: -0.03em;
  font-weight: 700;
}

.login-card-copy {
  margin: 12px 0 0;
  color: #686b82;
  line-height: 1.65;
}

.login-submit {
  width: 100%;
}

.login-account-tip {
  margin-top: 16px;
  color: #686b82;
  font-size: 14px;
}

@media (max-width: 1100px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .login-page {
    padding: 16px;
  }

  .login-showcase,
  .login-card {
    padding: 24px;
  }

  .login-title {
    font-size: 30px;
  }

  .login-copy {
    font-size: 15px;
  }
}
</style>
