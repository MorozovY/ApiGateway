import { Page, expect } from '@playwright/test'

/**
 * Генерирует Keycloak access token через Direct Access Grants flow
 * (используется для UI login form в Admin UI)
 *
 * ENHANCEMENT E-1: Валидирует JWT structure и claims
 */
export async function keycloakLogin(
  page: Page,
  username: string,
  password: string,
  landingUrl = '/dashboard'
): Promise<void> {
  console.log(`[E2E] Keycloak login attempt: ${username}`)

  // Navigate to login page
  await page.goto('/')

  // Fill login form (Direct Access Grants custom form)
  // Note: Используем data-testid вместо name attribute (Ant Design Form)
  await page.locator('[data-testid="username-input"]').fill(username)
  await page.locator('[data-testid="password-input"]').fill(password)
  await page.locator('[data-testid="login-button"]').click()

  // Wait for redirect to landing page
  await page.waitForURL(landingUrl, { timeout: 10_000 })

  // Verify token in sessionStorage (stored as keycloak_tokens JSON object)
  const tokensStr = await page.evaluate(() => sessionStorage.getItem('keycloak_tokens'))
  if (!tokensStr) {
    throw new Error('Keycloak tokens not found in sessionStorage')
  }

  // Parse keycloak_tokens
  const tokens = JSON.parse(tokensStr)
  if (!tokens.access_token) {
    throw new Error('access_token not found in keycloak_tokens')
  }

  // ENHANCEMENT E-1: Validate JWT structure
  const parts = tokens.access_token.split('.')
  if (parts.length !== 3) {
    throw new Error(`Invalid JWT format: expected 3 parts, got ${parts.length}`)
  }

  // Decode payload (base64url)
  const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))

  // Verify expected claims
  if (!payload.sub) {
    throw new Error('JWT missing "sub" claim')
  }
  if (!payload.azp) {
    throw new Error('JWT missing "azp" claim (consumer_id extraction)')
  }

  console.log(`[E2E] Login successful: ${username}, consumer_id: ${payload.azp}`)
}

/**
 * Генерирует Keycloak access token через Client Credentials flow
 * (используется для API consumers в Gateway requests)
 */
export async function getConsumerToken(
  page: Page,
  clientId: string,
  clientSecret: string
): Promise<string> {
  const keycloakUrl = process.env.KEYCLOAK_URL || 'http://localhost:8180'
  const realm = process.env.KEYCLOAK_REALM || 'api-gateway'

  const response = await page.request.post(
    `${keycloakUrl}/realms/${realm}/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'client_credentials',
        client_id: clientId,
        client_secret: clientSecret
      }
    }
  )

  if (!response.ok()) {
    throw new Error(`Failed to get consumer token: ${response.status()}`)
  }

  const data = await response.json()
  return data.access_token
}

/**
 * Logout из Keycloak (очистка sessionStorage)
 */
export async function keycloakLogout(page: Page): Promise<void> {
  // Click user menu button to open dropdown
  await page.locator('[data-testid="user-menu-button"]').click()

  // Wait for dropdown menu to appear and click logout
  await page.locator('.ant-dropdown-menu .ant-dropdown-menu-item-danger').click()

  // Verify redirect to login
  await page.waitForURL('/login', { timeout: 5000 })

  // Verify tokens cleared from sessionStorage
  const tokensStr = await page.evaluate(() => sessionStorage.getItem('keycloak_tokens'))
  if (tokensStr !== null) {
    throw new Error('Tokens were not cleared from sessionStorage after logout')
  }
}

/**
 * SPA навигация через sidebar menu
 * CRITICAL C-1: Extracted from epic-7/8 для переиспользования
 */
export async function navigateToMenu(page: Page, menuItemText: string | RegExp): Promise<void> {
  const menuItem = page.locator('[role="menuitem"]').filter({ hasText: menuItemText })
  await menuItem.click()
  await expect(menuItem).toHaveClass(/ant-menu-item-selected/, { timeout: 5000 })
}
