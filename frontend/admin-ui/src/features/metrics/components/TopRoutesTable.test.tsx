// Тесты для TopRoutesTable (Story 6.5, обновлено для Story 7.0)
import { describe, it, expect, vi, beforeAll } from 'vitest'
import { render, screen } from '@testing-library/react'
import TopRoutesTable from './TopRoutesTable'
import type { TopRoute } from '../types/metrics.types'

// Mock window.getComputedStyle для Ant Design Table
beforeAll(() => {
  Object.defineProperty(window, 'getComputedStyle', {
    value: () => ({
      getPropertyValue: () => '',
    }),
  })
  // Mock matchMedia
  Object.defineProperty(window, 'matchMedia', {
    value: vi.fn().mockImplementation((query) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
})

// Тестовые данные (Story 7.0: новый формат API — value + metric)
const mockTopRoutes: TopRoute[] = [
  {
    routeId: 'route-1',
    path: '/api/orders',
    value: 1520,
    metric: 'requests',
  },
  {
    routeId: 'route-2',
    path: '/api/users',
    value: 1050,
    metric: 'requests',
  },
  {
    routeId: 'route-3',
    path: '/api/payments',
    value: 580,
    metric: 'requests',
  },
]

describe('TopRoutesTable', () => {
  it('отображает таблицу с данными маршрутов', () => {
    render(<TopRoutesTable data={mockTopRoutes} />)

    expect(screen.getByTestId('top-routes-table')).toBeInTheDocument()

    // Проверяем пути маршрутов
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
    expect(screen.getByText('/api/users')).toBeInTheDocument()
    expect(screen.getByText('/api/payments')).toBeInTheDocument()
  })

  it('отображает заголовки колонок (Story 7.0: Path и Total Requests)', () => {
    render(<TopRoutesTable data={mockTopRoutes} />)

    expect(screen.getByText('Path')).toBeInTheDocument()
    expect(screen.getByText('Total Requests')).toBeInTheDocument()
  })

  it('отображает значения value как целые числа', () => {
    render(<TopRoutesTable data={mockTopRoutes} />)

    expect(screen.getByText('1520')).toBeInTheDocument()
    expect(screen.getByText('1050')).toBeInTheDocument()
    expect(screen.getByText('580')).toBeInTheDocument()
  })

  it('отображает пустую таблицу когда нет данных', () => {
    render(<TopRoutesTable data={[]} />)

    expect(screen.getByTestId('top-routes-table')).toBeInTheDocument()
  })

  it('поддерживает loading state', () => {
    render(<TopRoutesTable data={[]} loading={true} />)

    expect(screen.getByTestId('top-routes-table')).toBeInTheDocument()
    // Ant Design Table добавляет spin при loading
    expect(document.querySelector('.ant-spin')).toBeInTheDocument()
  })
})
