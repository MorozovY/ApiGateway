// Тесты для PageInfoBlock компонента (Story 15.4)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import { PageInfoBlock } from './PageInfoBlock'
import type { PageKey } from '../config/pageDescriptions'

// Мок localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key]
    }),
    clear: vi.fn(() => {
      store = {}
    }),
  }
})()

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Обёртка для тестов с Ant Design ConfigProvider
function renderWithConfig(ui: React.ReactElement) {
  return render(<ConfigProvider>{ui}</ConfigProvider>)
}

describe('PageInfoBlock', () => {
  const defaultProps = {
    pageKey: 'dashboard' as PageKey,
    title: 'Test Page',
    description: 'Описание тестовой страницы',
    features: [
      'Первая возможность',
      'Вторая возможность',
      'Третья возможность',
    ],
  }

  beforeEach(() => {
    vi.clearAllMocks()
    localStorageMock.clear()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('рендерит заголовок и описание', () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    expect(screen.getByText('Test Page')).toBeInTheDocument()
    expect(screen.getByText(/Описание тестовой страницы/)).toBeInTheDocument()
  })

  it('рендерит список возможностей когда развёрнут', async () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    // Блок по умолчанию развёрнут для новых пользователей
    await waitFor(() => {
      expect(screen.getByText('Первая возможность')).toBeInTheDocument()
      expect(screen.getByText('Вторая возможность')).toBeInTheDocument()
      expect(screen.getByText('Третья возможность')).toBeInTheDocument()
    })
  })

  it('по умолчанию развёрнут для новых пользователей', () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    const infoBlock = screen.getByTestId('page-info-block')
    expect(infoBlock).toHaveAttribute('data-expanded', 'true')
  })

  it('сохраняет collapsed состояние в localStorage', async () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    // Находим header для клика (первый интерактивный элемент в Collapse)
    const header = screen.getByRole('button')

    // Сворачиваем блок
    fireEvent.click(header)

    await waitFor(() => {
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        'pageInfoBlock_dashboard',
        'collapsed'
      )
    })
  })

  it('сохраняет expanded состояние в localStorage', async () => {
    // Устанавливаем начальное состояние как collapsed
    localStorageMock.getItem.mockReturnValue('collapsed')

    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    const header = screen.getByRole('button')

    // Разворачиваем блок
    fireEvent.click(header)

    await waitFor(() => {
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        'pageInfoBlock_dashboard',
        'expanded'
      )
    })
  })

  it('восстанавливает collapsed состояние из localStorage', () => {
    localStorageMock.getItem.mockReturnValue('collapsed')

    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    const infoBlock = screen.getByTestId('page-info-block')
    expect(infoBlock).toHaveAttribute('data-expanded', 'false')
  })

  it('восстанавливает expanded состояние из localStorage', async () => {
    localStorageMock.getItem.mockReturnValue('expanded')

    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    const infoBlock = screen.getByTestId('page-info-block')
    expect(infoBlock).toHaveAttribute('data-expanded', 'true')

    await waitFor(() => {
      expect(screen.getByText('Первая возможность')).toBeInTheDocument()
    })
  })

  it('использует уникальный ключ localStorage для каждой страницы', async () => {
    const { unmount } = renderWithConfig(
      <PageInfoBlock {...defaultProps} pageKey="routes" />
    )

    const header = screen.getByRole('button')
    fireEvent.click(header)

    await waitFor(() => {
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        'pageInfoBlock_routes',
        'collapsed'
      )
    })

    unmount()

    renderWithConfig(<PageInfoBlock {...defaultProps} pageKey="metrics" />)

    const header2 = screen.getByRole('button')
    fireEvent.click(header2)

    await waitFor(() => {
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        'pageInfoBlock_metrics',
        'collapsed'
      )
    })
  })

  it('имеет data-testid для E2E тестов', () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    expect(screen.getByTestId('page-info-block')).toBeInTheDocument()
  })

  it('содержит иконку InfoCircleOutlined', () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    // Иконка рендерится как SVG с role="img"
    const icons = screen.getAllByRole('img')
    expect(icons.length).toBeGreaterThan(0)
  })

  it('обрабатывает пустой массив features', () => {
    renderWithConfig(
      <PageInfoBlock
        {...defaultProps}
        features={[]}
      />
    )

    // Компонент должен отрендериться без ошибок
    expect(screen.getByTestId('page-info-block')).toBeInTheDocument()
  })

  it('обрабатывает ошибку localStorage gracefully', () => {
    // Симулируем ошибку localStorage
    localStorageMock.getItem.mockImplementation(() => {
      throw new Error('localStorage is not available')
    })
    localStorageMock.setItem.mockImplementation(() => {
      throw new Error('localStorage is not available')
    })

    // Компонент не должен упасть
    expect(() => {
      renderWithConfig(<PageInfoBlock {...defaultProps} />)
    }).not.toThrow()

    // И должен отрендериться в развёрнутом состоянии (default)
    const infoBlock = screen.getByTestId('page-info-block')
    expect(infoBlock).toHaveAttribute('data-expanded', 'true')
  })

  // ============================================
  // AC4: Responsive / Mobile адаптивность
  // ============================================

  it('не имеет фиксированной ширины (AC4 — responsive)', () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    const infoBlock = screen.getByTestId('page-info-block')
    const style = window.getComputedStyle(infoBlock)

    // Компонент не должен иметь фиксированную ширину
    // которая сломает отображение на мобильных устройствах
    expect(style.width).not.toMatch(/^\d+px$/)
  })

  it('использует Ant Design Collapse (AC4 — адаптивный компонент)', () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    const infoBlock = screen.getByTestId('page-info-block')

    // Ant Design Collapse добавляет класс ant-collapse
    expect(infoBlock.classList.contains('ant-collapse')).toBe(true)
  })

  it('features список использует ul без горизонтального overflow', () => {
    renderWithConfig(<PageInfoBlock {...defaultProps} />)

    const featuresList = screen.getByTestId('page-info-features')
    expect(featuresList.tagName).toBe('UL')

    // Проверяем что список не имеет свойств, которые могут сломать mobile view
    const style = window.getComputedStyle(featuresList)
    expect(style.whiteSpace).not.toBe('nowrap')
  })
})
