
import {defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  retries: 1, // 失败重试1次
  reporter: 'html', // 生成 HTML 测试报告
  use: {
    baseURL: 'http://localhost:5173', // 你的前端地址
    trace: 'on-first-retry', // 失败时保留追踪信息
    screenshot: 'only-on-failure', // 失败时自动截图
  },
  // 自动启动前端服务（如果已经启动，可以注释掉）
  // webServer: {
  //   command: 'npm run dev',
  //   url: 'http://localhost:5173',
  //   reuseExistingServer: !process.env.CI,
  // },
});