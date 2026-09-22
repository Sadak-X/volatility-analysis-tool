import { test, expect } from '@playwright/test';
import { login } from './helpers';

const MOCK_TASK_NO = 'TASK_TEST_2026';

// 统一的 Mock 数据结构（兼容扁平与包装两种格式）
const buildMockBody = (payload: any) => JSON.stringify({
    ...payload,          // 扁平结构
    code: 200,
    message: 'success',
    data: payload,       // data 包装结构
});

test.beforeEach(async ({ page }) => {
    await login(page);
});

test.describe('AI 前端应用模块测试', () => {

    test('TC-AI-13: 功能性-AI分析报告正常展示', async ({ page }) => {
        await page.route('**/api/ai/**', async route => {
            const url = route.request().url();
            if (url.includes('.ts') || url.includes('.vue')) return route.continue();
            console.log(`Intercepted AI: ${url}`);

            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: buildMockBody({
                    summaryText: '这是AI生成的摘要信息。',
                    fullReportMd: '## AI分析报告\n这是完整报告的内容。\n- 项目一\n- 项目二',
                    suggestedQuestions: [],
                }),
            });
        });

        await page.goto(`/ai/analysis/${MOCK_TASK_NO}`);

        await expect(page.locator('.report-summary')).toHaveText('这是AI生成的摘要信息。', { timeout: 10000 });
        await expect(page.locator('.report-body')).toContainText('AI分析报告');
    });

    test('TC-AI-14: 功能性-智能推荐问题正常生成与交互', async ({ page }) => {
        const mockQuestions = ['支撑位在哪里？', '当前风险大吗？'];

        await page.route('**/api/ai/**', async route => {
            if (route.request().url().includes('.ts')) return route.continue();
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: buildMockBody({
                    summaryText: 'AI摘要',
                    fullReportMd: '# 报告',
                    suggestedQuestions: mockQuestions,
                }),
            });
        });

        await page.goto(`/ai/analysis/${MOCK_TASK_NO}`);

        const suggestedButtons = page.locator('.suggested-questions button');
        await expect(suggestedButtons).toHaveCount(2, { timeout: 10000 });

        await suggestedButtons.first().click();
        await expect(page.locator('.input-area textarea')).toHaveValue('支撑位在哪里？');
    });

    test('TC-AI-15: 功能性-波动率计算分析结论正常展示', async ({ page }) => {
        await page.route('**/api/analysis/**', async route => {
            if (route.request().url().includes('.ts')) return route.continue();
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: buildMockBody({
                    analysisReady: true,
                    taskStatus: 'AI_DONE',
                    insight: {
                        title: '计算分析结论',
                        summary: '当前历史波动率为20.18%，处于合理区间。',
                        bullets: ['历史波动率20.18%', '隐含波动率26.56%'],
                    },
                }),
            });
        });

        await page.goto(`/analysis/detail/${MOCK_TASK_NO}`);

        const insightCard = page.locator('.insight-card');
        await expect(insightCard).toBeVisible({ timeout: 10000 });
        await expect(insightCard.locator('h3')).toHaveText('计算分析结论');
        await expect(insightCard.locator('li')).toHaveCount(2);
    });

    test('TC-AI-16: 功能性-波动率评估解释正常展示', async ({ page }) => {
        await page.route('**/api/assessment/**', async route => {
            if (route.request().url().includes('.ts')) return route.continue();
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: buildMockBody({
                    scoreTotal: 37.93,
                    riskLevel: 'MEDIUM',
                    insight: {
                        title: '波动率评估解释',
                        summary: '结合唐奇安通道，当前处于中轨下方，短期偏弱。',
                        bullets: ['上轨 1338.86', '下轨 1263.01'],
                    },
                }),
            });
        });

        await page.goto(`/assessment/detail/${MOCK_TASK_NO}`);

        const explainBlock = page.locator('.assessment-explain');
        await expect(explainBlock).toBeVisible({ timeout: 10000 });
        await expect(explainBlock.locator('h2')).toHaveText('波动率评估解释');
        await expect(explainBlock.locator('li')).toHaveCount(2);
    });

    test('TC-AI-17: 功能性-波动率预测说明正常展示', async ({ page }) => {
        await page.route('**/api/forecast/**', async route => {
            if (route.request().url().includes('.ts')) return route.continue();
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: buildMockBody({
                    forecastReady: true,
                    taskStatus: 'FORECASTED',
                    predictVolatility: 0.2069,
                    ciLower: 0,
                    ciUpper: 10.7228,
                    insight: {
                        title: '预测说明',
                        summary: '基于EWMA模型，预测波动率为20.69%。',
                        bullets: ['采用EWMA模型', '置信区间较宽'],
                    },
                }),
            });
        });

        await page.goto(`/forecast/detail/${MOCK_TASK_NO}`);

        const forecastExplain = page.locator('.forecast-explain');
        await expect(forecastExplain).toBeVisible({ timeout: 10000 });
        await expect(forecastExplain.locator('h3')).toHaveText('预测说明');
        await expect(forecastExplain.locator('li')).toHaveCount(2);
    });

    test('TC-AI-18: 功能性-AI追问对话功能正常运作', async ({ page }) => {
        await page.route('**/api/ai/**', async route => {
            if (route.request().url().includes('.ts')) return route.continue();
            const method = route.request().method();

            if (method === 'POST') {
                await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: buildMockBody({
                        answer: '这是AI针对您追问给出的解答。',
                        suggestedQuestions: []
                    }),
                });
            } else {
                await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: buildMockBody({
                        summaryText: '摘要',
                        fullReportMd: '# 报告',
                        suggestedQuestions: []
                    }),
                });
            }
        });

        await page.goto(`/ai/analysis/${MOCK_TASK_NO}`);

        const textarea = page.locator('.input-area textarea');
        await textarea.fill('支撑位在哪里？');
        await page.locator('.send-button').click();

        const userMessage = page.locator('.message-user');
        await expect(userMessage.last()).toContainText('你', { timeout: 10000 });
        await expect(userMessage.last()).toContainText('支撑位在哪里？');

        const aiMessage = page.locator('.message-assistant');
        await expect(aiMessage.last()).toContainText('AI 助手');
        await expect(aiMessage.last()).toContainText('这是AI针对您追问给出的解答。');
    });

});