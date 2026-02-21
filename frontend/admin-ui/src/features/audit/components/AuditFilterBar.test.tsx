// Тесты для AuditFilterBar (Story 7.5, AC2)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, render, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuditFilterBar } from './AuditFilterBar'
import { FILTER_DEBOUNCE_MS } from '../config/auditConfig'
import type { AuditFilter } from '../types/audit.types'

// Мок для users API (Story 8.6 — используем fetchUserOptions)
vi.mock('@features/users/api/usersApi', () => ({
  fetchUserOptions: vi.fn().mockResolvedValue({
    items: [
      { id: 'user-1', username: 'admin' },
      { id: 'user-2', username: 'developer' },
    ],
  }),
}))

describe('AuditFilterBar', () => {
  let queryClient: QueryClient
  const mockOnFilterChange = vi.fn()
  const mockOnClearFilters = vi.fn()

  const defaultFilter: AuditFilter = {
    userId: undefined,
    action: undefined,
    entityType: undefined,
    dateFrom: undefined,
    dateTo: undefined,
  }

  function renderFilterBar(filter: AuditFilter = defaultFilter) {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })

    return render(
      <QueryClientProvider client={queryClient}>
        <AuditFilterBar
          filter={filter}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      </QueryClientProvider>
    )
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  it('отображает все элементы фильтрации', async () => {
    renderFilterBar()

    // Date range picker
    const datePickers = screen.getAllByPlaceholderText(/дата/i)
    expect(datePickers.length).toBe(2)

    // Select для пользователя
    expect(screen.getByText('Пользователь')).toBeInTheDocument()

    // Select для типа сущности
    expect(screen.getByText('Тип сущности')).toBeInTheDocument()

    // Select для действия
    expect(screen.getByText('Действие')).toBeInTheDocument()
  })

  it('показывает кнопку сброса фильтров при активных фильтрах', () => {
    renderFilterBar({ ...defaultFilter, action: ['created'] })

    expect(screen.getByText('Сбросить фильтры')).toBeInTheDocument()
  })

  it('не показывает кнопку сброса при пустых фильтрах', () => {
    renderFilterBar(defaultFilter)

    expect(screen.queryByText('Сбросить фильтры')).not.toBeInTheDocument()
  })

  it('вызывает onClearFilters при клике на кнопку сброса', () => {
    renderFilterBar({ ...defaultFilter, action: ['created'] })

    fireEvent.click(screen.getByText('Сбросить фильтры'))

    expect(mockOnClearFilters).toHaveBeenCalled()
  })

  it('вызывает onFilterChange при выборе типа сущности (с debounce)', async () => {
    renderFilterBar()

    // Открываем select типа сущности
    const entityTypeSelect = screen.getByText('Тип сущности')
    fireEvent.mouseDown(entityTypeSelect)

    await waitFor(() => {
      expect(screen.getByText('Маршрут')).toBeInTheDocument()
    })

    // Выбираем маршрут
    fireEvent.click(screen.getByText('Маршрут'))

    // Ждём debounce (AC2: 300ms) — используем waitFor вместо fake timers
    await waitFor(
      () => {
        expect(mockOnFilterChange).toHaveBeenCalledWith({
          entityType: 'route',
          offset: 0,
        })
      },
      { timeout: FILTER_DEBOUNCE_MS + 100 }
    )
  })

  it('загружает список пользователей для dropdown', async () => {
    renderFilterBar()

    // Открываем select пользователя
    const userSelect = screen.getByText('Пользователь')
    fireEvent.mouseDown(userSelect)

    await waitFor(() => {
      expect(screen.getByText('admin')).toBeInTheDocument()
      expect(screen.getByText('developer')).toBeInTheDocument()
    })
  })

  it('отображает action filter как multi-select (AC2)', () => {
    renderFilterBar({ ...defaultFilter, action: ['created', 'updated'] })

    // Проверяем что Select для действия имеет класс multi-select
    const multiSelectContainers = document.querySelectorAll('.ant-select-multiple')
    expect(multiSelectContainers.length).toBeGreaterThan(0)

    // Проверяем что есть selection items (теги)
    const selectionItems = document.querySelectorAll('.ant-select-selection-item')
    expect(selectionItems.length).toBeGreaterThanOrEqual(1) // Минимум 1 тег или "+ N ..."
  })
})
