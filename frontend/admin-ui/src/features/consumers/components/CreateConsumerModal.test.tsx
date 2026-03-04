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

  it('рендерит модальное окно с заголовком Новый потребитель (AC2)', () => {
    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Русское название согласно локализации Story 16.1
    expect(screen.getByText('Новый потребитель')).toBeInTheDocument()
  })

  it('показывает форму с полями ID клиента и Описание (AC2)', () => {
    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Русские названия согласно локализации Story 16.1
    expect(screen.getByLabelText(/id клиента/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/описание/i)).toBeInTheDocument()
  })

  it('показывает Alert с предупреждением о secret (AC2)', () => {
    renderWithMockAuth(<CreateConsumerModal open={true} onClose={mockOnClose} />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Русское название согласно локализации Story 16.1
    expect(screen.getByText(/аутентификация потребителя/i)).toBeInTheDocument()
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
    const createButton = screen.getByRole('button', { name: /создать/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(screen.getByText(/id клиента обязателен/i)).toBeInTheDocument()
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

    const clientIdInput = screen.getByLabelText(/id клиента/i)

    // Вводим невалидный Client ID (заглавные буквы)
    await user.type(clientIdInput, 'Consumer-A')

    const createButton = screen.getByRole('button', { name: /создать/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(
        screen.getByText(/id клиента должен содержать только lowercase буквы/i)
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

    const clientIdInput = screen.getByLabelText(/id клиента/i)

    // Вводим слишком короткий Client ID (2 символа)
    await user.type(clientIdInput, 'ab')

    const createButton = screen.getByRole('button', { name: /создать/i })
    await user.click(createButton)

    await waitFor(() => {
      expect(screen.getByText(/id клиента должен быть от 3 до 63 символов/i)).toBeInTheDocument()
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

    const clientIdInput = screen.getByLabelText(/id клиента/i)
    await user.type(clientIdInput, 'company-a')

    const createButton = screen.getByRole('button', { name: /создать/i })
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

    const clientIdInput = screen.getByLabelText(/id клиента/i)
    const descriptionInput = screen.getByLabelText(/описание/i)

    await user.type(clientIdInput, 'partner-api')
    await user.type(descriptionInput, 'Partner API Consumer')

    const createButton = screen.getByRole('button', { name: /создать/i })
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

    const clientIdInput = screen.getByLabelText(/id клиента/i)
    await user.type(clientIdInput, 'new-consumer')

    const createButton = screen.getByRole('button', { name: /создать/i })
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

    const cancelButton = screen.getByRole('button', { name: /отмена/i })
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

    const clientIdInput = screen.getByLabelText(/id клиента/i)
    await user.type(clientIdInput, 'test-consumer')

    const createButton = screen.getByRole('button', { name: /создать/i })
    await user.click(createButton)

    // Ждём успешного создания и закрытия формы
    await waitFor(() => {
      expect(mockOnClose).toHaveBeenCalled()
    })
  })
})
