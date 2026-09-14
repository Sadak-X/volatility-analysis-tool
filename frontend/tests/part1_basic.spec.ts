import { test, expect } from '@playwright/test';
import path from 'node:path';
import { login } from './helpers';

// ================= 1. 登录与路由守卫 =================
test.describe('登录与路由守卫', () => {
    test('TC-FE-001: 正确账号密码成功登录', async ({ page }) => {
        await page.goto('/login');
        await page.getByPlaceholder('用户名').fill('admin');
        await page.getByPlaceholder('密码').fill('admin123');
        await page.getByRole('button', { name: '登录系统' }).click();
        await expect(page).toHaveURL(/.*dashboard/);
        const token = await page.evaluate(() => localStorage.getItem('vola-token'));
        expect(token).not.toBeNull();
    });

    test('TC-FE-002: 错误账号或密码登录拦截', async ({ page }) => {
        await page.goto('/login');
        await page.getByPlaceholder('用户名').fill('admin');
        await page.getByPlaceholder('密码').fill('wrongpassword');
        await page.getByRole('button', { name: '登录系统' }).click();
        await expect(page).toHaveURL(/.*login/);
        await expect(page.locator('.el-message--error')).toBeVisible();
    });

    test('TC-FE-003: 未登录访问受保护页面拦截', async ({ page, context }) => {
        await page.goto('/login');
        await context.clearCookies();
        await page.evaluate(() => window.localStorage.clear());

        await page.goto('/dashboard');
        await expect(page).toHaveURL(/.*login/);
        await expect(page).toHaveURL(/redirect=/);
    });
});

// ================= 2. 全局布局与仪表盘 =================
test.describe('全局布局与仪表盘', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    test('TC-FE-004: AppShell 整体布局渲染', async ({ page }) => {
        await expect(page.locator('.sidebar, aside')).toBeVisible();
        await expect(page.locator('text=当前用户').first()).toBeVisible();
    });

    test('TC-FE-005: Dashboard 首页数据看板加载', async ({ page }) => {
        await page.goto('/dashboard');
        await expect(page.locator('canvas').first()).toBeVisible();
    });

    test('TC-FE-006: TrendChart 组件正常挂载渲染', async ({ page }) => {
        await page.goto('/dashboard');
        const chart = page.locator('canvas').first();
        await chart.waitFor({ state: 'visible' });
        await chart.hover({ position: { x: 200, y: 100 } });
        await expect(page.locator('div[style*="position: absolute"]').first()).toBeVisible({ timeout: 3000 });
    });

    test('TC-FE-007: 图表窗口自适应缩放 ', async ({ page }) => {
        await page.goto('/dashboard');
        const chart = page.locator('canvas').first();
        await chart.waitFor({ state: 'visible' });
        await page.setViewportSize({ width: 800, height: 600 });
        await page.waitForTimeout(1000);
        // 【严格断言】期望窗口缩小后图表依然可见，不再使用 expect.soft
        await expect(page.locator('canvas').first()).toBeVisible();
    });
});

// ================= 3. 数据导入 =================
test.describe('数据导入模块', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        await page.goto('/data/import');
    });

    test('TC-FE-008: 下载 Excel 导入模板', async ({ page }) => {
        await page.getByRole('button', { name: '上传 Excel 导入' }).click();
        const downloadPromise = page.waitForEvent('download');
        await page.getByRole('button', { name: /下载模板/ }).click();
        const download = await downloadPromise;
        expect(download.suggestedFilename()).toContain('.xls');
    });

    test('TC-FE-009: 正常 Excel 文件上传解析', async ({ page }) => {
        await page.getByRole('button', { name: '上传 Excel 导入' }).click();
        const filePath = path.resolve(process.cwd(), 'tests/fixtures/test_100.xlsx');
        await page.locator('input[type="file"]').setInputFiles(filePath);
        await expect(page.locator('.el-message--success')).toBeVisible({ timeout: 15000 });
    });

    test('TC-FE-010: 非 Excel 文件格式拦截', async ({ page }) => {
        await page.getByRole('button', { name: '上传 Excel 导入' }).click();
        const filePath = path.resolve(process.cwd(), 'tests/fixtures/dummy.txt');

        let uploadRequestSent = false;
        page.on('request', request => {
            if (request.url().includes('/ingest/excel')) uploadRequestSent = true;
        });

        await page.locator('input[type="file"]').setInputFiles(filePath);
        await expect(page.locator('.el-message--error')).toBeVisible();
        expect(uploadRequestSent).toBeFalsy();
    });
});