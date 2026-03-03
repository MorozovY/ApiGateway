// Тесты для LazyErrorBoundary компонента
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LazyErrorBoundary } from './LazyErrorBoundary'

// Компонент который выбрасывает ошибку для тестирования
const ErrorThrowingComponent = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw new Error('Тестовая ошибка загрузки chunk')
  }
  return <div data-testid="child-content">Контент загружен</div>
}

describe('LazyErrorBoundary', () => {
  // Подавляем console.error для тестов с ошибками
  const originalError = console.error
  beforeEach(() => {
    console.error = vi.fn()
  })

  afterEach(() => {
    console.error = originalError
  })

  it('рендерит children когда ошибки нет', () => {
    render(
      <LazyErrorBoundary>
        <ErrorThrowingComponent shouldThrow={false} />
      </LazyErrorBoundary>
    )

    expect(screen.getByTestId('child-content')).toBeInTheDocument()
    expect(screen.getByText('Контент загружен')).toBeInTheDocument()
  })

  it('отображает error UI когда происходит ошибка', () => {
    render(
      <LazyErrorBoundary>
        <ErrorThrowingComponent shouldThrow={true} />
      </LazyErrorBoundary>
    )

    // Error boundary рендерит Result компонент с текстом ошибки
    expect(screen.getByText('Ошибка загрузки')).toBeInTheDocument()
    expect(
      screen.getByText(
        'Не удалось загрузить компонент. Попробуйте обновить страницу.'
      )
    ).toBeInTheDocument()
  })

  it('отображает кнопку повторной попытки', () => {
    render(
      <LazyErrorBoundary>
        <ErrorThrowingComponent shouldThrow={true} />
      </LazyErrorBoundary>
    )

    const retryButton = screen.getByTestId('retry-button')
    expect(retryButton).toBeInTheDocument()
    expect(retryButton).toHaveTextContent('Обновить страницу')
  })

  it('вызывает window.location.reload при клике на retry', () => {
    // Mock window.location.reload
    const reloadMock = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { reload: reloadMock },
      writable: true,
    })

    render(
      <LazyErrorBoundary>
        <ErrorThrowingComponent shouldThrow={true} />
      </LazyErrorBoundary>
    )

    const retryButton = screen.getByTestId('retry-button')
    fireEvent.click(retryButton)

    expect(reloadMock).toHaveBeenCalled()
  })
})
