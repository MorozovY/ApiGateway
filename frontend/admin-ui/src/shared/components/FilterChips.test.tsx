// Тесты для FilterChips компонента (Story 8.8 — Unified Table Filters)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { FilterChips } from './FilterChips'
import type { FilterChip } from './FilterChips'

describe('FilterChips', () => {
  // Мок функции onClose
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('не рендерит ничего когда chips пустой массив', () => {
    const { container } = render(<FilterChips chips={[]} />)

    expect(container.firstChild).toBeNull()
  })

  it('рендерит chips для каждого фильтра', () => {
    const chips: FilterChip[] = [
      { key: 'search', label: 'Поиск: orders', onClose: mockOnClose },
      { key: 'status', label: 'Статус: Published', onClose: mockOnClose },
    ]

    render(<FilterChips chips={chips} />)

    expect(screen.getByText('Поиск: orders')).toBeInTheDocument()
    expect(screen.getByText('Статус: Published')).toBeInTheDocument()
  })

  it('вызывает onClose при клике на крестик', () => {
    const onCloseSearch = vi.fn()
    const onCloseStatus = vi.fn()

    const chips: FilterChip[] = [
      { key: 'search', label: 'Поиск: orders', onClose: onCloseSearch },
      { key: 'status', label: 'Статус: Published', onClose: onCloseStatus },
    ]

    render(<FilterChips chips={chips} />)

    // Находим все кнопки закрытия
    const closeButtons = screen.getAllByRole('img', { name: /close/i })

    // Кликаем на первую кнопку закрытия (для search)
    fireEvent.click(closeButtons[0])
    expect(onCloseSearch).toHaveBeenCalledTimes(1)
    expect(onCloseStatus).not.toHaveBeenCalled()
  })

  it('применяет кастомный цвет к Tag', () => {
    const chips: FilterChip[] = [
      { key: 'status', label: 'Статус: Published', color: 'green', onClose: mockOnClose },
    ]

    render(<FilterChips chips={chips} />)

    // Проверяем, что Tag присутствует (Ant Design Tag с цветом green)
    const tag = screen.getByText('Статус: Published').closest('.ant-tag')
    expect(tag).toHaveClass('ant-tag-green')
  })

  it('использует синий цвет по умолчанию когда color не указан', () => {
    const chips: FilterChip[] = [
      { key: 'search', label: 'Поиск: orders', onClose: mockOnClose },
    ]

    render(<FilterChips chips={chips} />)

    const tag = screen.getByText('Поиск: orders').closest('.ant-tag')
    expect(tag).toHaveClass('ant-tag-blue')
  })

  it('применяет кастомный className', () => {
    const chips: FilterChip[] = [
      { key: 'search', label: 'Поиск: orders', onClose: mockOnClose },
    ]

    render(<FilterChips chips={chips} className="custom-class" />)

    // Space должен иметь кастомный класс
    const container = screen.getByText('Поиск: orders').closest('.ant-space')
    expect(container).toHaveClass('custom-class')
  })

  it('поддерживает разные типы фильтров с правильными цветами', () => {
    const chips: FilterChip[] = [
      { key: 'search', label: 'Поиск: orders', color: 'blue', onClose: mockOnClose },
      { key: 'role', label: 'Роль: Admin', color: 'purple', onClose: mockOnClose },
      { key: 'user', label: 'Пользователь: admin', color: 'cyan', onClose: mockOnClose },
      { key: 'entity', label: 'Тип: route', color: 'orange', onClose: mockOnClose },
      { key: 'action', label: 'Действие: created', color: 'magenta', onClose: mockOnClose },
      { key: 'date', label: 'Дата: 2026-02-01', color: 'geekblue', onClose: mockOnClose },
    ]

    render(<FilterChips chips={chips} />)

    // Проверяем что все chips отрендерились
    expect(screen.getByText('Поиск: orders')).toBeInTheDocument()
    expect(screen.getByText('Роль: Admin')).toBeInTheDocument()
    expect(screen.getByText('Пользователь: admin')).toBeInTheDocument()
    expect(screen.getByText('Тип: route')).toBeInTheDocument()
    expect(screen.getByText('Действие: created')).toBeInTheDocument()
    expect(screen.getByText('Дата: 2026-02-01')).toBeInTheDocument()
  })

  it('имеет data-testid для E2E тестов', () => {
    const chips: FilterChip[] = [
      { key: 'search', label: 'Поиск: orders', onClose: mockOnClose },
    ]

    render(<FilterChips chips={chips} />)

    expect(screen.getByTestId('filter-chips')).toBeInTheDocument()
  })
})
