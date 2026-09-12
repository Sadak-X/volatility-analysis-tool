import { Page, expect } from '@playwright/test';

export async function login(page: Page) {
    await page.goto('/login');
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('admin123');
    await page.getByRole('button', { name: '登录系统' }).click();
    await expect(page).toHaveURL(/.*dashboard/);
}