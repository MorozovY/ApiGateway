// Тесты для LoadingFallback компонента
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LoadingFallback } from './LoadingFallback'

describe('LoadingFallback', () => {
  it('рендерит контейнер с loading fallback', () => {
    render(<LoadingFallback />)

    const container = screen.getByTestId('loading-fallback')
    expect(container).toBeInTheDocument()
  })

  it('отображает Spin компонент с текстом "Загрузка..."', () => {
    render(<LoadingFallback />)

    // Ant Design Spin создаёт элемент с классом ant-spin
    const spinner = document.querySelector('.ant-spin')
    expect(spinner).toBeInTheDocument()

    // Проверяем что отображается tip текст (AC4)
    expect(screen.getByText('Загрузка...')).toBeInTheDocument()
  })

  it('контейнер имеет атрибут style для центрирования', () => {
    render(<LoadingFallback />)

    const container = screen.getByTestId('loading-fallback')
    // Проверяем что style атрибут содержит нужные значения
    const style = container.getAttribute('style')
    expect(style).toContain('display: flex')
    expect(style).toContain('flex-direction: column')
    expect(style).toContain('justify-content: center')
    expect(style).toContain('align-items: center')
    expect(style).toContain('gap: 16px')
  })
})
