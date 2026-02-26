// Diagnostic test для проверки Keycloak auth flow
import { test, expect } from '@playwright/test'
import { login, apiRequest } from './helpers/auth'

test.describe('DIAGNOSTIC: Auth Flow', () => {
  test('проверяет что JWT token работает для API calls', async ({ page }) => {
    const TIMESTAMP = Date.now()

    console.log('[DIAGNOSTIC] Step 1: Login')
    await login(page, 'test-developer', 'Test1234!', '/routes')

    console.log('[DIAGNOSTIC] Step 2: Check URL after login')
    const url = page.url()
    console.log(`[DIAGNOSTIC] Current URL: ${url}`)
    console.log(`[DIAGNOSTIC] Expected URL: contains /routes`)

    console.log('[DIAGNOSTIC] Step 2.5: Take screenshot')
    await page.screenshot({ path: 'e2e/screenshots/diagnostic-after-login.png', fullPage: true })

    console.log('[DIAGNOSTIC] Step 3: Check what headings are visible')
    const h2Elements = await page.locator('h2').allTextContents()
    console.log(`[DIAGNOSTIC] All h2 elements on page: ${JSON.stringify(h2Elements)}`)

    console.log('[DIAGNOSTIC] Step 4: Wait for page load')
    await expect(page.locator('h2:has-text("Routes")')).toBeVisible()

    console.log('[DIAGNOSTIC] Step 4: Wait for AuthContext init')
    await expect(page.locator('[data-testid="user-menu-button"]')).toBeVisible({ timeout: 5000 })

    console.log('[DIAGNOSTIC] Step 5: Check sessionStorage')
    const tokensStr = await page.evaluate(() => sessionStorage.getItem('keycloak_tokens'))
    console.log(`[DIAGNOSTIC] sessionStorage tokens: ${tokensStr ? 'PRESENT' : 'MISSING'}`)
    expect(tokensStr).not.toBeNull()

    console.log('[DIAGNOSTIC] Step 6: Make API request via apiRequest helper')
    const response = await apiRequest(page, 'GET', '/api/v1/routes')
    console.log(`[DIAGNOSTIC] API response status: ${response.status()}`)
    if (!response.ok()) {
      const errorText = await response.text()
      console.log(`[DIAGNOSTIC] API response error: ${errorText}`)
    }
    expect(response.status()).toBe(200)

    console.log('[DIAGNOSTIC] Step 6.5: Try creating a route via API')
    const createResponse = await apiRequest(page, 'POST', '/api/v1/routes', {
      path: `/diagnostic-${TIMESTAMP}`,
      upstreamUrl: 'http://test.local:8080',
      methods: ['GET', 'POST']
    })
    console.log(`[DIAGNOSTIC] Create route response status: ${createResponse.status()}`)
    if (!createResponse.ok()) {
      const errorText = await createResponse.text()
      console.log(`[DIAGNOSTIC] Create route error: ${errorText}`)
    }
    expect(createResponse.ok()).toBeTruthy()

    console.log('[DIAGNOSTIC] Step 7: Check that response is JSON')
    const data = await response.json()
    console.log(`[DIAGNOSTIC] API response data keys: ${Object.keys(data).join(', ')}`)
    expect(data).toHaveProperty('items')

    console.log('[DIAGNOSTIC] ✅ ALL CHECKS PASSED!')
  })
})
