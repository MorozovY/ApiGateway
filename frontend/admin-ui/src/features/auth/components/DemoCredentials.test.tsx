// Тесты для DemoCredentials компонента (Story 9.5)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import { DemoCredentials } from './DemoCredentials'
import axios from '@shared/utils/axios'

// Мокаем axios
vi.mock('@shared/utils/axios', () => ({
  default: {
    post: vi.fn(),
  },
}))

// Мокаем antd message
const mockMessageSuccess = vi.fn()
const mockMessageError = vi.fn()
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd')
  return {
    ...actual,
    message: {
      success: (text: string) => mockMessageSuccess(text),
      error: (text: string) => mockMessageError(text),
    },
  }
})

describe('DemoCredentials', () => {
  const mockAxiosPost = vi.mocked(axios.post)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  // AC1: Рендеринг credentials
  describe('рендеринг credentials (AC1)', () => {
    it('отображает секцию с заголовком Демо-доступ', () => {
      renderWithMockAuth(<DemoCredentials />)

      expect(screen.getByTestId('demo-credentials-card')).toBeInTheDocument()
      expect(screen.getByText('🔐 Демо-доступ')).toBeInTheDocument()
    })

    it('отображает три набора credentials', () => {
      renderWithMockAuth(<DemoCredentials />)

      expect(screen.getByTestId('demo-credentials-table')).toBeInTheDocument()
      expect(screen.getByText('developer / developer123')).toBeInTheDocument()
      expect(screen.getByText('security / security123')).toBeInTheDocument()
      expect(screen.getByText('admin / admin123')).toBeInTheDocument()
    })

    it('отображает роли пользователей', () => {
      renderWithMockAuth(<DemoCredentials />)

      expect(screen.getByText('Developer')).toBeInTheDocument()
      expect(screen.getByText('Security')).toBeInTheDocument()
      expect(screen.getByText('Admin')).toBeInTheDocument()
    })
  })

  // AC2: Клик по логину заполняет форму
  describe('выбор учётных данных (AC2)', () => {
    it('вызывает onSelect при клике на логин developer', async () => {
      const user = userEvent.setup()
      const mockOnSelect = vi.fn()

      renderWithMockAuth(<DemoCredentials onSelect={mockOnSelect} />)

      await user.click(screen.getByTestId('demo-login-developer'))

      expect(mockOnSelect).toHaveBeenCalledWith('developer', 'developer123')
    })

    it('вызывает onSelect при клике на логин admin', async () => {
      const user = userEvent.setup()
      const mockOnSelect = vi.fn()

      renderWithMockAuth(<DemoCredentials onSelect={mockOnSelect} />)

      await user.click(screen.getByTestId('demo-login-admin'))

      expect(mockOnSelect).toHaveBeenCalledWith('admin', 'admin123')
    })

    it('вызывает onSelect при клике на логин security', async () => {
      const user = userEvent.setup()
      const mockOnSelect = vi.fn()

      renderWithMockAuth(<DemoCredentials onSelect={mockOnSelect} />)

      await user.click(screen.getByTestId('demo-login-security'))

      expect(mockOnSelect).toHaveBeenCalledWith('security', 'security123')
    })
  })

  // AC4: Кнопка сброса паролей
  describe('сброс паролей (AC4)', () => {
    it('отображает кнопку сброса паролей', () => {
      renderWithMockAuth(<DemoCredentials />)

      expect(screen.getByTestId('reset-passwords-button')).toBeInTheDocument()
      expect(screen.getByText('Сбросить пароли')).toBeInTheDocument()
    })

    it('вызывает API при клике на кнопку сброса', async () => {
      const user = userEvent.setup()
      mockAxiosPost.mockResolvedValueOnce({ data: { message: 'OK' } })

      renderWithMockAuth(<DemoCredentials />)

      await user.click(screen.getByTestId('reset-passwords-button'))

      expect(mockAxiosPost).toHaveBeenCalledWith('/api/v1/auth/reset-demo-passwords')
    })

    it('показывает success message при успешном сбросе', async () => {
      const user = userEvent.setup()
      mockAxiosPost.mockResolvedValueOnce({ data: { message: 'OK' } })

      renderWithMockAuth(<DemoCredentials />)

      await user.click(screen.getByTestId('reset-passwords-button'))

      await waitFor(() => {
        expect(mockMessageSuccess).toHaveBeenCalledWith('Пароли сброшены')
      })
    })

    it('показывает error message при ошибке сброса', async () => {
      const user = userEvent.setup()
      mockAxiosPost.mockRejectedValueOnce(new Error('Network error'))

      renderWithMockAuth(<DemoCredentials />)

      await user.click(screen.getByTestId('reset-passwords-button'))

      await waitFor(() => {
        expect(mockMessageError).toHaveBeenCalledWith('Ошибка при сбросе паролей')
      })
    })

    it('показывает loading состояние кнопки во время сброса', async () => {
      const user = userEvent.setup()
      // Создаём промис который не резолвится сразу
      let resolvePromise: (value: unknown) => void
      const pendingPromise = new Promise((resolve) => {
        resolvePromise = resolve
      })
      mockAxiosPost.mockReturnValueOnce(pendingPromise as Promise<unknown>)

      renderWithMockAuth(<DemoCredentials />)

      const button = screen.getByTestId('reset-passwords-button')
      await user.click(button)

      // Проверяем, что кнопка в loading состоянии (Ant Design добавляет класс)
      await waitFor(() => {
        expect(button).toHaveClass('ant-btn-loading')
      })

      // Завершаем промис
      resolvePromise!({ data: { message: 'OK' } })

      // После завершения loading должен исчезнуть
      await waitFor(() => {
        expect(button).not.toHaveClass('ant-btn-loading')
      })
    })
  })

  // AC5: Подсказка о сбросе паролей
  describe('подсказка (AC5)', () => {
    it('отображает подсказку о сбросе паролей', () => {
      renderWithMockAuth(<DemoCredentials />)

      expect(screen.getByTestId('demo-hint')).toBeInTheDocument()
      expect(screen.getByText(/Не работает\?/)).toBeInTheDocument()
      expect(screen.getByText('Сбросить пароли')).toBeInTheDocument()
    })
  })
})
