// E2E тест: Consumers CRUD — создание, редактирование, деактивация consumers
// Story 14.6: AC4 — Consumer CRUD Tests (3 теста)

import { test, expect } from '@playwright/test'
import { setupMockAuth } from '../fixtures/auth.fixture'
import { setupMockApi, mockConsumers } from '../fixtures/api.fixture'

test.describe('Consumers CRUD', () => {
  test.beforeEach(async ({ page }) => {
    // Используем admin role для доступа к Consumers
    await setupMockAuth(page)
    await setupMockApi(page)
  })

  test('создание consumer показывает модальное окно с секретом', async ({ page }) => {
    await page.goto('/consumers')

    // Ждём загрузки страницы
    await expect(page.locator('h3').filter({ hasText: /consumers/i })).toBeVisible()

    // Нажимаем Create Consumer
    await page.getByTestId('create-consumer-button').click()

    // Проверяем что модальное окно Create Consumer открылось
    await expect(page.getByRole('dialog', { name: 'Create Consumer' })).toBeVisible()

    // Заполняем форму
    await page.getByTestId('consumer-client-id-input').fill('test-new-consumer')
    await page.getByTestId('consumer-description-input').fill('Test consumer description')

    // Нажимаем Create
    await page.getByRole('button', { name: /^create$/i }).click()

    // Должен появиться modal с secret (название "Client Secret")
    const secretDialog = page.getByRole('dialog', { name: 'Client Secret' })
    await expect(secretDialog).toBeVisible()

    // Проверяем наличие кнопки копирования
    await expect(secretDialog.getByRole('button', { name: /copy/i })).toBeVisible()

    // Проверяем что есть кнопка закрытия
    await expect(secretDialog.getByRole('button', { name: /закрыть/i })).toBeVisible()
  })

  test('установка rate limit через модальное окно', async ({ page }) => {
    await page.goto('/consumers')

    // Ждём загрузки данных
    await expect(page.getByRole('cell', { name: mockConsumers[0].clientId })).toBeVisible()

    // Находим строку с первым consumer
    const consumerRow = page.getByRole('row').filter({ hasText: mockConsumers[0].clientId })

    // Нажимаем Set Rate Limit
    await consumerRow.getByRole('button', { name: /set rate limit/i }).click()

    // Проверяем что модальное окно rate limit открылось
    // Заголовок содержит "Rate Limit для <clientId>"
    const rateLimitDialog = page.getByRole('dialog').filter({ hasText: /rate limit/i })
    await expect(rateLimitDialog).toBeVisible()

    // Проверяем наличие кнопки "Set Rate Limit" (submit)
    await expect(rateLimitDialog.getByRole('button', { name: /set rate limit/i })).toBeVisible()

    // Проверяем наличие кнопки Cancel
    await expect(rateLimitDialog.getByRole('button', { name: /cancel/i })).toBeVisible()
  })

  test('деактивация consumer показывает подтверждение', async ({ page }) => {
    await page.goto('/consumers')

    // Находим активный consumer
    const activeConsumer = mockConsumers.find((c) => c.enabled)
    if (!activeConsumer) return

    // Ждём загрузки данных
    await expect(page.getByRole('cell', { name: activeConsumer.clientId })).toBeVisible()

    const consumerRow = page.getByRole('row').filter({ hasText: activeConsumer.clientId })

    // Нажимаем Disable
    await consumerRow.getByRole('button', { name: /disable/i }).click()

    // Должен появиться Popconfirm с текстом о деактивации
    await expect(page.getByText(/деактивировать consumer/i)).toBeVisible()

    // Подтверждаем
    await page.getByRole('button', { name: /да/i }).click()

    // После деактивации статус должен измениться на Disabled
    await expect(consumerRow.getByText('Disabled')).toBeVisible()
  })
})
