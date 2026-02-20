// Тесты для TopRoutesTable (Story 6.5)
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

// Тестовые данные
const mockTopRoutes: TopRoute[] = [
  {
    routeId: 'route-1',
    path: '/api/orders',
    requestsPerSecond: 15.2,
    avgLatencyMs: 35,
    errorRate: 0.005, // < 1% — зелёный
  },
  {
    routeId: 'route-2',
    path: '/api/users',
    requestsPerSecond: 10.5,
    avgLatencyMs: 28,
    errorRate: 0.03, // 1-5% — жёлтый
  },
  {
    routeId: 'route-3',
    path: '/api/payments',
    requestsPerSecond: 5.8,
    avgLatencyMs: 120,
    errorRate: 0.08, // > 5% — красный
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

  it('отображает заголовки колонок', () => {
    render(<TopRoutesTable data={mockTopRoutes} />)

    expect(screen.getByText('Path')).toBeInTheDocument()
    expect(screen.getByText('RPS')).toBeInTheDocument()
    expect(screen.getByText('Avg Latency')).toBeInTheDocument()
    expect(screen.getByText('Error Rate')).toBeInTheDocument()
  })

  it('форматирует RPS с одним знаком после запятой', () => {
    render(<TopRoutesTable data={mockTopRoutes} />)

    expect(screen.getByText('15.2')).toBeInTheDocument()
    expect(screen.getByText('10.5')).toBeInTheDocument()
    expect(screen.getByText('5.8')).toBeInTheDocument()
  })

  it('отображает latency с единицей измерения', () => {
    render(<TopRoutesTable data={mockTopRoutes} />)

    expect(screen.getByText('35 ms')).toBeInTheDocument()
    expect(screen.getByText('28 ms')).toBeInTheDocument()
    expect(screen.getByText('120 ms')).toBeInTheDocument()
  })

  it('отображает error rate в процентах', () => {
    render(<TopRoutesTable data={mockTopRoutes} />)

    // Проверяем что значения отображаются в процентах
    expect(screen.getByText('0.50%')).toBeInTheDocument() // 0.005 * 100
    expect(screen.getByText('3.00%')).toBeInTheDocument() // 0.03 * 100
    expect(screen.getByText('8.00%')).toBeInTheDocument() // 0.08 * 100
  })

  it('отображает пустую таблицу когда нет данных', () => {
    render(<TopRoutesTable data={[]} />)

    expect(screen.getByTestId('top-routes-table')).toBeInTheDocument()
  })
})
