// Тесты для LoadGeneratorSummary (Story 8.9, AC4)
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LoadGeneratorSummary } from './LoadGeneratorSummary'
import type { LoadGeneratorSummary as SummaryType } from '../types/loadGenerator.types'

describe('LoadGeneratorSummary', () => {
  const mockOnReset = vi.fn()

  const baseSummary: SummaryType = {
    totalRequests: 100,
    successCount: 95,
    errorCount: 5,
    durationMs: 10000, // 10 секунд
    successRate: 95,
    averageResponseTime: 45,
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('отображает карточку Summary', () => {
    render(<LoadGeneratorSummary summary={baseSummary} onReset={mockOnReset} />)

    expect(screen.getByTestId('load-generator-summary')).toBeInTheDocument()
    expect(screen.getByText('Summary')).toBeInTheDocument()
  })

  it('отображает Total Requests', () => {
    render(<LoadGeneratorSummary summary={baseSummary} onReset={mockOnReset} />)

    expect(screen.getByTestId('summary-total')).toBeInTheDocument()
    expect(screen.getByText('100')).toBeInTheDocument()
    expect(screen.getByText('Total Requests')).toBeInTheDocument()
  })

  it('отображает Duration в секундах', () => {
    const summary: SummaryType = {
      ...baseSummary,
      durationMs: 15500, // 15.5 секунд
    }

    render(<LoadGeneratorSummary summary={summary} onReset={mockOnReset} />)

    expect(screen.getByTestId('summary-duration')).toBeInTheDocument()
    expect(screen.getByText('15.5s')).toBeInTheDocument()
  })

  it('отображает Duration в минутах для длительных сессий', () => {
    const summary: SummaryType = {
      ...baseSummary,
      durationMs: 125000, // 125 секунд = 2m 5s
    }

    render(<LoadGeneratorSummary summary={summary} onReset={mockOnReset} />)

    expect(screen.getByText('2m 5s')).toBeInTheDocument()
  })

  it('отображает Success Rate', () => {
    render(<LoadGeneratorSummary summary={baseSummary} onReset={mockOnReset} />)

    const statElement = screen.getByTestId('summary-success-rate')
    expect(statElement).toBeInTheDocument()
    // Ant Design Statistic разбивает число на int и decimal части
    // Проверяем что значение содержится в элементе
    expect(statElement).toHaveTextContent('95')
  })

  it('применяет зелёный цвет для success rate >= 95%', () => {
    const summary: SummaryType = {
      ...baseSummary,
      successRate: 98,
    }

    render(<LoadGeneratorSummary summary={summary} onReset={mockOnReset} />)

    const statElement = screen.getByTestId('summary-success-rate')
    const valueElement = statElement.querySelector('.ant-statistic-content-value')
    expect(valueElement).toHaveStyle({ color: '#52c41a' })
  })

  it('применяет жёлтый цвет для success rate 80-95%', () => {
    const summary: SummaryType = {
      ...baseSummary,
      successRate: 85,
    }

    render(<LoadGeneratorSummary summary={summary} onReset={mockOnReset} />)

    const statElement = screen.getByTestId('summary-success-rate')
    const valueElement = statElement.querySelector('.ant-statistic-content-value')
    expect(valueElement).toHaveStyle({ color: '#faad14' })
  })

  it('применяет красный цвет для success rate < 80%', () => {
    const summary: SummaryType = {
      ...baseSummary,
      successRate: 70,
    }

    render(<LoadGeneratorSummary summary={summary} onReset={mockOnReset} />)

    const statElement = screen.getByTestId('summary-success-rate')
    const valueElement = statElement.querySelector('.ant-statistic-content-value')
    expect(valueElement).toHaveStyle({ color: '#ff4d4f' })
  })

  it('отображает Average Response time', () => {
    render(<LoadGeneratorSummary summary={baseSummary} onReset={mockOnReset} />)

    expect(screen.getByTestId('summary-avg-response')).toBeInTheDocument()
    expect(screen.getByText('45')).toBeInTheDocument()
  })

  it('отображает "-" когда averageResponseTime равен null', () => {
    const summary: SummaryType = {
      ...baseSummary,
      averageResponseTime: null,
    }

    render(<LoadGeneratorSummary summary={summary} onReset={mockOnReset} />)

    const avgElement = screen.getByTestId('summary-avg-response')
    expect(avgElement).toHaveTextContent('-')
  })

  it('отображает кнопку Reset', () => {
    render(<LoadGeneratorSummary summary={baseSummary} onReset={mockOnReset} />)

    expect(screen.getByTestId('reset-button')).toBeInTheDocument()
    expect(screen.getByText('Reset')).toBeInTheDocument()
  })

  it('вызывает onReset при клике на кнопку Reset', () => {
    render(<LoadGeneratorSummary summary={baseSummary} onReset={mockOnReset} />)

    const resetButton = screen.getByTestId('reset-button')
    fireEvent.click(resetButton)

    expect(mockOnReset).toHaveBeenCalledTimes(1)
  })

  it('корректно округляет дробное averageResponseTime', () => {
    const summary: SummaryType = {
      ...baseSummary,
      averageResponseTime: 42.789,
    }

    render(<LoadGeneratorSummary summary={summary} onReset={mockOnReset} />)

    // toFixed(0) должен округлить до 43
    expect(screen.getByText('43')).toBeInTheDocument()
  })

  it('отображает все четыре статистики', () => {
    render(<LoadGeneratorSummary summary={baseSummary} onReset={mockOnReset} />)

    expect(screen.getByText('Total Requests')).toBeInTheDocument()
    expect(screen.getByText('Duration')).toBeInTheDocument()
    expect(screen.getByText('Success Rate')).toBeInTheDocument()
    expect(screen.getByText('Avg Response')).toBeInTheDocument()
  })
})
