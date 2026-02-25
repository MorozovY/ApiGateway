import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import pg from 'pg'

// ESM-совместимый аналог __dirname
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const { Pool } = pg

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
 * Очистка тестовых данных в БД перед прогоном E2E тестов.
 *
 * Удаляет все данные с E2E паттернами:
 * - routes: path LIKE '/e2e-%'
 * - rate_limits: name LIKE 'e2e-%'
 * - users: username LIKE 'e2e-%'
 *
 * Порядок удаления учитывает FK constraints:
 * 1. audit_logs → users (ON DELETE RESTRICT)
 * 2. routes.approved_by/rejected_by → users
 * 3. rate_limits.created_by → users (NOT NULL)
 * 4. routes → rate_limits (rate_limit_id)
 *
 * Не удаляет:
 * - test-developer, test-security, test-admin (системные тестовые пользователи)
 */
async function cleanupTestData(): Promise<void> {
  const databaseUrl = process.env.DATABASE_URL || 'postgresql://gateway:gateway@localhost:5432/gateway'

  const pool = new Pool({ connectionString: databaseUrl })

  try {
    console.log('[E2E Cleanup] Очистка тестовых данных...')

    // Удаляем route_versions если существует (route_versions -> routes FK)
    // Игнорируем ошибку 42P01 (table does not exist), логируем другие
    await pool.query(`
      DELETE FROM route_versions
      WHERE route_id IN (SELECT id FROM routes WHERE path LIKE '/e2e-%')
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Cleanup] Ошибка при удалении route_versions:', err)
      }
    })

    // Удаляем маршруты с E2E паттерном в path
    const routesResult = await pool.query(`DELETE FROM routes WHERE path LIKE '/e2e-%'`)
    console.log(`[E2E Cleanup] Удалено маршрутов (по path): ${routesResult.rowCount ?? 0}`)

    // Удаляем маршруты которые ссылаются на E2E политики (по rate_limit_id)
    const routesByPolicyResult = await pool.query(`
      DELETE FROM routes
      WHERE rate_limit_id IN (SELECT id FROM rate_limits WHERE name LIKE 'e2e-%')
    `)
    console.log(`[E2E Cleanup] Удалено маршрутов (по политике): ${routesByPolicyResult.rowCount ?? 0}`)

    // Удаляем политики rate limit с E2E паттерном
    const policiesResult = await pool.query(`DELETE FROM rate_limits WHERE name LIKE 'e2e-%'`)
    console.log(`[E2E Cleanup] Удалено политик: ${policiesResult.rowCount ?? 0}`)

    // --- Очистка E2E пользователей (username LIKE 'e2e-%') ---
    // Шаг 1: Удаляем audit_logs для e2e пользователей (FK ON DELETE RESTRICT)
    const auditLogsResult = await pool.query(`
      DELETE FROM audit_logs
      WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Cleanup] Ошибка при удалении audit_logs:', err)
      }
      return { rowCount: 0 }
    })
    console.log(`[E2E Cleanup] Удалено audit_logs: ${auditLogsResult.rowCount ?? 0}`)

    // Шаг 2: Обнуляем FK ссылки на e2e пользователей в routes
    await pool.query(`
      UPDATE routes SET approved_by = NULL
      WHERE approved_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch(() => { /* игнорируем если колонка не существует */ })

    await pool.query(`
      UPDATE routes SET rejected_by = NULL
      WHERE rejected_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch(() => { /* игнорируем если колонка не существует */ })

    // Шаг 3: Удаляем rate_limits, созданные e2e пользователями (created_by NOT NULL)
    const rateLimitsByUserResult = await pool.query(`
      DELETE FROM rate_limits
      WHERE created_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Cleanup] Ошибка при удалении rate_limits по created_by:', err)
      }
      return { rowCount: 0 }
    })
    console.log(`[E2E Cleanup] Удалено rate_limits (по created_by): ${rateLimitsByUserResult.rowCount ?? 0}`)

    // Шаг 4: Удаляем e2e пользователей
    const usersResult = await pool.query(`DELETE FROM users WHERE username LIKE 'e2e-%'`)
    console.log(`[E2E Cleanup] Удалено пользователей: ${usersResult.rowCount ?? 0}`)

    console.log('[E2E Cleanup] Очистка завершена.')
  } finally {
    await pool.end()
  }
}

/**
 * Global setup для Playwright.
 *
 * Создаёт тестовых пользователей через API если они ещё не существуют.
 * Для аутентификации использует admin credentials из env переменных.
 */
async function globalSetup(): Promise<void> {
  // Загружаем .env.e2e если он существует
  loadEnvFile()

  // Шаг 0: Очистка тестовых данных от предыдущих прогонов
  await cleanupTestData()

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
  const keycloakUrl = process.env.KEYCLOAK_URL || 'http://localhost:8180'
  const keycloakRealm = process.env.KEYCLOAK_REALM || 'api-gateway'
  const keycloakClientId = process.env.KEYCLOAK_CLIENT_ID || 'gateway-admin-ui'

  // Шаг 1: Аутентификация через Keycloak Direct Access Grants
  console.log(`[E2E Setup] Вход под пользователем ${adminUsername} через Keycloak...`)

  const tokenResponse = await fetch(
    `${keycloakUrl}/realms/${keycloakRealm}/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: keycloakClientId,
        username: adminUsername,
        password: adminPassword,
      }),
    }
  )

  if (!tokenResponse.ok) {
    const body = await tokenResponse.text()
    throw new Error(
      `[E2E Setup] Не удалось получить токен для ${adminUsername}: ${tokenResponse.status}\n${body}`
    )
  }

  const tokenData = await tokenResponse.json() as { access_token: string }
  const bearerToken = tokenData.access_token

  console.log('[E2E Setup] Вход выполнен успешно (Keycloak token получен).')

  // Шаг 2: Получение списка существующих пользователей
  const usersResponse = await fetch(`${apiBase}/api/v1/users`, {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${bearerToken}`,
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
        'Authorization': `Bearer ${bearerToken}`,
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

  // Шаг 4: Создание Keycloak пользователей в БД (для audit_logs FK constraint)
  console.log('[E2E Setup] Создаём Keycloak пользователей в БД для audit_logs...')
  const databaseUrl = process.env.DATABASE_URL || 'postgresql://gateway:gateway@localhost:5432/gateway'
  const pool = new Pool({ connectionString: databaseUrl })

  try {
    // Создаём пользователей из realm-export с Keycloak UUID
    // ВАЖНО: username != email (username: admin, email: admin@example.com)
    // UUID должны совпадать с jwt.subject из Keycloak для audit_logs FK constraint
    await pool.query(`
      INSERT INTO users (id, username, password_hash, email, role, created_at, updated_at)
      VALUES
        ('f6de7d8b-0737-4c7e-8442-3ae00be29e91', 'admin', '$2a$10$dummy', 'admin@example.com', 'admin', NOW(), NOW()),
        ('4b32f41d-fafb-483d-9a5c-706494035fd6', 'developer', '$2a$10$dummy', 'dev@example.com', 'developer', NOW(), NOW())
      ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        username = EXCLUDED.username,
        updated_at = NOW()
    `)
    console.log('[E2E Setup] Keycloak пользователи созданы в БД.')
  } finally {
    await pool.end()
  }

  console.log('[E2E Setup] Подготовка завершена.')
}

export default globalSetup
