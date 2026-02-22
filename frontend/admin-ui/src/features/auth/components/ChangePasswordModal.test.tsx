// Тесты для ChangePasswordModal компонента (Story 9.4)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { renderWithMockAuth } from '@/test/test-utils'
import { ChangePasswordModal } from './ChangePasswordModal'
import { changePasswordApi } from '../api/authApi'

// Мокаем API
vi.mock('../api/authApi', () => ({
  changePasswordApi: vi.fn(),
}))

// Хелпер для создания AxiosError с нужным статусом
function createAxiosError(status: number): AxiosError {
  const error = new AxiosError('Request failed')
  error.response = {
    status,
    statusText: status === 401 ? 'Unauthorized' : 'Internal Server Error',
    data: {},
    headers: {},
    config: { headers: {} as never },
  }
  return error
}

// Мокаем antd App.useApp() (Story 10.9 — theme-aware message)
const mockMessageSuccess = vi.fn()
const mockMessageError = vi.fn()
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd')
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({
        message: {
          success: (text: string) => mockMessageSuccess(text),
          error: (text: string) => mockMessageError(text),
          warning: vi.fn(),
          info: vi.fn(),
          loading: vi.fn(() => vi.fn()),
        },
        modal: { confirm: vi.fn() },
        notification: { success: vi.fn(), error: vi.fn() },
      }),
    },
  }
})

describe('ChangePasswordModal', () => {
  const mockOnClose = vi.fn()
  const mockChangePasswordApi = vi.mocked(changePasswordApi)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  // Subtask 5.1: Валидация (AC5)
  describe('валидация формы', () => {
    it('AC5 - кнопка Submit disabled при пустой форме', () => {
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      // При открытии modal кнопка должна быть disabled
      expect(screen.getByTestId('submit-button')).toBeDisabled()
    })

    it('AC5 - кнопка Submit disabled без текущего пароля', async () => {
      const user = userEvent.setup()
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      // Заполняем только новый пароль и подтверждение
      await user.type(screen.getByTestId('new-password'), 'newPassword123')
      await user.type(screen.getByTestId('confirm-password'), 'newPassword123')

      // Кнопка должна остаться disabled (нет текущего пароля)
      expect(screen.getByTestId('submit-button')).toBeDisabled()
    })

    it('AC5 - кнопка Submit disabled при коротком новом пароле', async () => {
      const user = userEvent.setup()
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      await user.type(screen.getByTestId('current-password'), 'currentPass')
      await user.type(screen.getByTestId('new-password'), 'short')
      await user.type(screen.getByTestId('confirm-password'), 'short')

      // Кнопка должна остаться disabled (пароль < 8 символов)
      expect(screen.getByTestId('submit-button')).toBeDisabled()
    })

    it('AC5 - кнопка Submit disabled когда пароли не совпадают', async () => {
      const user = userEvent.setup()
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      await user.type(screen.getByTestId('current-password'), 'currentPass')
      await user.type(screen.getByTestId('new-password'), 'newPassword123')
      await user.type(screen.getByTestId('confirm-password'), 'differentPassword')

      // Кнопка должна остаться disabled (пароли не совпадают)
      expect(screen.getByTestId('submit-button')).toBeDisabled()
    })

    it('AC5 - кнопка Submit enabled после заполнения всех полей корректно', async () => {
      const user = userEvent.setup()
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      // Заполняем все поля корректно
      await user.type(screen.getByTestId('current-password'), 'currentPass123')
      await user.type(screen.getByTestId('new-password'), 'newPassword123')
      await user.type(screen.getByTestId('confirm-password'), 'newPassword123')

      // Кнопка должна стать enabled
      await waitFor(() => {
        expect(screen.getByTestId('submit-button')).toBeEnabled()
      })
    })
  })

  // Subtask 5.2: Успешный submit
  describe('успешная смена пароля', () => {
    it('вызывает API с правильными данными при успешном submit', async () => {
      const user = userEvent.setup()
      mockChangePasswordApi.mockResolvedValueOnce(undefined)

      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      await user.type(screen.getByTestId('current-password'), 'currentPass123')
      await user.type(screen.getByTestId('new-password'), 'newPassword123')
      await user.type(screen.getByTestId('confirm-password'), 'newPassword123')

      await user.click(screen.getByTestId('submit-button'))

      await waitFor(() => {
        expect(mockChangePasswordApi).toHaveBeenCalledWith({
          currentPassword: 'currentPass123',
          newPassword: 'newPassword123',
        })
      })
    })

    it('показывает toast и закрывает modal при успехе', async () => {
      const user = userEvent.setup()
      mockChangePasswordApi.mockResolvedValueOnce(undefined)

      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      await user.type(screen.getByTestId('current-password'), 'currentPass123')
      await user.type(screen.getByTestId('new-password'), 'newPassword123')
      await user.type(screen.getByTestId('confirm-password'), 'newPassword123')

      await user.click(screen.getByTestId('submit-button'))

      await waitFor(() => {
        expect(mockMessageSuccess).toHaveBeenCalledWith('Пароль успешно изменён')
      })

      await waitFor(() => {
        expect(mockOnClose).toHaveBeenCalled()
      })
    })
  })

  // Subtask 5.3: Обработка ошибок
  describe('обработка ошибок', () => {
    it('AC4 - показывает inline error при неверном текущем пароле (401)', async () => {
      const user = userEvent.setup()
      mockChangePasswordApi.mockRejectedValueOnce(createAxiosError(401))

      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      await user.type(screen.getByTestId('current-password'), 'wrongPassword1')
      await user.type(screen.getByTestId('new-password'), 'newPassword123')
      await user.type(screen.getByTestId('confirm-password'), 'newPassword123')

      await user.click(screen.getByTestId('submit-button'))

      await waitFor(() => {
        expect(screen.getByText('Неверный текущий пароль')).toBeInTheDocument()
      })

      // Modal не должен закрыться
      expect(mockOnClose).not.toHaveBeenCalled()
    })

    it('показывает общую ошибку при других ошибках', async () => {
      const user = userEvent.setup()
      mockChangePasswordApi.mockRejectedValueOnce(createAxiosError(500))

      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      await user.type(screen.getByTestId('current-password'), 'currentPass123')
      await user.type(screen.getByTestId('new-password'), 'newPassword123')
      await user.type(screen.getByTestId('confirm-password'), 'newPassword123')

      await user.click(screen.getByTestId('submit-button'))

      await waitFor(() => {
        expect(mockMessageError).toHaveBeenCalledWith('Ошибка при смене пароля')
      })
    })
  })

  // AC6: Отмена операции
  describe('отмена операции', () => {
    it('AC6 - вызывает onClose при нажатии Отмена', async () => {
      const user = userEvent.setup()
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      await user.click(screen.getByTestId('cancel-button'))

      expect(mockOnClose).toHaveBeenCalled()
    })

    // Примечание: Escape обрабатывается Ant Design Modal автоматически (keyboard=true по умолчанию).
    // Юнит-тест для Escape не добавляем — это тестирование библиотеки, не нашего кода.
  })

  // Рендеринг
  describe('рендеринг', () => {
    it('рендерит все поля формы', () => {
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      // Проверяем заголовок modal (есть и на кнопке, используем role)
      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(screen.getByText('Текущий пароль')).toBeInTheDocument()
      expect(screen.getByText('Новый пароль')).toBeInTheDocument()
      expect(screen.getByText('Подтвердите пароль')).toBeInTheDocument()
      expect(screen.getByTestId('current-password')).toBeInTheDocument()
      expect(screen.getByTestId('new-password')).toBeInTheDocument()
      expect(screen.getByTestId('confirm-password')).toBeInTheDocument()
      expect(screen.getByTestId('submit-button')).toBeInTheDocument()
      expect(screen.getByText('Отмена')).toBeInTheDocument()
    })

    it('контролирует видимость через prop open', () => {
      renderWithMockAuth(
        <ChangePasswordModal open={true} onClose={mockOnClose} />
      )

      // При открытом modal поля формы доступны
      expect(screen.getByTestId('current-password')).toBeInTheDocument()
      expect(screen.getByTestId('new-password')).toBeInTheDocument()
      expect(screen.getByTestId('confirm-password')).toBeInTheDocument()
    })
  })
})
