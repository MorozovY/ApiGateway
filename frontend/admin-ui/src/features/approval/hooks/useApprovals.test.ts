// Тесты для хуков approval (Story 10.2 — polling configuration)
import { describe, it, expect } from 'vitest'
import {
  APPROVALS_REFRESH_INTERVAL,
  APPROVALS_STALE_TIME,
  PENDING_ROUTES_QUERY_KEY,
} from './useApprovals'

describe('useApprovals константы (Story 10.2)', () => {
  it('APPROVALS_REFRESH_INTERVAL равен 5000 мс (5 секунд, AC1)', () => {
    expect(APPROVALS_REFRESH_INTERVAL).toBe(5000)
  })

  it('APPROVALS_STALE_TIME равен 2000 мс (2 секунды)', () => {
    expect(APPROVALS_STALE_TIME).toBe(2000)
  })

  it('PENDING_ROUTES_QUERY_KEY определён для React Query кэша', () => {
    expect(PENDING_ROUTES_QUERY_KEY).toBe('pendingRoutes')
  })
})

// Design Decision: Тесты проверяют конфигурацию polling констант.
// Реальное polling behavior тестируется косвенно:
// 1. React Query гарантирует работу refetchInterval (library contract)
// 2. UI тесты в ApprovalsPage.test.tsx проверяют loading states
// 3. Manual validation (Task 5) подтверждает E2E поведение
// Explicit polling тесты с fake timers требуют QueryClientProvider
// и добавляют complexity без значительной value (Story 10.2 code review).
