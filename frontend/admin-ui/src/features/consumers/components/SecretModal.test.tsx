// Тесты для SecretModal (Story 12.9, AC2, AC3)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import SecretModal from './SecretModal'

describe('SecretModal', () => {
  const mockOnClose = vi.fn()
  const testSecret = 'super-secret-abc123xyz789'
  const testClientId = 'test-consumer'

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('показывает secret и warning message', () => {
    renderWithMockAuth(
      <SecretModal open={true} clientId={testClientId} secret={testSecret} onClose={mockOnClose} />
    )

    // Проверяем title модального окна
    expect(screen.getByRole('dialog', { name: /client secret/i })).toBeInTheDocument()
    // Проверяем Client ID
    expect(screen.getByText(/client id:/i)).toBeInTheDocument()
    expect(screen.getByText(testClientId)).toBeInTheDocument()
    // Проверяем что secret отображается в input
    expect(screen.getByDisplayValue(testSecret)).toBeInTheDocument()
    // Проверяем warning message
    expect(screen.getByText(/сохраните этот secret сейчас/i)).toBeInTheDocument()
  })

  it('копирует secret в clipboard при клике на Copy', async () => {
    const user = userEvent.setup()

    // Spy на clipboard.writeText после userEvent.setup()
    const writeTextSpy = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined)

    renderWithMockAuth(
      <SecretModal open={true} clientId={testClientId} secret={testSecret} onClose={mockOnClose} />
    )

    // Ищем кнопку Copy по тексту (role может не работать из-за иконки)
    const copyButton = screen.getByText('Copy')
    await user.click(copyButton)

    expect(writeTextSpy).toHaveBeenCalledWith(testSecret)

    writeTextSpy.mockRestore()
  })

  it('закрывает модальное окно при клике на Закрыть', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(
      <SecretModal open={true} clientId={testClientId} secret={testSecret} onClose={mockOnClose} />
    )

    const closeButton = screen.getByRole('button', { name: /закрыть/i })
    await user.click(closeButton)

    expect(mockOnClose).toHaveBeenCalled()
  })

  it('не рендерит модальное окно когда open=false', () => {
    renderWithMockAuth(
      <SecretModal open={false} clientId={testClientId} secret={testSecret} onClose={mockOnClose} />
    )

    expect(screen.queryByText(/client secret/i)).not.toBeInTheDocument()
  })
})
