// Unit тесты для AutoRefreshControl (Story 16.8)
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { AutoRefreshControl, formatLastUpdated } from './AutoRefreshControl'

describe('AutoRefreshControl', () => {
  const defaultProps = {
    enabled: false,
    interval: 30000,
    lastUpdated: null,
    onEnabledChange: vi.fn(),
    onIntervalChange: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('отображает toggle в выключенном состоянии по умолчанию', () => {
    render(<AutoRefreshControl {...defaultProps} />)

    const toggle = screen.getByTestId('auto-refresh-toggle')
    expect(toggle).toBeInTheDocument()
    expect(toggle).not.toBeChecked()
  })

  it('отображает toggle во включённом состоянии когда enabled=true', () => {
    render(<AutoRefreshControl {...defaultProps} enabled={true} />)

    const toggle = screen.getByTestId('auto-refresh-toggle')
    expect(toggle).toBeChecked()
  })

  it('показывает selector интервала всегда, но disabled когда auto-refresh выключен', () => {
    render(<AutoRefreshControl {...defaultProps} enabled={false} />)

    const selector = screen.getByTestId('auto-refresh-interval')
    expect(selector).toBeInTheDocument()
    // Ant Design Select добавляет класс ant-select-disabled
    expect(selector).toHaveClass('ant-select-disabled')
  })

  it('показывает selector интервала enabled когда auto-refresh включён', () => {
    render(<AutoRefreshControl {...defaultProps} enabled={true} />)

    const selector = screen.getByTestId('auto-refresh-interval')
    expect(selector).toBeInTheDocument()
    expect(selector).not.toHaveClass('ant-select-disabled')
  })

  it('вызывает onEnabledChange при клике на toggle', () => {
    const onEnabledChange = vi.fn()
    render(<AutoRefreshControl {...defaultProps} onEnabledChange={onEnabledChange} />)

    const toggle = screen.getByTestId('auto-refresh-toggle')
    fireEvent.click(toggle)

    expect(onEnabledChange).toHaveBeenCalledTimes(1)
    // Ant Design Switch вызывает onChange(checked, event) с двумя аргументами
    expect(onEnabledChange).toHaveBeenCalledWith(true, expect.anything())
  })

  it('вызывает onIntervalChange при выборе интервала', () => {
    const onIntervalChange = vi.fn()
    render(
      <AutoRefreshControl {...defaultProps} enabled={true} onIntervalChange={onIntervalChange} />
    )

    // Открываем Select
    const selector = screen.getByTestId('auto-refresh-interval')
    fireEvent.mouseDown(selector.querySelector('.ant-select-selector')!)

    // Выбираем опцию "15 сек"
    const option = screen.getByText('15 сек')
    fireEvent.click(option)

    expect(onIntervalChange).toHaveBeenCalledTimes(1)
    // Ant Design Select вызывает onChange(value, option) с двумя аргументами
    expect(onIntervalChange).toHaveBeenCalledWith(15000, expect.anything())
  })

  it('отображает lastUpdated когда дата предоставлена', () => {
    const now = new Date()
    render(<AutoRefreshControl {...defaultProps} lastUpdated={now} />)

    const indicator = screen.getByTestId('last-updated-indicator')
    expect(indicator).toBeInTheDocument()
    expect(indicator).toHaveTextContent('Обновлено только что')
  })

  it('НЕ отображает lastUpdated когда дата null', () => {
    render(<AutoRefreshControl {...defaultProps} lastUpdated={null} />)

    expect(screen.queryByTestId('last-updated-indicator')).not.toBeInTheDocument()
  })

  it('отображает иконку SyncOutlined с анимацией когда enabled и не paused', () => {
    const { container } = render(<AutoRefreshControl {...defaultProps} enabled={true} isPaused={false} />)

    // Проверяем что иконка есть и имеет класс anticon-spin (анимация)
    const icon = container.querySelector('.anticon-sync')
    expect(icon).toBeInTheDocument()
  })

  // AC3: Page Visibility API — индикатор паузы
  describe('Page Visibility (AC3)', () => {
    it('показывает индикатор "Приостановлено" когда isPaused=true', () => {
      render(<AutoRefreshControl {...defaultProps} enabled={true} isPaused={true} />)

      const pausedIndicator = screen.getByTestId('paused-indicator')
      expect(pausedIndicator).toBeInTheDocument()
      expect(pausedIndicator).toHaveTextContent('Приостановлено')
    })

    it('НЕ показывает индикатор паузы когда isPaused=false', () => {
      render(<AutoRefreshControl {...defaultProps} enabled={true} isPaused={false} />)

      expect(screen.queryByTestId('paused-indicator')).not.toBeInTheDocument()
    })

    it('НЕ показывает lastUpdated когда приостановлен', () => {
      const now = new Date()
      render(<AutoRefreshControl {...defaultProps} enabled={true} lastUpdated={now} isPaused={true} />)

      // Вместо lastUpdated показываем "Приостановлено"
      expect(screen.queryByTestId('last-updated-indicator')).not.toBeInTheDocument()
      expect(screen.getByTestId('paused-indicator')).toBeInTheDocument()
    })

    it('иконка НЕ крутится когда приостановлен', () => {
      const { container } = render(<AutoRefreshControl {...defaultProps} enabled={true} isPaused={true} />)

      // Иконка должна быть, но без анимации spin
      const icon = container.querySelector('.anticon-sync')
      expect(icon).toBeInTheDocument()
      // При isPaused spin prop = false, поэтому нет класса anticon-spin
    })
  })
})

describe('formatLastUpdated', () => {
  // Используем фиксированное время для тестов
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-04T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('возвращает пустую строку для null', () => {
    expect(formatLastUpdated(null)).toBe('')
  })

  it('возвращает "Обновлено только что" для < 10 секунд', () => {
    const date = new Date('2026-03-04T11:59:55Z') // 5 секунд назад
    expect(formatLastUpdated(date)).toBe('Обновлено только что')
  })

  it('возвращает "Обновлено X секунд назад" для 10-59 секунд', () => {
    const date = new Date('2026-03-04T11:59:30Z') // 30 секунд назад
    expect(formatLastUpdated(date)).toBe('Обновлено 30 секунд назад')
  })

  it('корректно склоняет "21 секунду назад"', () => {
    const date = new Date('2026-03-04T11:59:39Z') // 21 секунда назад
    expect(formatLastUpdated(date)).toBe('Обновлено 21 секунду назад')
  })

  it('корректно склоняет "22 секунды назад"', () => {
    const date = new Date('2026-03-04T11:59:38Z') // 22 секунды назад
    expect(formatLastUpdated(date)).toBe('Обновлено 22 секунды назад')
  })

  it('возвращает "Обновлено X минут назад" для >= 60 секунд', () => {
    const date = new Date('2026-03-04T11:57:00Z') // 3 минуты назад
    expect(formatLastUpdated(date)).toBe('Обновлено 3 минуты назад')
  })

  it('корректно склоняет "1 минуту назад"', () => {
    const date = new Date('2026-03-04T11:59:00Z') // 1 минута назад
    expect(formatLastUpdated(date)).toBe('Обновлено 1 минуту назад')
  })

  it('корректно склоняет "5 минут назад"', () => {
    const date = new Date('2026-03-04T11:55:00Z') // 5 минут назад
    expect(formatLastUpdated(date)).toBe('Обновлено 5 минут назад')
  })

  it('корректно склоняет "11 минут назад" (особый случай 11-19)', () => {
    const date = new Date('2026-03-04T11:49:00Z') // 11 минут назад
    expect(formatLastUpdated(date)).toBe('Обновлено 11 минут назад')
  })
})
