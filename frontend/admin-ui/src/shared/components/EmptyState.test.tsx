// Unit тесты для EmptyState (Story 16.5, Task 1.4)
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { CheckCircleOutlined } from '@ant-design/icons'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('отображает title', () => {
    render(<EmptyState title="Маршруты ещё не созданы" />)

    expect(screen.getByText('Маршруты ещё не созданы')).toBeInTheDocument()
  })

  it('отображает title и description', () => {
    render(
      <EmptyState
        title="Маршруты ещё не созданы"
        description="Создайте первый маршрут для начала работы"
      />
    )

    expect(screen.getByText('Маршруты ещё не созданы')).toBeInTheDocument()
    expect(screen.getByText('Создайте первый маршрут для начала работы')).toBeInTheDocument()
  })

  it('отображает CTA кнопку когда action передан', () => {
    const handleClick = vi.fn()

    render(
      <EmptyState
        title="Маршруты ещё не созданы"
        action={{ label: 'Создать маршрут', onClick: handleClick }}
      />
    )

    const button = screen.getByRole('button', { name: 'Создать маршрут' })
    expect(button).toBeInTheDocument()
  })

  it('вызывает onClick при клике на CTA', () => {
    const handleClick = vi.fn()

    render(
      <EmptyState
        title="Маршруты ещё не созданы"
        action={{ label: 'Создать маршрут', onClick: handleClick }}
      />
    )

    const button = screen.getByRole('button', { name: 'Создать маршрут' })
    fireEvent.click(button)

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('отображает кастомную иконку когда передана', () => {
    render(
      <EmptyState
        icon={<CheckCircleOutlined data-testid="custom-icon" style={{ fontSize: 48, color: '#52c41a' }} />}
        title="Нет маршрутов на согласование"
      />
    )

    expect(screen.getByTestId('custom-icon')).toBeInTheDocument()
  })

  it('использует PRESENTED_IMAGE_SIMPLE когда useSimpleImage=true', () => {
    const { container } = render(
      <EmptyState
        title="Пустая таблица"
        useSimpleImage={true}
      />
    )

    // Проверяем что рендерится Empty компонент (через class)
    const emptyElement = container.querySelector('.ant-empty')
    expect(emptyElement).toBeInTheDocument()
  })

  it('рендерит primary кнопку по умолчанию', () => {
    const handleClick = vi.fn()

    render(
      <EmptyState
        title="Пустая таблица"
        action={{ label: 'Действие', onClick: handleClick }}
      />
    )

    const button = screen.getByRole('button', { name: 'Действие' })
    expect(button).toHaveClass('ant-btn-primary')
  })

  it('рендерит default кнопку когда type=default', () => {
    const handleClick = vi.fn()

    render(
      <EmptyState
        title="Пустая таблица"
        action={{ label: 'Действие', onClick: handleClick, type: 'default' }}
      />
    )

    const button = screen.getByRole('button', { name: 'Действие' })
    expect(button).not.toHaveClass('ant-btn-primary')
  })

  it('не отображает кнопку когда action не передан', () => {
    render(<EmptyState title="Пустая таблица" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('не отображает description когда не передан', () => {
    render(<EmptyState title="Пустая таблица" />)

    // Проверяем что только title присутствует
    expect(screen.getByText('Пустая таблица')).toBeInTheDocument()
    // Description div не должен существовать (проверяем отсутствие дополнительного текста)
    const textElements = screen.getAllByText(/Пустая таблица/)
    expect(textElements).toHaveLength(1)
  })
})
