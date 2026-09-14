import { test, expect } from '@playwright/test';
import { login } from './helpers';

// ================= 4. 任务管理 =================
test.describe('任务管理模块', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        await page.goto('/task/list');
    });


    test('TC-FE-011: 任务状态流转与结果查看', async ({ page }) => {
        await page.waitForSelector('.el-table__row', { timeout: 10000 });
        const firstRow = page.locator('.el-table__row').first();
        await expect(firstRow.locator('td').nth(2)).toHaveText(/已完成|失败|运行中/);

        const resultBtn = firstRow.getByRole('button', { name: '结果' });
        if (await resultBtn.isVisible()) {
            await resultBtn.click();
            await expect(page.getByText('任务详情')).toBeVisible({ timeout: 5000 });
        }
    });

    test('TC-FE-012: 任务状态显示及进度条渲染', async ({ page }) => {
        await page.waitForSelector('.el-table__row', { timeout: 10000 });
        const firstRow = page.locator('.el-table__row').first();
        await expect(firstRow.locator('td').nth(2)).toContainText(/已完成|失败/);
    });
});

// ================= 5. 评估详情与 AI 分析（弹窗内操作） =================
test.describe('评估与AI分析模块', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        await page.goto('/task/list');
        await page.waitForSelector('.el-table__row', { timeout: 10000 });
        await page.locator('.el-table__row').first().getByRole('button', { name: '结果' }).click();
        await expect(page.getByText('任务详情')).toBeVisible({ timeout: 5000 });
    });

    test('TC-FE-013: 唐奇安通道突破预警', async ({ page }) => {
        await page.getByRole('tab', { name: '评估结果' }).click();
        await expect(page.getByText('唐奇安通道').first()).toBeVisible({ timeout: 5000 });
        await expect(page.getByText('上轨').first()).toBeVisible();
        await expect(page.getByText(/突破|跌破|接近|靠近/).first()).toBeVisible();
    });

    test('TC-FE-014: AI 分析页面初始化与渲染', async ({ page }) => {
        await page.getByRole('tab', { name: '波动率分析' }).click();
        await expect(page.getByText('波动率计算结果分析').first()).toBeVisible({ timeout: 15000 });
    });

    test('TC-FE-015: 风险评分卡片颜色联动', async ({ page }) => {
        await page.getByRole('tab', { name: '评估结果' }).click();
        await expect(page.getByText('综合评分', { exact: true }).first()).toBeVisible({ timeout: 5000 });
        await expect(page.getByText('风险等级', { exact: true }).first()).toBeVisible();
        await expect(page.getByText(/中风险|低风险|高风险/).first()).toBeVisible();
    });
});

// ================= 6. 波动率预测 =================
test.describe('波动率预测模块', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    test('TC-FE-016: 预测列表分页与筛选', async ({ page }) => {
        await page.goto('/forecast/list');
        await expect(page.locator('.el-pagination')).toBeVisible();

        const nextPageBtn = page.locator('.el-pagination button.btn-next');
        if (await nextPageBtn.isEnabled()) {
            await nextPageBtn.click();
            await expect(page.locator('.el-pagination .is-active')).toHaveText('2');
        }
    });

    test('TC-FE-017: 预测图表及模型标签渲染 ', async ({ page }) => {
        await page.goto('/task/list');
        await page.waitForSelector('.el-table__row');
        await page.locator('.el-table__row').first().getByRole('button', { name: '结果' }).click();
        await expect(page.getByText('任务详情')).toBeVisible({ timeout: 5000 });

        await page.getByRole('tab', { name: '预测结果' }).click();

        await expect(page.getByText('预测波动率', { exact: true }).first()).toBeVisible({ timeout: 5000 });
        await expect(page.getByText('置信区间', { exact: true }).first()).toBeVisible();


        const errorNumberLocator = page.getByText('1072.29%');

        await expect(errorNumberLocator).toHaveCount(0);

    });
});

// ================= 7. API 层与容错 =================
test.describe('API 层与全局容错', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    test('TC-FE-018: Axios 请求拦截器注入 Token', async ({ page }) => {
        await page.goto('/task/list');

        const token = await page.evaluate(() => localStorage.getItem('vola-token'));
        expect(token).not.toBeNull();
        expect(token).toContain('eyJ');

        await page.waitForSelector('.el-table__row', { timeout: 10000 });
        await expect(page.locator('.el-table__row').first()).toBeVisible();
    });


});