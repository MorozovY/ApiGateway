// E2E тест: Consumers List — отображение и фильтрация consumers
// Story 14.6: AC3 — Consumer List Tests (2 теста)

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockConsumers } from '../fixtures/api.fixture'

test.describe('Consumers List', () => {
  test.beforeEach(async ({ page }) => {
    // Используем admin role для доступа к Consumers
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('таблица consumers отображает колонки Client ID, Status, Rate Limit, Actions', async ({ page }) => {
    await page.goto('/consumers')

    // Ждём загрузки страницы — заголовок Consumers
    await expect(page.locator('h3').filter({ hasText: /consumers/i })).toBeVisible()

    // Проверяем заголовки колонок
    await expect(page.getByRole('columnheader', { name: 'Client ID' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Status' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Rate Limit' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: 'Actions' })).toBeVisible()

    // Проверяем что mock данные отображаются
    const activeConsumer = mockConsumers.find((c) => c.enabled)
    if (activeConsumer) {
      await expect(page.getByRole('cell', { name: activeConsumer.clientId })).toBeVisible()
    }
  })

  test('поиск по client ID работает', async ({ page }) => {
    await page.goto('/consumers')

    // Ждём загрузки данных — первый consumer должен быть виден
    await expect(page.getByRole('cell', { name: mockConsumers[0].clientId })).toBeVisible()

    // Вводим поиск (ищем 'partner')
    const searchInput = page.getByTestId('consumer-search-input')
    await searchInput.fill('partner')

    // Ждём появления отфильтрованного результата (вместо waitForTimeout)
    await expect(page.getByRole('cell', { name: 'partner-service-001' })).toBeVisible()

    // Другие consumers не должны быть видны
    await expect(page.getByRole('cell', { name: 'mobile-app-backend' })).not.toBeVisible()
  })

  test('фильтр по статусу Active/Disabled работает', async ({ page }) => {
    await page.goto('/consumers')

    // Ждём загрузки данных
    await expect(page.getByRole('cell', { name: mockConsumers[0].clientId })).toBeVisible()

    // Находим колонку Status и кликаем на фильтр
    const statusHeader = page.getByRole('columnheader', { name: 'Status' })
    await statusHeader.locator('.ant-table-filter-trigger').click()

    // Ждём появления dropdown меню фильтра
    const filterDropdown = page.locator('.ant-table-filter-dropdown')
    await expect(filterDropdown).toBeVisible()

    // Выбираем Disabled в dropdown фильтре (не в таблице)
    await filterDropdown.getByText('Disabled').click()

    // Подтверждаем фильтр
    await filterDropdown.getByRole('button', { name: 'OK' }).click()

    // Disabled consumer должен быть виден
    const disabledConsumer = mockConsumers.find((c) => !c.enabled)
    if (disabledConsumer) {
      await expect(page.getByRole('cell', { name: disabledConsumer.clientId })).toBeVisible()
    }

    // Active consumers не должны быть видны
    const activeConsumer = mockConsumers.find((c) => c.enabled)
    if (activeConsumer) {
      await expect(page.getByRole('cell', { name: activeConsumer.clientId })).not.toBeVisible()
    }
  })
})
