import { defineConfig, devices } from '@playwright/test'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

// ESM-совместимый аналог __dirname
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

// Загрузка переменных из .env файла (для CI)
function loadEnvFile(): void {
  const envPath = path.resolve(__dirname, '.env')
  if (!fs.existsSync(envPath)) return

  const content = fs.readFileSync(envPath, 'utf-8')
  for (const line of content.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const eqIndex = trimmed.indexOf('=')
    if (eqIndex === -1) continue

    const key = trimmed.slice(0, eqIndex).trim()
    const value = trimmed.slice(eqIndex + 1).trim()

    // Не перезаписываем уже установленные переменные
    if (!(key in process.env)) {
      process.env[key] = value
    }
  }
}

// Загружаем .env ДО создания конфига
loadEnvFile()

export default defineConfig({
  testDir: './e2e',
  // FIX M-4: Включаем parallel execution после добавления test isolation (TIMESTAMP + resources cleanup)
  fullyParallel: true,
  // Story 13.14 AC4: Увеличены retries для CI стабильности (login flakiness)
  retries: process.env.CI ? 2 : 1,
  // Story 13.14 AC4: Увеличен global timeout для CI (network latency)
  timeout: process.env.CI ? 60_000 : 30_000,
  reporter: 'html',
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    // В CI используем BASE_URL из переменных окружения
    baseURL: process.env.BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    // Story 13.14 AC4: Увеличен action timeout для CI
    actionTimeout: process.env.CI ? 15_000 : 10_000,
    navigationTimeout: process.env.CI ? 30_000 : 15_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
})
