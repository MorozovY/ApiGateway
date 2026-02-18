import { test, expect } from '@playwright/test'
import { login, logout } from './helpers/auth'

/**
 * Epic 2 — Authentication & User Management.
 * Проверяет вход/выход, ролевую защиту маршрутов и создание пользователей.
 */
test.describe('Epic 2: Authentication & User Management', () => {
  test('Developer успешно логинится и видит Dashboard', async ({ page }) => {
    // login() переходит на /dashboard → редирект на /login → логинится → возвращается на /dashboard
    await login(page, 'test-developer', 'Test1234!', '/dashboard')

    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page.locator('text=Admin Panel')).toBeVisible()
  })

  test('Неверный пароль показывает сообщение об ошибке', async ({ page }) => {
    await page.goto('/login')
    await page.waitForURL(/\/login/)

    await page.locator('[data-testid="username-input"]').fill('test-developer')
    await page.locator('[data-testid="password-input"]').fill('wrong-password-123')
    await page.locator('[data-testid="login-button"]').click()

    // Сообщение об ошибке должно появиться
    await expect(page.locator('[data-testid="login-error"]')).toBeVisible()

    // Остаёмся на странице логина
    await expect(page).toHaveURL(/\/login/)
  })

  test('Logout завершает сессию и перенаправляет на /login', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!', '/dashboard')
    await expect(page).toHaveURL(/\/dashboard/)

    await logout(page)

    await expect(page).toHaveURL(/\/login/)
    await expect(page.locator('[data-testid="login-button"]')).toBeVisible()
  })

  test('Developer не может открыть /users (редирект на dashboard)', async ({ page }) => {
    // login() с landingUrl='/users' → /users → /login (returnUrl=/users) → логинится →
    // React Router пытается перейти на /users → ProtectedRoute(requiredRole='admin') →
    // developer не admin → редирект на /dashboard
    await login(page, 'test-developer', 'Test1234!', '/users')

    // Developer должен быть перенаправлен на dashboard, а не на /users
    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page).not.toHaveURL(/\/users/)
  })

  test('Admin создаёт нового пользователя через UI', async ({ page }) => {
    // Admin имеет доступ к /users — returnUrl сработает корректно
    await login(page, 'test-admin', 'Test1234!', '/users')

    await expect(page.locator('h3:has-text("Users")')).toBeVisible()

    // Открываем модальное окно создания пользователя
    await page.locator('button:has-text("Add User")').click()
    await expect(page.locator('.ant-modal-title:has-text("Add User")')).toBeVisible()

    // Заполняем форму уникальными данными
    const timestamp = Date.now()
    const newUsername = `e2e-user-${timestamp}`

    await page.locator('.ant-modal').locator('input[placeholder="Введите username"]').fill(newUsername)
    await page.locator('.ant-modal').locator('input[placeholder="Введите email"]').fill(`${newUsername}@example.com`)
    await page.locator('.ant-modal').locator('input[placeholder="Введите пароль"]').fill('Test1234!')

    // Сохраняем
    await page.locator('.ant-modal-footer button:has-text("Create")').click()

    // Модальное окно закрывается
    await expect(page.locator('.ant-modal-title:has-text("Add User")')).not.toBeVisible({ timeout: 10_000 })

    // Увеличиваем размер страницы таблицы, чтобы найти пользователя
    // (система содержит >10 пользователей, новый может быть на 2-й странице)
    await page.locator('.ant-pagination-options .ant-select-selector').click()
    await page.locator('.ant-select-dropdown').waitFor({ state: 'visible' })
    await page.getByRole('option', { name: '50 / page' }).click()
    await page.locator('.ant-select-dropdown').waitFor({ state: 'hidden' })

    // Новый пользователь появляется в таблице (exact: true — только username ячейка, не email)
    await expect(page.getByText(newUsername, { exact: true })).toBeVisible({ timeout: 10_000 })
  })
})
