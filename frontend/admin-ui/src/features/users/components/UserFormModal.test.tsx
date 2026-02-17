// Тесты для UserFormModal компонента (Story 2.6)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import UserFormModal from './UserFormModal'
import * as usersApi from '../api/usersApi'
import type { User } from '../types/user.types'

// Мокаем API
vi.mock('../api/usersApi', () => ({
  createUser: vi.fn(),
  updateUser: vi.fn(),
}))

const mockCreateUser = usersApi.createUser as ReturnType<typeof vi.fn>
const mockUpdateUser = usersApi.updateUser as ReturnType<typeof vi.fn>

// Тестовый пользователь для режима редактирования
const testUser: User = {
  id: '1',
  username: 'testuser',
  email: 'test@company.com',
  role: 'developer',
  isActive: true,
  createdAt: '2026-02-01T10:00:00Z',
}

describe('UserFormModal', () => {
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    mockCreateUser.mockResolvedValue({ ...testUser, id: '2' })
    mockUpdateUser.mockResolvedValue(testUser)
  })

  describe('режим создания', () => {
    it('рендерит форму с заголовком Add User', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.getByText('Add User')).toBeInTheDocument()
    })

    it('показывает все поля включая password', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/role/i)).toBeInTheDocument()
    })

    it('валидирует обязательные поля', async () => {
      const user = userEvent.setup()

      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      // Кликаем Create без заполнения полей
      await user.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(screen.getByText('Username обязателен')).toBeInTheDocument()
      })
    })

    it('валидирует формат email', async () => {
      const user = userEvent.setup()

      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      // Заполняем username и password
      await user.type(screen.getByLabelText(/username/i), 'newuser')
      await user.type(screen.getByLabelText(/password/i), 'password123')

      // Вводим некорректный email
      await user.type(screen.getByLabelText(/email/i), 'invalid-email')

      // Кликаем Create
      await user.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(screen.getByText('Некорректный формат email')).toBeInTheDocument()
      })
    })

    it('валидирует минимальную длину пароля', async () => {
      const user = userEvent.setup()

      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      // Заполняем поля с коротким паролем
      await user.type(screen.getByLabelText(/username/i), 'newuser')
      await user.type(screen.getByLabelText(/email/i), 'test@example.com')
      await user.type(screen.getByLabelText(/password/i), 'short')

      // Кликаем Create
      await user.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(screen.getByText('Минимум 8 символов')).toBeInTheDocument()
      })
    })

    it('вызывает createUser с правильными данными', async () => {
      const user = userEvent.setup()

      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      // Заполняем форму
      await user.type(screen.getByLabelText(/username/i), 'newuser')
      await user.type(screen.getByLabelText(/email/i), 'newuser@company.com')
      await user.type(screen.getByLabelText(/password/i), 'password123')

      // Выбираем роль (по умолчанию developer)
      await user.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(mockCreateUser).toHaveBeenCalledWith({
          username: 'newuser',
          email: 'newuser@company.com',
          password: 'password123',
          role: 'developer',
        })
      })
    })

    it('закрывает модальное окно после успешного создания', async () => {
      const user = userEvent.setup()

      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      // Заполняем форму
      await user.type(screen.getByLabelText(/username/i), 'newuser')
      await user.type(screen.getByLabelText(/email/i), 'newuser@company.com')
      await user.type(screen.getByLabelText(/password/i), 'password123')

      await user.click(screen.getByRole('button', { name: /create/i }))

      await waitFor(() => {
        expect(mockOnClose).toHaveBeenCalled()
      })
    })
  })

  describe('режим редактирования', () => {
    it('рендерит форму с заголовком Edit User', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={testUser} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.getByText('Edit User')).toBeInTheDocument()
    })

    it('не показывает поле username в режиме редактирования', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={testUser} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.queryByLabelText(/username/i)).not.toBeInTheDocument()
    })

    it('не показывает поле password в режиме редактирования', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={testUser} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
    })

    it('показывает информационное сообщение о пароле', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={testUser} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.getByText(/пароль нельзя изменить/i)).toBeInTheDocument()
    })

    it('предзаполняет форму данными пользователя', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={testUser} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.getByLabelText(/email/i)).toHaveValue('test@company.com')
    })

    it('вызывает updateUser с изменёнными данными', async () => {
      const user = userEvent.setup()

      renderWithMockAuth(
        <UserFormModal open={true} user={testUser} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      // Меняем email
      const emailInput = screen.getByLabelText(/email/i)
      await user.clear(emailInput)
      await user.type(emailInput, 'newemail@company.com')

      await user.click(screen.getByRole('button', { name: /save/i }))

      await waitFor(() => {
        expect(mockUpdateUser).toHaveBeenCalledWith('1', {
          email: 'newemail@company.com',
        })
      })
    })

    it('показывает кнопку Save вместо Create', () => {
      renderWithMockAuth(
        <UserFormModal open={true} user={testUser} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /create/i })).not.toBeInTheDocument()
    })
  })

  describe('общее поведение', () => {
    it('закрывает модальное окно при клике на Cancel', async () => {
      const user = userEvent.setup()

      renderWithMockAuth(
        <UserFormModal open={true} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      await user.click(screen.getByRole('button', { name: /cancel/i }))

      expect(mockOnClose).toHaveBeenCalled()
    })

    it('не рендерит модальное окно когда open=false', () => {
      renderWithMockAuth(
        <UserFormModal open={false} user={null} onClose={mockOnClose} />,
        { authValue: { isAuthenticated: true } }
      )

      expect(screen.queryByText('Add User')).not.toBeInTheDocument()
    })
  })
})
