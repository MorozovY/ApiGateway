// Тесты для AuditPage (Story 7.5, AC1-AC7)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor, cleanup, fireEvent } from '@testing-library/react'
import { message } from 'antd'
import { renderWithMockAuth } from '../../../test/test-utils'
import { AuditPage } from './AuditPage'
import { fetchAllAuditLogsForExport } from '../api/auditApi'
import { downloadAuditCsv } from '../utils/exportCsv'
import type { AuditLogEntry, AuditLogsResponse } from '../types/audit.types'

// Мок данные
const mockAuditLogs: AuditLogEntry[] = [
  {
    id: 'audit-1',
    entityType: 'route',
    entityId: 'route-uuid-123',
    action: 'created',
    user: { id: 'user-1', username: 'developer' },
    timestamp: '2026-02-11T14:30:00Z',
    changes: { before: null, after: { path: '/api/test' } },
    ipAddress: '192.168.1.1',
    correlationId: 'corr-123',
  },
]

const mockData: AuditLogsResponse = {
  items: mockAuditLogs,
  total: 1,
  offset: 0,
  limit: 20,
}

// Мок antd message
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd')
  return {
    ...actual,
    message: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
    },
  }
})

// Мок для fetchAllAuditLogsForExport — vi.mock hoisted
vi.mock('../api/auditApi', () => ({
  fetchAllAuditLogsForExport: vi.fn().mockResolvedValue({
    items: [
      {
        id: 'audit-1',
        entityType: 'route',
        entityId: 'route-uuid-123',
        action: 'created',
        user: { id: 'user-1', username: 'developer' },
        timestamp: '2026-02-11T14:30:00Z',
        changes: { before: null, after: { path: '/api/test' } },
        ipAddress: '192.168.1.1',
        correlationId: 'corr-123',
      },
    ],
    total: 1,
    offset: 0,
    limit: 10000,
  }),
}))

// Мок для downloadAuditCsv
vi.mock('../utils/exportCsv', () => ({
  downloadAuditCsv: vi.fn(),
}))

// Мок для useAuditLogs
let mockAuditData: AuditLogsResponse | undefined = mockData
let mockIsLoading = false
let mockError: Error | null = null

vi.mock('../hooks/useAuditLogs', () => ({
  useAuditLogs: () => ({
    data: mockAuditData,
    isLoading: mockIsLoading,
    error: mockError,
    refetch: vi.fn(),
  }),
  AUDIT_LOGS_QUERY_KEY: 'audit-logs',
}))

// Мок пользователей
const securityUser = {
  userId: 'sec-1',
  username: 'security-user',
  role: 'security' as const,
}

const adminUser = {
  userId: 'admin-1',
  username: 'admin-user',
  role: 'admin' as const,
}

const developerUser = {
  userId: 'dev-1',
  username: 'developer-user',
  role: 'developer' as const,
}

describe('AuditPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuditData = mockData
    mockIsLoading = false
    mockError = null
  })

  afterEach(() => {
    cleanup()
  })

  describe('Role-based Access (AC6)', () => {
    it('редиректит developer на главную с сообщением об ошибке', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: developerUser },
        initialEntries: ['/audit'],
      })

      await waitFor(() => {
        expect(message.error).toHaveBeenCalledWith('Недостаточно прав для просмотра аудит-логов')
      })
    })

    it('отображает страницу для security пользователя', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        expect(screen.getByText('Аудит-логи')).toBeInTheDocument()
      })
    })

    it('отображает страницу для admin пользователя', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: adminUser },
      })

      await waitFor(() => {
        expect(screen.getByText('Аудит-логи')).toBeInTheDocument()
      })
    })
  })

  describe('UI Elements (AC1, AC2)', () => {
    it('отображает заголовок страницы', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        expect(screen.getByText('Аудит-логи')).toBeInTheDocument()
      })
    })

    it('отображает кнопку экспорта CSV (AC4)', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        expect(screen.getByText('Экспорт CSV')).toBeInTheDocument()
      })
    })

    it('отображает панель фильтров (AC2)', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        // Date range picker placeholders
        expect(screen.getByPlaceholderText('Дата от')).toBeInTheDocument()
        expect(screen.getByPlaceholderText('Дата до')).toBeInTheDocument()
      })
    })

    it('отображает таблицу с данными (AC1)', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        expect(screen.getByText('developer')).toBeInTheDocument()
        expect(screen.getByText('Создано')).toBeInTheDocument()
      })
    })
  })

  describe('Empty State (AC7)', () => {
    it('показывает empty state при отсутствии данных', async () => {
      mockAuditData = { items: [], total: 0, offset: 0, limit: 20 }

      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        expect(screen.getByText('Нет записей для выбранных фильтров')).toBeInTheDocument()
        expect(screen.getByText('Попробуйте изменить параметры фильтрации')).toBeInTheDocument()
      })
    })
  })

  describe('Error Handling', () => {
    it('показывает ошибку при сбое загрузки', async () => {
      mockAuditData = undefined
      mockError = new Error('Network error')

      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        expect(screen.getByText('Ошибка загрузки')).toBeInTheDocument()
        expect(screen.getByText('Network error')).toBeInTheDocument()
      })
    })
  })

  describe('Export Button (AC4)', () => {
    it('кнопка экспорта disabled при total=0', async () => {
      mockAuditData = { items: [], total: 0, offset: 0, limit: 20 }

      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        const exportButton = screen.getByText('Экспорт CSV').closest('button')
        expect(exportButton).toBeDisabled()
      })
    })

    it('кнопка экспорта enabled при наличии данных', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        const exportButton = screen.getByText('Экспорт CSV').closest('button')
        expect(exportButton).not.toBeDisabled()
      })
    })

    it('при клике на экспорт вызывает fetchAllAuditLogsForExport и downloadAuditCsv', async () => {
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
      })

      await waitFor(() => {
        expect(screen.getByText('Экспорт CSV')).toBeInTheDocument()
      })

      const exportButton = screen.getByText('Экспорт CSV').closest('button')!
      fireEvent.click(exportButton)

      await waitFor(() => {
        expect(fetchAllAuditLogsForExport).toHaveBeenCalled()
        // downloadAuditCsv вызывается с массивом items и фильтрами дат
        expect(downloadAuditCsv).toHaveBeenCalledWith(
          expect.arrayContaining([
            expect.objectContaining({ id: 'audit-1', action: 'created' })
          ]),
          undefined, // dateFrom
          undefined  // dateTo
        )
        expect(message.success).toHaveBeenCalledWith('Экспорт завершён')
      })
    })
  })

  describe('URL Filter Validation', () => {
    it('фильтрует невалидные action values из URL', async () => {
      // URL содержит невалидные значения "invalid" и "fake"
      renderWithMockAuth(<AuditPage />, {
        authValue: { isAuthenticated: true, user: securityUser },
        initialEntries: ['/audit?action=created,invalid,updated,fake'],
      })

      await waitFor(() => {
        expect(screen.getByText('Аудит-логи')).toBeInTheDocument()
      })

      // Компонент должен успешно отрендериться, невалидные значения отфильтрованы
      // Проверяем что нет ошибок в UI
      expect(screen.queryByText('Ошибка')).not.toBeInTheDocument()
    })
  })
})
