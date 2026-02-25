// Тесты для CreateConsumerModal (Story 12.9, AC2)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import CreateConsumerModal from './CreateConsumerModal'
import * as consumersApi from '../api/consumersApi'

// Мокаем API
vi.mock('../api/consumersApi', () => ({
  fetchConsumers: vi.fn(),
  fetchConsumer: vi.fn(),
  createConsumer: vi.fn(),
  rotateSecret: vi.fn(),
  disableConsumer: vi.fn(),
  enableConsumer: vi.fn(),
}))

const mockCreateConsumer = consumersApi.createConsumer as ReturnType<typeof vi.fn>

describe('CreateConsumerModal', () => {
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('рендерит модальное окно с заголовком Create Consumer (AC2)', () => {
    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByText('Create Consumer')).toBeInTheDocument()
  })

  it('показывает форму с полями Client ID и Description (AC2)', () => {
    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByLabelText(/client id/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument()
  })

  it('показывает Alert с предупреждением о secret (AC2)', () => {
    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByText(/consumer authentication/i)).toBeInTheDocument()
    expect(
      screen.getByText(/secret будет показан только один раз после создания/i)
    ).toBeInTheDocument()
  })

  it('валидирует Client ID — required (AC2)', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Кликаем Create без заполнения Client ID
    const createButton = screen.getByRole('button', { name: /create/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(screen.getByText(/client id обязателен/i)).toBeInTheDocument()
    })
  })

  it('валидирует Client ID — pattern (lowercase, numbers, hyphens) (AC2)', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const clientIdInput = screen.getByLabelText(/client id/i)

    // Вводим невалидный Client ID (заглавные буквы)
    await user.type(clientIdInput, 'Consumer-A')

    const createButton = screen.getByRole('button', { name: /create/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(
        screen.getByText(/client id должен содержать только lowercase буквы/i)
      ).toBeInTheDocument()
    })
  })

  it('валидирует Client ID — min/max length (3-63 chars) (AC2)', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const clientIdInput = screen.getByLabelText(/client id/i)

    // Вводим слишком короткий Client ID (2 символа)
    await user.type(clientIdInput, 'ab')

    const createButton = screen.getByRole('button', { name: /create/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(screen.getByText(/client id должен быть от 3 до 63 символов/i)).toBeInTheDocument()
    })
  })

  it('принимает валидный Client ID (lowercase, numbers, hyphens) (AC2)', async () => {
    const user = userEvent.setup()
    mockCreateConsumer.mockResolvedValue({
      clientId: 'company-a',
      secret: 'secret-12345',
      message: 'Сохраните этот secret сейчас. Он больше не будет показан.',
    })

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const clientIdInput = screen.getByLabelText(/client id/i)
    await user.type(clientIdInput, 'company-a')

    const createButton = screen.getByRole('button', { name: /create/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(mockCreateConsumer).toHaveBeenCalledWith({
        clientId: 'company-a',
        description: undefined,
      })
    })
  })

  it('отправляет форму с description если заполнено (AC2)', async () => {
    const user = userEvent.setup()
    mockCreateConsumer.mockResolvedValue({
      clientId: 'partner-api',
      secret: 'secret-67890',
      message: 'Сохраните этот secret сейчас. Он больше не будет показан.',
    })

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const clientIdInput = screen.getByLabelText(/client id/i)
    const descriptionInput = screen.getByLabelText(/description/i)

    await user.type(clientIdInput, 'partner-api')
    await user.type(descriptionInput, 'Partner API Consumer')

    const createButton = screen.getByRole('button', { name: /create/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(mockCreateConsumer).toHaveBeenCalledWith({
        clientId: 'partner-api',
        description: 'Partner API Consumer',
      })
    })
  })

  it('показывает SecretModal после успешного создания (AC2)', async () => {
    const user = userEvent.setup()
    mockCreateConsumer.mockResolvedValue({
      clientId: 'new-consumer',
      secret: 'new-secret-12345',
      message: 'Сохраните этот secret сейчас. Он больше не будет показан.',
    })

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const clientIdInput = screen.getByLabelText(/client id/i)
    await user.type(clientIdInput, 'new-consumer')

    const createButton = screen.getByRole('button', { name: /create/i })
    await user.click(createButton)

    // Ждём открытия SecretModal (title = "Client Secret")
    await waitFor(() => {
      expect(screen.getByText('Client Secret')).toBeInTheDocument()
    })

    expect(screen.getByText('new-consumer')).toBeInTheDocument()
    // Secret отображается в Input.Password, поэтому используем getByDisplayValue
    expect(screen.getByDisplayValue('new-secret-12345')).toBeInTheDocument()
  })

  it('закрывает модальное окно при клике Cancel (AC2)', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    await user.click(cancelButton)

    expect(mockOnClose).toHaveBeenCalled()
  })

  it('сбрасывает форму при закрытии модального окна (AC2)', async () => {
    const user = userEvent.setup()
    mockCreateConsumer.mockResolvedValue({
      clientId: 'test-consumer',
      secret: 'test-secret',
      message: 'Сохраните этот secret сейчас. Он больше не будет показан.',
    })

    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    const clientIdInput = screen.getByLabelText(/client id/i)
    await user.type(clientIdInput, 'test-consumer')

    const createButton = screen.getByRole('button', { name: /create/i })
    await user.click(createButton)

    // Ждём успешного создания и закрытия формы
    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled()
    })
  })
})
