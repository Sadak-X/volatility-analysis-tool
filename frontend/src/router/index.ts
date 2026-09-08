import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import DataImportView from '../views/DataImportView.vue'
import TaskListView from '../views/TaskListView.vue'
import AnalysisDetailView from '../views/AnalysisDetailView.vue'
import AssessmentDetailView from '../views/AssessmentDetailView.vue'
import ForecastDetailView from '../views/ForecastDetailView.vue'
import AiAnalysisView from '../views/AiAnalysisView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: DashboardView },
    { path: '/data/import', component: DataImportView },
    { path: '/task/list', component: TaskListView },
    { path: '/analysis/detail/:taskNo', component: AnalysisDetailView, props: true },
    { path: '/assessment/detail/:taskNo', component: AssessmentDetailView, props: true },
    { path: '/forecast/list', redirect: '/task/list' },
    { path: '/forecast/detail/:taskNo', component: ForecastDetailView, props: true },
    { path: '/ai/analysis/:taskNo', component: AiAnalysisView, props: true },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  const isPublicRoute = Boolean(to.meta.public)

  if (!authStore.isAuthenticated) {
    if (authStore.token || authStore.user) {
      authStore.clear()
    }
    if (isPublicRoute) return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.path === '/login') {
    return '/dashboard'
  }

  return true
})

export default router
