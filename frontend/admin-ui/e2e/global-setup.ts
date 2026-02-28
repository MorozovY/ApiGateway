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
 * Конфигурация тестовых пользователей для создания через Admin API.
 *
 * NOTE: test-* users НЕ создаются через Admin API, так как они создаются
 * напрямую в Keycloak (шаг 4) и в БД с Keycloak UUIDs (шаг 5).
 * Это необходимо для audit_logs FK constraint (user_id должен совпадать с Keycloak sub).
 */
const TEST_USERS = [
  // test-* users создаются отдельно через Keycloak, не через Admin API
]

/**
 * FIX M-2: Очистка E2E consumers из Keycloak.
 *
 * Удаляет всех clients с clientId LIKE 'e2e-%' через Keycloak Admin API.
 * Не удаляет pre-seeded consumers: company-a, company-b, company-c.
 */
async function cleanupKeycloakConsumers(): Promise<void> {
  const keycloakUrl = process.env.KEYCLOAK_URL || 'http://localhost:8180'
  const adminUser = process.env.KEYCLOAK_ADMIN_USER || 'admin'
  const adminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin'

  try {
    console.log('[E2E Cleanup] Очистка E2E consumers из Keycloak...')

    // Получаем admin token для Keycloak Admin API
    const adminTokenResponse = await fetch(
      `${keycloakUrl}/realms/master/protocol/openid-connect/token`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'password',
          client_id: 'admin-cli',
          username: adminUser,
          password: adminPassword,
        }),
      }
    )

    if (!adminTokenResponse.ok) {
      console.warn('[E2E Cleanup] Не удалось получить Keycloak admin token, пропускаем cleanup consumers')
      return
    }

    const adminTokenData = await adminTokenResponse.json() as { access_token: string }
    const adminToken = adminTokenData.access_token

    // Получаем список всех clients в realm api-gateway
    const clientsResponse = await fetch(
      `${keycloakUrl}/admin/realms/api-gateway/clients`,
      {
        headers: { 'Authorization': `Bearer ${adminToken}` },
      }
    )

    if (!clientsResponse.ok) {
      console.warn('[E2E Cleanup] Не удалось получить список clients из Keycloak')
      return
    }

    const clients = await clientsResponse.json() as Array<{ id: string; clientId: string }>

    // Фильтруем E2E consumers (clientId начинается с 'e2e-')
    const e2eConsumers = clients.filter(c => c.clientId.startsWith('e2e-'))

    console.log(`[E2E Cleanup] Найдено ${e2eConsumers.length} E2E consumers для удаления`)

    // Удаляем каждый E2E consumer
    let deletedCount = 0
    for (const consumer of e2eConsumers) {
      const deleteResponse = await fetch(
        `${keycloakUrl}/admin/realms/api-gateway/clients/${consumer.id}`,
        {
          method: 'DELETE',
          headers: { 'Authorization': `Bearer ${adminToken}` },
        }
      )

      if (deleteResponse.ok || deleteResponse.status === 404) {
        deletedCount++
        console.log(`[E2E Cleanup] Consumer ${consumer.clientId} удалён`)
      } else {
        console.warn(`[E2E Cleanup] Не удалось удалить consumer ${consumer.clientId}: ${deleteResponse.status}`)
      }
    }

    console.log(`[E2E Cleanup] Удалено consumers: ${deletedCount}`)
  } catch (error) {
    console.warn('[E2E Cleanup] Ошибка при очистке Keycloak consumers:', error)
  }
}

/**
 * Очистка тестовых данных в БД перед прогоном E2E тестов.
 *
 * Удаляет все данные с E2E паттернами:
 * - routes: path LIKE '/e2e-%'
 * - rate_limits: name LIKE 'e2e-%'
 * - users: username LIKE 'e2e-%'
 * - consumers: clientId LIKE 'e2e-%' (в Keycloak)
 *
 * Порядок удаления учитывает FK constraints:
 * 1. audit_logs → users (ON DELETE RESTRICT)
 * 2. routes.approved_by/rejected_by → users
 * 3. rate_limits.created_by → users (NOT NULL)
 * 4. routes → rate_limits (rate_limit_id)
 *
 * Не удаляет:
 * - test-developer, test-security, test-admin (системные тестовые пользователи)
 * - company-a, company-b, company-c (pre-seeded consumers для тестов)
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

    // Шаг 5: Удаляем test-* пользователей (будут пересозданы с Keycloak UUIDs)
    // Сначала удаляем их FK ссылки
    const testUserIds = await pool.query(`
      SELECT id FROM users WHERE username IN ('test-developer', 'test-security', 'test-admin')
    `)

    if (testUserIds.rows.length > 0) {
      const ids = testUserIds.rows.map(r => r.id)

      // Удаляем FK ссылки в правильном порядке
      await pool.query(`UPDATE routes SET rate_limit_id = NULL WHERE rate_limit_id IN (SELECT id FROM rate_limits WHERE created_by = ANY($1))`, [ids]).catch(() => {})
      await pool.query(`DELETE FROM rate_limits WHERE created_by = ANY($1)`, [ids]).catch(() => {})
      await pool.query(`DELETE FROM audit_logs WHERE user_id = ANY($1)`, [ids]).catch(() => {})
      await pool.query(`UPDATE routes SET approved_by = NULL WHERE approved_by = ANY($1)`, [ids]).catch(() => {})
      await pool.query(`UPDATE routes SET rejected_by = NULL WHERE rejected_by = ANY($1)`, [ids]).catch(() => {})
      await pool.query(`DELETE FROM users WHERE id = ANY($1)`, [ids])

      console.log(`[E2E Cleanup] Удалены test-* пользователи для пересоздания с Keycloak UUIDs`)
    }

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

  // FIX M-2: Очистка E2E consumers из Keycloak
  await cleanupKeycloakConsumers()

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

  // Шаг 4: Создание test-* пользователей в Keycloak (для Epic 2-8 backward compatibility)
  console.log('[E2E Setup] Создаём test-* пользователей в Keycloak...')

  const keycloakAdminUser = process.env.KEYCLOAK_ADMIN_USER || 'admin'
  const keycloakAdminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin'

  // Получаем Keycloak admin token
  const kcAdminTokenResponse = await fetch(
    `${keycloakUrl}/realms/master/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: 'admin-cli',
        username: keycloakAdminUser,
        password: keycloakAdminPassword,
      }),
    }
  )

  if (!kcAdminTokenResponse.ok) {
    console.warn('[E2E Setup] Не удалось получить Keycloak admin token, пропускаем создание test-* пользователей')
  } else {
    const kcAdminTokenData = await kcAdminTokenResponse.json() as { access_token: string }
    const kcAdminToken = kcAdminTokenData.access_token

    // Создаём test-* пользователей в Keycloak
    const testKeycloakUsers = [
      {
        username: 'test-developer',
        email: 'test-developer@example.com',
        firstName: 'Test',
        lastName: 'Developer',
        enabled: true,
        emailVerified: true,
        credentials: [{ type: 'password', value: 'Test1234!', temporary: false }],
        realmRoles: ['admin-ui:developer']
      },
      {
        username: 'test-security',
        email: 'test-security@example.com',
        firstName: 'Test',
        lastName: 'Security',
        enabled: true,
        emailVerified: true,
        credentials: [{ type: 'password', value: 'Test1234!', temporary: false }],
        realmRoles: ['admin-ui:security']
      },
      {
        username: 'test-admin',
        email: 'test-admin@example.com',
        firstName: 'Test',
        lastName: 'Admin',
        enabled: true,
        emailVerified: true,
        credentials: [{ type: 'password', value: 'Test1234!', temporary: false }],
        realmRoles: ['admin-ui:admin']
      }
    ]

    for (const testUser of testKeycloakUsers) {
      // Проверяем существует ли пользователь
      const checkResponse = await fetch(
        `${keycloakUrl}/admin/realms/${keycloakRealm}/users?username=${testUser.username}`,
        {
          headers: { 'Authorization': `Bearer ${kcAdminToken}` },
        }
      )

      if (checkResponse.ok) {
        const existingUsers = await checkResponse.json() as Array<{ id: string; username: string }>
        if (existingUsers.length > 0) {
          console.log(`[E2E Setup] Keycloak пользователь ${testUser.username} уже существует — пропускаем`)
          continue
        }
      }

      // Создаём пользователя
      const createResponse = await fetch(
        `${keycloakUrl}/admin/realms/${keycloakRealm}/users`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${kcAdminToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            username: testUser.username,
            email: testUser.email,
            firstName: testUser.firstName,
            lastName: testUser.lastName,
            enabled: testUser.enabled,
            emailVerified: testUser.emailVerified,
            credentials: testUser.credentials
          })
        }
      )

      if (createResponse.ok || createResponse.status === 409) {
        console.log(`[E2E Setup] Keycloak пользователь ${testUser.username} создан`)

        // Назначаем роли (если пользователь создан)
        if (createResponse.ok) {
          // Получаем ID созданного пользователя из Location header
          const location = createResponse.headers.get('Location')
          if (location) {
            const userId = location.split('/').pop()

            // Получаем роль по имени
            for (const roleName of testUser.realmRoles) {
              const roleResponse = await fetch(
                `${keycloakUrl}/admin/realms/${keycloakRealm}/roles/${roleName}`,
                {
                  headers: { 'Authorization': `Bearer ${kcAdminToken}` },
                }
              )

              if (roleResponse.ok) {
                const roleData = await roleResponse.json() as { id: string; name: string }

                // Назначаем роль пользователю
                await fetch(
                  `${keycloakUrl}/admin/realms/${keycloakRealm}/users/${userId}/role-mappings/realm`,
                  {
                    method: 'POST',
                    headers: {
                      'Authorization': `Bearer ${kcAdminToken}`,
                      'Content-Type': 'application/json'
                    },
                    body: JSON.stringify([roleData])
                  }
                )

                console.log(`[E2E Setup] Назначена роль ${roleName} для ${testUser.username}`)
              }
            }
          }
        }
      } else {
        console.warn(`[E2E Setup] Не удалось создать Keycloak пользователя ${testUser.username}: ${createResponse.status}`)
      }
    }

    console.log('[E2E Setup] Test-* пользователи созданы в Keycloak')
  }

  // Шаг 5: Создание Keycloak пользователей в БД (для audit_logs FK constraint)
  console.log('[E2E Setup] Создаём Keycloak пользователей в БД для audit_logs...')
  const databaseUrl = process.env.DATABASE_URL || 'postgresql://gateway:gateway@localhost:5432/gateway'
  const pool = new Pool({ connectionString: databaseUrl })

  try {
    // Получаем admin token для Keycloak Admin API
    const kcAdminTokenResponse2 = await fetch(
      `${keycloakUrl}/realms/master/protocol/openid-connect/token`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'password',
          client_id: 'admin-cli',
          username: process.env.KEYCLOAK_ADMIN_USER || 'admin',
          password: process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin',
        }),
      }
    )

    if (!kcAdminTokenResponse2.ok) {
      console.warn('[E2E Setup] Не удалось получить Keycloak admin token для синхронизации пользователей в БД')
    } else {
      const kcAdminTokenData2 = await kcAdminTokenResponse2.json() as { access_token: string }
      const kcAdminToken2 = kcAdminTokenData2.access_token

      // Все пользователи которые нужно синхронизировать с БД
      // ВАЖНО: Keycloak генерирует новые UUID при импорте realm, не использует UUID из файла!
      // Поэтому получаем UUID динамически через Admin API
      const usersToSync = [
        { username: 'admin', email: 'admin@example.com', role: 'admin' },
        { username: 'developer', email: 'dev@example.com', role: 'developer' },
        { username: 'security', email: 'security@example.com', role: 'security' },
        { username: 'test-developer', email: 'test-developer@example.com', role: 'developer' },
        { username: 'test-security', email: 'test-security@example.com', role: 'security' },
        { username: 'test-admin', email: 'test-admin@example.com', role: 'admin' }
      ]

      for (const user of usersToSync) {
        // Получаем user ID из Keycloak
        const searchResponse = await fetch(
          `${keycloakUrl}/admin/realms/${keycloakRealm}/users?username=${user.username}&exact=true`,
          {
            headers: { 'Authorization': `Bearer ${kcAdminToken2}` },
          }
        )

        if (searchResponse.ok) {
          const users = await searchResponse.json() as Array<{ id: string; username: string }>
          if (users.length > 0) {
            const keycloakUserId = users[0].id

            // Создаем/обновляем user с Keycloak UUID
            // ON CONFLICT (username) — если пользователь существует, обновляем его id на Keycloak UUID
            // Сначала очищаем FK ссылки для пользователя с другим id
            const oldUserIds = await pool.query(`
              SELECT id FROM users WHERE username = $1 AND id != $2
            `, [user.username, keycloakUserId])

            if (oldUserIds.rows.length > 0) {
              const oldId = oldUserIds.rows[0].id

              // СНАЧАЛА: Создаём нового пользователя с Keycloak UUID чтобы FK могли ссылаться на него
              await pool.query(`
                INSERT INTO users (id, username, password_hash, email, role, created_at, updated_at)
                VALUES ($1, $2 || '_keycloak', '$2a$10$dummy', $3, $4, NOW(), NOW())
                ON CONFLICT DO NOTHING
              `, [keycloakUserId, user.username, user.email, user.role])

              // Очищаем FK ссылки в правильном порядке — переносим на нового пользователя
              // (все FK таблицы на users: routes.approved_by, routes.rejected_by, rate_limits.created_by, consumer_rate_limits.created_by, audit_logs.user_id)
              await pool.query(`UPDATE routes SET created_by = $2 WHERE created_by = $1`, [oldId, keycloakUserId]).catch(() => {})
              await pool.query(`UPDATE routes SET approved_by = $2 WHERE approved_by = $1`, [oldId, keycloakUserId]).catch(() => {})
              await pool.query(`UPDATE routes SET rejected_by = $2 WHERE rejected_by = $1`, [oldId, keycloakUserId]).catch(() => {})
              await pool.query(`UPDATE rate_limits SET created_by = $2 WHERE created_by = $1`, [oldId, keycloakUserId]).catch(() => {})
              await pool.query(`UPDATE consumer_rate_limits SET created_by = $2 WHERE created_by = $1`, [oldId, keycloakUserId]).catch(() => {})
              await pool.query(`DELETE FROM audit_logs WHERE user_id = $1`, [oldId]).catch(() => {})
              await pool.query(`DELETE FROM users WHERE id = $1`, [oldId]).catch(() => {})

              // Теперь переименовываем нового пользователя на правильный username
              await pool.query(`UPDATE users SET username = $1 WHERE id = $2`, [user.username, keycloakUserId]).catch(() => {})

              console.log(`[E2E Setup] Мигрирован ${user.username} на Keycloak ID ${keycloakUserId}`)
            } else {
              // Пользователь не существует или уже имеет правильный id — просто создаём/обновляем
              await pool.query(`
                INSERT INTO users (id, username, password_hash, email, role, created_at, updated_at)
                VALUES ($1, $2, '$2a$10$dummy', $3, $4, NOW(), NOW())
                ON CONFLICT DO NOTHING
              `, [keycloakUserId, user.username, user.email, user.role])
            }

            console.log(`[E2E Setup] Синхронизирован ${user.username} с Keycloak ID ${keycloakUserId}`)
          }
        }
      }
    }

    console.log('[E2E Setup] Keycloak пользователи синхронизированы в БД.')
  } finally {
    await pool.end()
  }

  console.log('[E2E Setup] Подготовка завершена.')
}

export default globalSetup
