// Тесты для LoadGeneratorProgress (Story 8.9, AC3)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LoadGeneratorProgress } from './LoadGeneratorProgress'
import type { LoadGeneratorState } from '../types/loadGenerator.types'

describe('LoadGeneratorProgress', () => {
  // Мокаем Date.now для стабильных тестов
  const mockNow = 1708500000000 // Фиксированный timestamp

  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(mockNow)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('отображает карточку Progress', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow - 5000, // 5 секунд назад
      sentCount: 10,
      successCount: 9,
      errorCount: 1,
      lastError: null,
      averageResponseTime: 45,
    }

    render(<LoadGeneratorProgress state={state} />)

    expect(screen.getByTestId('load-generator-progress')).toBeInTheDocument()
    expect(screen.getByText('Progress')).toBeInTheDocument()
  })

  it('отображает счётчик Sent', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow,
      sentCount: 25,
      successCount: 20,
      errorCount: 5,
      lastError: null,
      averageResponseTime: 30,
    }

    render(<LoadGeneratorProgress state={state} />)

    const sentStat = screen.getByTestId('stat-sent')
    expect(sentStat).toBeInTheDocument()
    expect(sentStat).toHaveTextContent('25')
  })

  it('отображает счётчик Success', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow,
      sentCount: 20,
      successCount: 18,
      errorCount: 2,
      lastError: null,
      averageResponseTime: 50,
    }

    render(<LoadGeneratorProgress state={state} />)

    const successStat = screen.getByTestId('stat-success')
    expect(successStat).toBeInTheDocument()
    expect(successStat).toHaveTextContent('18')
  })

  it('отображает счётчик Errors', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow,
      sentCount: 20,
      successCount: 15,
      errorCount: 5,
      lastError: 'Network error',
      averageResponseTime: 60,
    }

    render(<LoadGeneratorProgress state={state} />)

    const errorsStat = screen.getByTestId('stat-errors')
    expect(errorsStat).toBeInTheDocument()
    expect(errorsStat).toHaveTextContent('5')
  })

  it('отображает elapsed time', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow - 15000, // 15 секунд назад
      sentCount: 30,
      successCount: 28,
      errorCount: 2,
      lastError: null,
      averageResponseTime: 40,
    }

    render(<LoadGeneratorProgress state={state} />)

    const elapsedStat = screen.getByTestId('stat-elapsed')
    expect(elapsedStat).toBeInTheDocument()
    expect(elapsedStat).toHaveTextContent('15s')
  })

  it('отображает elapsed time в минутах для длительных сессий', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow - 90000, // 90 секунд назад = 1m 30s
      sentCount: 100,
      successCount: 95,
      errorCount: 5,
      lastError: null,
      averageResponseTime: 35,
    }

    render(<LoadGeneratorProgress state={state} />)

    const elapsedStat = screen.getByTestId('stat-elapsed')
    expect(elapsedStat).toHaveTextContent('1m 30s')
  })

  it('отображает average response time', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow,
      sentCount: 50,
      successCount: 48,
      errorCount: 2,
      lastError: null,
      averageResponseTime: 42.7,
    }

    render(<LoadGeneratorProgress state={state} />)

    const avgStat = screen.getByTestId('stat-avg-response')
    expect(avgStat).toBeInTheDocument()
    // toFixed(0) округляет до целого
    expect(avgStat).toHaveTextContent('43')
  })

  it('отображает "-" когда averageResponseTime равен null', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow,
      sentCount: 0,
      successCount: 0,
      errorCount: 0,
      lastError: null,
      averageResponseTime: null,
    }

    render(<LoadGeneratorProgress state={state} />)

    const avgStat = screen.getByTestId('stat-avg-response')
    expect(avgStat).toBeInTheDocument()
    expect(avgStat).toHaveTextContent('-')
  })

  it('отображает last error когда есть ошибка', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow,
      sentCount: 10,
      successCount: 8,
      errorCount: 2,
      lastError: 'Connection refused',
      averageResponseTime: 55,
    }

    render(<LoadGeneratorProgress state={state} />)

    expect(screen.getByTestId('last-error')).toBeInTheDocument()
    expect(screen.getByText(/Connection refused/)).toBeInTheDocument()
  })

  it('не отображает last error когда ошибок нет', () => {
    const state: LoadGeneratorState = {
      status: 'running',
      startTime: mockNow,
      sentCount: 10,
      successCount: 10,
      errorCount: 0,
      lastError: null,
      averageResponseTime: 45,
    }

    render(<LoadGeneratorProgress state={state} />)

    expect(screen.queryByTestId('last-error')).not.toBeInTheDocument()
  })

  it('отображает "0s" когда startTime равен null', () => {
    const state: LoadGeneratorState = {
      status: 'idle',
      startTime: null,
      sentCount: 0,
      successCount: 0,
      errorCount: 0,
      lastError: null,
      averageResponseTime: null,
    }

    render(<LoadGeneratorProgress state={state} />)

    const elapsedStat = screen.getByTestId('stat-elapsed')
    expect(elapsedStat).toHaveTextContent('0s')
  })
})
