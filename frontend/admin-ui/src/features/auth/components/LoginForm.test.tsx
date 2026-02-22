// Тесты для LoginForm компонента
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import { LoginForm } from './LoginForm'
import type { AuthContextType } from '../types/auth.types'

describe('LoginForm', () => {
  // Мок функций
  const mockLogin = vi.fn()
  const mockClearError = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  // Базовые значения для мок контекста
  const baseAuthValue: Partial<AuthContextType> = {
    login: mockLogin,
    clearError: mockClearError,
    isLoading: false,
    error: null,
  }

  it('рендерит форму с полями username и password', () => {
    renderWithMockAuth(<LoginForm />, { authValue: baseAuthValue })

    expect(screen.getByTestId('username-input')).toBeInTheDocument()
    expect(screen.getByTestId('password-input')).toBeInTheDocument()
    expect(screen.getByTestId('login-button')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /войти/i })).toBeInTheDocument()
  })

  it('показывает validation errors для пустых полей', async () => {
    const user = userEvent.setup()
    renderWithMockAuth(<LoginForm />, { authValue: baseAuthValue })

    // Кликаем на кнопку без заполнения полей
    await user.click(screen.getByTestId('login-button'))

    // Ждём появления сообщений об ошибках валидации
    await waitFor(() => {
      expect(screen.getByText('Введите имя пользователя')).toBeInTheDocument()
    })
    await waitFor(() => {
      expect(screen.getByText('Введите пароль')).toBeInTheDocument()
    })

    // login не должен был вызваться
    expect(mockLogin).not.toHaveBeenCalled()
  })

  it('вызывает login с введёнными данными', async () => {
    const user = userEvent.setup()
    renderWithMockAuth(<LoginForm />, { authValue: baseAuthValue })

    // Заполняем форму
    await user.type(screen.getByTestId('username-input'), 'testuser')
    await user.type(screen.getByTestId('password-input'), 'password123')

    // Отправляем форму
    await user.click(screen.getByTestId('login-button'))

    // Проверяем, что clearError и login были вызваны
    await waitFor(() => {
      expect(mockClearError).toHaveBeenCalled()
    })
    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('testuser', 'password123')
    })
  })

  it('показывает error alert при наличии ошибки', () => {
    const authValueWithError: Partial<AuthContextType> = {
      ...baseAuthValue,
      error: 'Invalid username or password',
    }

    renderWithMockAuth(<LoginForm />, { authValue: authValueWithError })

    expect(screen.getByTestId('login-error')).toBeInTheDocument()
    expect(screen.getByText('Invalid username or password')).toBeInTheDocument()
  })

  it('не показывает error alert когда ошибки нет', () => {
    renderWithMockAuth(<LoginForm />, { authValue: baseAuthValue })

    expect(screen.queryByTestId('login-error')).not.toBeInTheDocument()
  })

  it('показывает loading state на кнопке при isLoading', () => {
    const authValueLoading: Partial<AuthContextType> = {
      ...baseAuthValue,
      isLoading: true,
    }

    renderWithMockAuth(<LoginForm />, { authValue: authValueLoading })

    const button = screen.getByTestId('login-button')
    // Ant Design добавляет класс ant-btn-loading при loading
    expect(button).toHaveClass('ant-btn-loading')
  })

  it('не показывает loading state когда isLoading false', () => {
    renderWithMockAuth(<LoginForm />, { authValue: baseAuthValue })

    const button = screen.getByTestId('login-button')
    expect(button).not.toHaveClass('ant-btn-loading')
  })

  // Story 10.6: Ссылки на API документацию
  describe('ссылки на API документацию (Story 10.6)', () => {
    it('отображает секцию API документации', () => {
      renderWithMockAuth(<LoginForm />, { authValue: baseAuthValue })

      // AC1: Секция API документации отображается
      expect(screen.getByTestId('api-docs-links')).toBeInTheDocument()

      // AC1: Ссылка на Swagger отображается
      expect(screen.getByTestId('swagger-link')).toBeInTheDocument()
    })

    it('секция API документации расположена после Demo Credentials (AC3)', () => {
      renderWithMockAuth(<LoginForm />, { authValue: baseAuthValue })

      const demoCredentials = screen.getByTestId('demo-credentials-card')
      const apiDocsLinks = screen.getByTestId('api-docs-links')

      // Проверяем что ApiDocsLinks идёт после DemoCredentials в DOM
      // compareDocumentPosition возвращает битовую маску, DOCUMENT_POSITION_FOLLOWING = 4
      const position = demoCredentials.compareDocumentPosition(apiDocsLinks)
      expect(position & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    })
  })
})
