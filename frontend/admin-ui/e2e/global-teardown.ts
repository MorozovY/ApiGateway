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
 */
function loadEnvFile(): void {
  const envFilePath = path.resolve(__dirname, '../.env.e2e')
  if (!fs.existsSync(envFilePath)) {
    return
  }

  const content = fs.readFileSync(envFilePath, 'utf-8')
  for (const line of content.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue

    const eqIndex = trimmed.indexOf('=')
    if (eqIndex === -1) continue

    const key = trimmed.slice(0, eqIndex).trim()
    const value = trimmed.slice(eqIndex + 1).trim()

    if (!(key in process.env)) {
      process.env[key] = value
    }
  }
}

/**
 * Очистка тестовых данных в БД после прогона E2E тестов.
 *
 * Удаляет все данные созданные во время тестов:
 * - routes: path LIKE '/e2e-%'
 * - rate_limits: name LIKE 'e2e-%'
 * - users: username LIKE 'e2e-%'
 * - audit_logs: связанные с тестовыми данными
 *
 * Порядок удаления учитывает FK constraints.
 */
async function cleanupTestData(): Promise<void> {
  const databaseUrl = process.env.DATABASE_URL || 'postgresql://gateway:gateway@localhost:5432/gateway'

  const pool = new Pool({ connectionString: databaseUrl })

  try {
    console.log('[E2E Teardown] Очистка тестовых данных после прогона...')

    // --- Удаление audit_logs для тестовых сущностей ---
    // Удаляем по entity_type и паттернам в details (JSONB)
    const auditLogsRoutesResult = await pool.query(`
      DELETE FROM audit_logs
      WHERE entity_type = 'route'
        AND (
          details->>'path' LIKE '/e2e-%'
          OR details->>'routeId' IN (SELECT id::text FROM routes WHERE path LIKE '/e2e-%')
        )
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Teardown] Ошибка при удалении audit_logs (routes):', err)
      }
      return { rowCount: 0 }
    })
    console.log(`[E2E Teardown] Удалено audit_logs (routes): ${auditLogsRoutesResult.rowCount ?? 0}`)

    const auditLogsPoliciesResult = await pool.query(`
      DELETE FROM audit_logs
      WHERE entity_type = 'rate_limit'
        AND (
          details->>'name' LIKE 'e2e-%'
          OR details->>'rateLimitId' IN (SELECT id::text FROM rate_limits WHERE name LIKE 'e2e-%')
        )
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Teardown] Ошибка при удалении audit_logs (rate_limits):', err)
      }
      return { rowCount: 0 }
    })
    console.log(`[E2E Teardown] Удалено audit_logs (rate_limits): ${auditLogsPoliciesResult.rowCount ?? 0}`)

    // --- Удаление route_versions ---
    await pool.query(`
      DELETE FROM route_versions
      WHERE route_id IN (SELECT id FROM routes WHERE path LIKE '/e2e-%')
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Teardown] Ошибка при удалении route_versions:', err)
      }
    })

    // --- Удаление маршрутов ---
    const routesResult = await pool.query(`DELETE FROM routes WHERE path LIKE '/e2e-%'`)
    console.log(`[E2E Teardown] Удалено маршрутов (по path): ${routesResult.rowCount ?? 0}`)

    // Удаляем маршруты которые ссылаются на E2E политики
    const routesByPolicyResult = await pool.query(`
      DELETE FROM routes
      WHERE rate_limit_id IN (SELECT id FROM rate_limits WHERE name LIKE 'e2e-%')
    `)
    console.log(`[E2E Teardown] Удалено маршрутов (по политике): ${routesByPolicyResult.rowCount ?? 0}`)

    // --- Удаление политик rate limit ---
    const policiesResult = await pool.query(`DELETE FROM rate_limits WHERE name LIKE 'e2e-%'`)
    console.log(`[E2E Teardown] Удалено политик: ${policiesResult.rowCount ?? 0}`)

    // --- Очистка E2E пользователей (username LIKE 'e2e-%') ---
    // Шаг 1: Удаляем audit_logs для e2e пользователей
    const auditLogsUsersResult = await pool.query(`
      DELETE FROM audit_logs
      WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Teardown] Ошибка при удалении audit_logs (users):', err)
      }
      return { rowCount: 0 }
    })
    console.log(`[E2E Teardown] Удалено audit_logs (users): ${auditLogsUsersResult.rowCount ?? 0}`)

    // Шаг 2: Обнуляем FK ссылки на e2e пользователей в routes
    await pool.query(`
      UPDATE routes SET approved_by = NULL
      WHERE approved_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch(() => { /* игнорируем если колонка не существует */ })

    await pool.query(`
      UPDATE routes SET rejected_by = NULL
      WHERE rejected_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch(() => { /* игнорируем если колонка не существует */ })

    // Шаг 3: Удаляем rate_limits, созданные e2e пользователями
    const rateLimitsByUserResult = await pool.query(`
      DELETE FROM rate_limits
      WHERE created_by IN (SELECT id FROM users WHERE username LIKE 'e2e-%')
    `).catch((err: { code?: string }) => {
      if (err.code !== '42P01') {
        console.warn('[E2E Teardown] Ошибка при удалении rate_limits по created_by:', err)
      }
      return { rowCount: 0 }
    })
    console.log(`[E2E Teardown] Удалено rate_limits (по created_by): ${rateLimitsByUserResult.rowCount ?? 0}`)

    // Шаг 4: Удаляем e2e пользователей
    const usersResult = await pool.query(`DELETE FROM users WHERE username LIKE 'e2e-%'`)
    console.log(`[E2E Teardown] Удалено пользователей: ${usersResult.rowCount ?? 0}`)

    console.log('[E2E Teardown] Очистка завершена.')
  } finally {
    await pool.end()
  }
}

/**
 * Global teardown для Playwright.
 *
 * Вызывается после завершения всех тестов.
 * Очищает данные, созданные во время прогона.
 */
async function globalTeardown(): Promise<void> {
  loadEnvFile()
  await cleanupTestData()
}

export default globalTeardown
