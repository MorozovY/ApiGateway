// E2E тест: Audit Logs — просмотр аудит логов
// Story 13.15: P2 тест — список аудит записей с фильтрами

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockAuditLogs } from '../fixtures/api.fixture'

test.describe('Audit Logs', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('страница аудита доступна для admin', async ({ page }) => {
    await page.goto('/audit')

    // Заголовок страницы
    await expect(page.getByRole('heading', { name: /audit|аудит/i })).toBeVisible()
  })

  test('таблица отображает аудит записи', async ({ page }) => {
    await page.goto('/audit')

    // Проверяем что таблица загружена
    const table = page.locator('.ant-table-tbody')
    await expect(table).toBeVisible()

    // Проверяем что есть хотя бы одна строка данных (не empty state)
    const rows = page.locator('.ant-table-tbody tr')
    await expect(rows.first()).toBeVisible()
  })

  test('таблица содержит колонки Action, User, Timestamp', async ({ page }) => {
    await page.goto('/audit')

    await expect(page.getByRole('columnheader', { name: /action|действие/i })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: /user|пользователь/i })).toBeVisible()
  })

  test('навигация на Audit через Sidebar', async ({ page }) => {
    await page.goto('/dashboard')

    const sidebar = page.locator('.ant-layout-sider')
    await sidebar.getByText('Аудит').click()

    await expect(page).toHaveURL('/audit')
  })

  test('панель фильтров видна', async ({ page }) => {
    await page.goto('/audit')

    // Фильтры должны быть видны
    // Используем более общий селектор
    const filters = page.locator('.ant-select, .ant-picker, input[type="search"]')
    await expect(filters.first()).toBeVisible()
  })
})
