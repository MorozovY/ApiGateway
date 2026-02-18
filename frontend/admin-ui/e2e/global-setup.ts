import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

// ESM-совместимый аналог __dirname
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

/**
 * Загрузка переменных из файла .env.e2e (если существует).
 * Поддерживает формат KEY=VALUE (строки с # игнорируются).
 */
function loadEnvFile(): void {
  const envFilePath = path.resolve(__dirname, '../.env.e2e')
  if (!fs.existsSync(envFilePath)) {
    return
  }

  const content = fs.readFileSync(envFilePath, 'utf-8')
  for (const line of content.split('\n')) {
    const trimmed = line.trim()
    // Пропускаем комментарии и пустые строки
    if (!trimmed || trimmed.startsWith('#')) continue

    const eqIndex = trimmed.indexOf('=')
    if (eqIndex === -1) continue

    const key = trimmed.slice(0, eqIndex).trim()
    const value = trimmed.slice(eqIndex + 1).trim()

    // Не перезаписываем уже установленные переменные окружения
    if (!(key in process.env)) {
      process.env[key] = value
    }
  }
}

/**
 * Конфигурация тестовых пользователей.
 * Создаются в global setup, используются во всех E2E тестах.
 */
const TEST_USERS = [
  { username: 'test-developer', password: 'Test1234!', email: 'test-developer@example.com', role: 'developer' },
  { username: 'test-security',  password: 'Test1234!', email: 'test-security@example.com',  role: 'security'  },
  { username: 'test-admin',     password: 'Test1234!', email: 'test-admin@example.com',     role: 'admin'     },
]

/**
 * Global setup для Playwright.
 *
 * Создаёт тестовых пользователей через API если они ещё не существуют.
 * Для аутентификации использует admin credentials из env переменных.
 */
async function globalSetup(): Promise<void> {
  // Загружаем .env.e2e если он существует
  loadEnvFile()

  const adminUsername = process.env.E2E_ADMIN_USERNAME
  const adminPassword = process.env.E2E_ADMIN_PASSWORD

  if (!adminUsername || !adminPassword) {
    throw new Error(
      'E2E тесты требуют установки переменных окружения:\n' +
      '  E2E_ADMIN_USERNAME — имя пользователя администратора\n' +
      '  E2E_ADMIN_PASSWORD — пароль администратора\n' +
      '\n' +
      'Создайте файл frontend/admin-ui/.env.e2e:\n' +
      '  E2E_ADMIN_USERNAME=admin\n' +
      '  E2E_ADMIN_PASSWORD=<ваш_пароль>'
    )
  }

  const apiBase = 'http://localhost:8081'

  // Шаг 1: Аутентификация под admin
  console.log(`[E2E Setup] Вход под пользователем ${adminUsername}...`)

  const loginResponse = await fetch(`${apiBase}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: adminUsername, password: adminPassword }),
    credentials: 'include',
  })

  if (!loginResponse.ok) {
    const body = await loginResponse.text()
    throw new Error(
      `[E2E Setup] Не удалось войти под ${adminUsername}: ${loginResponse.status}\n${body}`
    )
  }

  // Извлекаем auth cookie из заголовка Set-Cookie
  const setCookieHeader = loginResponse.headers.get('set-cookie')
  if (!setCookieHeader) {
    throw new Error('[E2E Setup] Сервер не вернул cookie после логина')
  }

  // Парсим auth_token из set-cookie заголовка
  const cookieMatch = setCookieHeader.match(/auth_token=([^;]+)/)
  if (!cookieMatch) {
    throw new Error('[E2E Setup] Не найден auth_token в set-cookie заголовке')
  }
  const authCookie = `auth_token=${cookieMatch[1]}`

  console.log('[E2E Setup] Вход выполнен успешно.')

  // Шаг 2: Получение списка существующих пользователей
  const usersResponse = await fetch(`${apiBase}/api/v1/users`, {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': authCookie,
    },
  })

  let existingUsernames: Set<string> = new Set()
  if (usersResponse.ok) {
    const usersData = await usersResponse.json() as { items?: Array<{ username: string }> } | Array<{ username: string }>
    const usersList = Array.isArray(usersData) ? usersData : (usersData.items ?? [])
    existingUsernames = new Set(usersList.map((u) => u.username))
  }

  // Шаг 3: Создание отсутствующих тестовых пользователей
  for (const testUser of TEST_USERS) {
    if (existingUsernames.has(testUser.username)) {
      console.log(`[E2E Setup] Пользователь ${testUser.username} уже существует — пропускаем.`)
      continue
    }

    console.log(`[E2E Setup] Создаём пользователя ${testUser.username} (${testUser.role})...`)

    const createResponse = await fetch(`${apiBase}/api/v1/users`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Cookie': authCookie,
      },
      body: JSON.stringify(testUser),
    })

    if (!createResponse.ok) {
      const body = await createResponse.text()
      // 409 — пользователь уже существует (race condition), считаем успехом
      if (createResponse.status !== 409) {
        throw new Error(
          `[E2E Setup] Не удалось создать пользователя ${testUser.username}: ${createResponse.status}\n${body}`
        )
      }
      console.log(`[E2E Setup] Пользователь ${testUser.username} уже существует (409) — пропускаем.`)
    } else {
      console.log(`[E2E Setup] Пользователь ${testUser.username} создан.`)
    }
  }

  console.log('[E2E Setup] Подготовка завершена.')
}

export default globalSetup
