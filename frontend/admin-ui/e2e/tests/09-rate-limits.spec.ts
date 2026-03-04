// E2E тест: Rate Limits — управление политиками ограничения
// Story 13.15: P2 тест — список и создание rate limit политик

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockRateLimits } from '../fixtures/api.fixture'

test.describe('Rate Limits', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('страница rate limits доступна для admin', async ({ page }) => {
    await page.goto('/rate-limits')

    // Заголовок страницы (Story 16.1 — локализация)
    await expect(page.getByRole('heading', { name: 'Лимиты трафика' })).toBeVisible()
  })

  test('таблица отображает список политик', async ({ page }) => {
    await page.goto('/rate-limits')

    // Проверяем что таблица загружена (есть хотя бы одна строка с данными)
    const table = page.locator('.ant-table-tbody')
    await expect(table).toBeVisible()

    // Первая политика из mock данных видна в таблице
    await expect(table.getByText(mockRateLimits[0].name)).toBeVisible()
  })

  test('таблица содержит колонки Name, Requests/sec', async ({ page }) => {
    await page.goto('/rate-limits')

    // Проверяем заголовки колонок
    await expect(page.getByRole('columnheader', { name: /название/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /запросов/i })).toBeVisible()
  })

  test('навигация на Rate Limits через Sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    const sidebar = page.locator('.ant-layout-sider')
    await sidebar.getByText('Лимиты').click()

    await expect(page).toHaveURL('/rate-limits')
  })

  test('кнопка создания новой политики видна', async ({ page }) => {
    await page.goto('/rate-limits')

    // Кнопка Новый лимит
    await expect(page.getByRole('button', { name: /новый лимит/i })).toBeVisible()
  })
})
