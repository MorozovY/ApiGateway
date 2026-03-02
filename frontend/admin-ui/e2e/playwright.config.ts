// Playwright Config — E2E тесты с mock зависимостями
// Story 13.15: CI-first подход, единая конфигурация без условий `if CI`

import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  // Директория с тестами
  testDir: './tests',

  // Параллельное выполнение тестов
  fullyParallel: true,

  // Retries для стабильности (одинаково для локального и CI)
  retries: 1,

  // Timeout для теста (30 секунд достаточно для mock API)
  timeout: 30_000,

  // Reporter — HTML отчёт
  reporter: [['html', { outputFolder: '../playwright-report' }]],

  // Общие настройки для всех тестов
  use: {
    // Base URL — локальный dev server
    baseURL: 'http://localhost:3000',

    // Трейсы только при retry (для отладки)
    trace: 'on-first-retry',

    // Screenshots при падении
    screenshot: 'only-on-failure',

    // Timeouts для действий
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
  },

  // Один браузер (Chromium) для скорости
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // Dev server — запуск Vite при необходимости
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
