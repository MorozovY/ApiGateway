import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  // FIX M-4: Включаем parallel execution после добавления test isolation (TIMESTAMP + resources cleanup)
  fullyParallel: true,
  retries: 1,
  reporter: 'html',
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
