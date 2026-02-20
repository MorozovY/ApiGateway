// Тесты для AuditLogsTable (Story 7.5, AC1, AC3, AC5)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, render, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuditLogsTable } from './AuditLogsTable'
import type { AuditLogsResponse, AuditFilter, AuditLogEntry } from '../types/audit.types'

const mockAuditLogs: AuditLogEntry[] = [
  {
    id: 'audit-1',
    entityType: 'route',
    entityId: 'route-uuid-123',
    action: 'created',
    user: { id: 'user-1', username: 'john_dev' },
    timestamp: '2026-02-11T14:30:00Z',
    changes: { before: null, after: { path: '/api/test', upstreamUrl: 'http://test:8080' } },
    ipAddress: '192.168.1.1',
    correlationId: 'corr-123',
  },
  {
    id: 'audit-2',
    entityType: 'user',
    entityId: 'user-uuid-456',
    action: 'updated',
    user: { id: 'user-2', username: 'admin_user' },
    timestamp: '2026-02-11T15:00:00Z',
    changes: { before: { role: 'developer' }, after: { role: 'admin' } },
    ipAddress: null,
    correlationId: null,
  },
  {
    id: 'audit-3',
    entityType: 'route',
    entityId: 'route-uuid-789',
    action: 'deleted',
    user: { id: 'user-3', username: 'jane_dev' },
    timestamp: '2026-02-11T16:00:00Z',
    changes: { before: { path: '/api/old' }, after: null },
    ipAddress: '10.0.0.1',
    correlationId: 'corr-456',
  },
]

const mockData: AuditLogsResponse = {
  items: mockAuditLogs,
  total: 3,
  offset: 0,
  limit: 20,
}

const defaultFilter: AuditFilter = {
  offset: 0,
  limit: 20,
}

describe('AuditLogsTable', () => {
  const mockOnPaginationChange = vi.fn()

  function renderTable(
    data: AuditLogsResponse | undefined = mockData,
    isLoading = false,
    filter: AuditFilter = defaultFilter
  ) {
    return render(
      <MemoryRouter>
        <AuditLogsTable
          data={data}
          isLoading={isLoading}
          filter={filter}
          onPaginationChange={mockOnPaginationChange}
        />
      </MemoryRouter>
    )
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  it('отображает колонки таблицы (AC1)', () => {
    renderTable()

    // Проверяем заголовки колонок
    expect(screen.getByText('Timestamp')).toBeInTheDocument()
    expect(screen.getByText('Действие')).toBeInTheDocument()
    expect(screen.getByText('Тип')).toBeInTheDocument()
    expect(screen.getByText('Сущность')).toBeInTheDocument()
  })

  it('отображает данные аудит-логов', () => {
    renderTable()

    // Usernames
    expect(screen.getByText('john_dev')).toBeInTheDocument()
    expect(screen.getByText('admin_user')).toBeInTheDocument()
    expect(screen.getByText('jane_dev')).toBeInTheDocument()

    // Entity types (русские лейблы)
    const routeLabels = screen.getAllByText('Маршрут')
    expect(routeLabels.length).toBe(2)
  })

  it('отображает action badges с цветами (AC5)', () => {
    renderTable()

    // Action labels (русские)
    expect(screen.getByText('Создано')).toBeInTheDocument()
    expect(screen.getByText('Обновлено')).toBeInTheDocument()
    expect(screen.getByText('Удалено')).toBeInTheDocument()
  })

  it('показывает skeleton при начальной загрузке без данных (AC7)', () => {
    // Передаём data=undefined и isLoading=true для первой загрузки
    render(
      <MemoryRouter>
        <AuditLogsTable
          data={undefined}
          isLoading={true}
          filter={defaultFilter}
          onPaginationChange={mockOnPaginationChange}
        />
      </MemoryRouter>
    )

    // Skeleton элемент должен быть
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument()
    // Данные не должны отображаться
    expect(screen.queryByText('john_dev')).not.toBeInTheDocument()
  })

  it('расширяет row при клике на expand icon (AC3)', async () => {
    renderTable()

    // Находим все expand иконки
    const expandIcons = document.querySelectorAll('.anticon-expand')
    expect(expandIcons.length).toBeGreaterThan(0)

    // Кликаем на первую
    fireEvent.click(expandIcons[0]!)

    await waitFor(() => {
      // Должны появиться детали
      expect(screen.getByText('Entity ID')).toBeInTheDocument()
      expect(screen.getByText('Correlation ID')).toBeInTheDocument()
      expect(screen.getByText('IP Address')).toBeInTheDocument()
    })
  })

  it('показывает entity link для route (AC1)', () => {
    renderTable()

    // Для маршрутов должна быть ссылка
    const routeLinks = screen.getAllByRole('link')
    const routeLink = routeLinks.find((link) => link.getAttribute('href')?.includes('/routes/'))
    expect(routeLink).toBeInTheDocument()
  })

  it('показывает пагинацию', () => {
    renderTable()

    expect(screen.getByText('Всего 3 записей')).toBeInTheDocument()
  })

  it('не показывает ссылку для deleted route', () => {
    renderTable()

    // Находим все ссылки на маршруты
    const routeLinks = screen.getAllByRole('link')
    const deletedRouteLink = routeLinks.find((link) =>
      link.getAttribute('href')?.includes('route-uuid-789')
    )

    // Для deleted route (route-uuid-789) не должно быть ссылки
    expect(deletedRouteLink).toBeUndefined()
  })
})
