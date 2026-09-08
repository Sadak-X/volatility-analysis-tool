import { http, type ApiResponse } from './client'

const AI_REQUEST_TIMEOUT = 300000

export const api = {
  login: (payload: { username: string; password: string }) =>
    http.post<never, ApiResponse<{ token: string; user: any }>>('/auth/login', payload),
  register: (payload: { username: string; password: string; nickname?: string }) =>
    http.post<never, ApiResponse<{ token: string; user: any }>>('/auth/register', payload),
  me: () => http.get<never, ApiResponse<any>>('/auth/me'),
  systemConfig: () => http.get<never, ApiResponse<any>>('/system/config'),
  confidenceLevels: () => http.get<never, ApiResponse<any[]>>('/dict/confidence-level'),
  dashboard: (scope: string) =>
    http.get<never, ApiResponse<any[]>>('/dashboard/high-volatility', { params: { scope, limit: 10 } }),
  stockPage: (params: Record<string, any>) =>
    http.get<never, ApiResponse<any>>('/stock/page', { params }),
  stockSearch: (keyword = '') =>
    http.get<never, ApiResponse<any[]>>('/stock/search', { params: { keyword } }),
  createTask: (payload: Record<string, any>) =>
    http.post<never, ApiResponse<any>>('/task/create', payload),
  getLatestAiReport: (taskNo: string) =>
    http.get<never, ApiResponse<any>>(`/ai/${taskNo}/latest`),
  batchCreateTask: (payload: Record<string, any>) =>
    http.post<never, ApiResponse<any>>('/task/batch-create', payload),
  uploadExcel: (formData: FormData) =>
    http.post<never, ApiResponse<any>>('/data/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  importStocksFromExcel: (payload: Record<string, any>) =>
    http.post<never, ApiResponse<any>>('/data/import/stocks/excel', payload),
  importStocks: (payload: Record<string, any>) =>
    http.post<never, ApiResponse<any>>('/data/import/stocks', payload),
  deleteTask: (taskNo: string) =>
    http.delete<never, ApiResponse<any>>(`/task/${taskNo}`),
  batchDeleteTasks: (taskNos: string[]) =>
    http.post<never, ApiResponse<any>>('/task/batch-delete', taskNos),
  importExcel: (payload: Record<string, any>) =>
    http.post<never, ApiResponse<any>>('/data/import/excel', payload),
  importMarket: (payload: Record<string, any>) =>
    http.post<never, ApiResponse<any>>('/data/import/market', payload),
  downloadExcelErrors: (taskNo: string) =>
    http.get(`/data/import/${taskNo}/errors/download`, {
      responseType: 'blob',
    }),
  taskPage: (params: Record<string, any>) =>
    http.get<never, ApiResponse<any>>('/task/page', { params }),
  taskDetail: (taskNo: string) =>
    http.get<never, ApiResponse<any>>(`/task/${taskNo}`),
  rerunTask: (taskNo: string) =>
    http.post<never, ApiResponse<any>>(`/task/${taskNo}/rerun`),
  analysisOverview: (taskNo: string) =>
    http.get<never, ApiResponse<any>>(`/analysis/${taskNo}/overview`),
  analysisTrend: (taskNo: string) =>
    http.get<never, ApiResponse<any>>(`/analysis/${taskNo}/trend`),
  assessmentDetail: (taskNo: string) =>
    http.get<never, ApiResponse<any>>(`/assessment/${taskNo}/detail`),
  forecastPage: (params: Record<string, any>) =>
    http.get<never, ApiResponse<any>>('/forecast/page', { params }),
  forecastDetail: (taskNo: string) =>
    http.get<never, ApiResponse<any>>(`/forecast/${taskNo}/detail`),
  forecastItems: (taskNo: string) =>
    http.get<never, ApiResponse<any>>(`/forecast/${taskNo}/items`),
  generateAi: (taskNo: string, payload = { analysisMode: 'BRIEF' }) =>
    http.post<never, ApiResponse<any>>(`/ai/${taskNo}/generate`, payload, { timeout: AI_REQUEST_TIMEOUT }),
  followupAi: (taskNo: string, payload: { question: string; history?: Array<{ role: string; content: string }> }) =>
    http.post<never, ApiResponse<any>>(`/ai/${taskNo}/followup`, payload, { timeout: AI_REQUEST_TIMEOUT }),
  downloadAiReport: (taskNo: string) =>
    http.get(`/report/${taskNo}/download`, {
      responseType: 'blob',
    }),
  downloadAiReportPdf: (taskNo: string) =>
    http.get(`/report/${taskNo}/download/pdf`, {
      responseType: 'blob',
    }),
}
