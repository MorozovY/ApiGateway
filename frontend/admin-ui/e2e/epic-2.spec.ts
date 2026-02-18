import { test, expect } from '@playwright/test'
import { login, logout } from './helpers/auth'

/**
 * Epic 2 — Authentication & User Management.
 * Проверяет вход/выход, ролевую защиту маршрутов и создание пользователей.
 */
test.describe('Epic 2: Authentication & User Management', () => {
  test('Developer успешно логинится и видит Dashboard', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')

    // После логина должен быть редирект на dashboard
    await expect(page).toHaveURL(/\/dashboard/)

    // Заголовок приложения отображается
    await expect(page.locator('text=Admin Panel')).toBeVisible()
  })

  test('Неверный пароль показывает сообщение об ошибке', async ({ page }) => {
    await page.goto('/login')

    await page.locator('[data-testid="username-input"]').fill('test-developer')
    await page.locator('[data-testid="password-input"]').fill('wrong-password-123')
    await page.locator('[data-testid="login-button"]').click()

    // Сообщение об ошибке должно появиться
    await expect(page.locator('[data-testid="login-error"]')).toBeVisible()

    // Остаёмся на странице логина
    await expect(page).toHaveURL(/\/login/)
  })

  test('Logout завершает сессию и перенаправляет на /login', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')

    // Убеждаемся что вошли
    await expect(page).toHaveURL(/\/dashboard/)

    await logout(page)

    // После выхода — страница логина
    await expect(page).toHaveURL(/\/login/)

    // Форма логина снова отображается
    await expect(page.locator('[data-testid="login-button"]')).toBeVisible()
  })

  test('Developer не может открыть /users (редирект на dashboard)', async ({ page }) => {
    await login(page, 'test-developer', 'Test1234!')

    // Попытка открыть страницу управления пользователями
    await page.goto('/users')

    // Developer не должен иметь доступ — ожидается редирект
    await expect(page).not.toHaveURL(/\/users/)
  })

  test('Admin создаёт нового пользователя через UI', async ({ page }) => {
    await login(page, 'test-admin', 'Test1234!')
    await page.goto('/users')

    // Ожидаем загрузки страницы
    await expect(page.locator('h3:has-text("Users")')).toBeVisible()

    // Открываем модальное окно создания пользователя
    await page.locator('button:has-text("Add User")').click()

    // Ожидаем появления модального окна
    await expect(page.locator('.ant-modal-title:has-text("Add User")')).toBeVisible()

    // Заполняем форму
    const timestamp = Date.now()
    const newUsername = `e2e-user-${timestamp}`

    await page.locator('.ant-modal').locator('input[placeholder="Введите username"]').fill(newUsername)
    await page.locator('.ant-modal').locator('input[placeholder="Введите email"]').fill(`${newUsername}@example.com`)
    await page.locator('.ant-modal').locator('input[placeholder="Введите пароль"]').fill('Test1234!')

    // Сохраняем пользователя
    await page.locator('.ant-modal-footer button:has-text("Create")').click()

    // Модальное окно должно закрыться
    await expect(page.locator('.ant-modal-title:has-text("Add User")')).not.toBeVisible()

    // Новый пользователь должен появиться в таблице
    await expect(page.locator(`text=${newUsername}`)).toBeVisible()
  })
})
