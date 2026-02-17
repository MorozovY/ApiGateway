// Тесты для UsersPage компонента (Story 2.6)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import UsersPage from './UsersPage'
import * as usersApi from '../api/usersApi'
import type { UserListResponse, User } from '../types/user.types'

// Мокаем API
vi.mock('../api/usersApi', () => ({
  fetchUsers: vi.fn(),
  deactivateUser: vi.fn(),
}))

const mockFetchUsers = usersApi.fetchUsers as ReturnType<typeof vi.fn>

// Тестовые данные
const mockUsers: User[] = [
  {
    id: '1',
    username: 'admin',
    email: 'admin@company.com',
    role: 'admin',
    isActive: true,
    createdAt: '2026-02-01T10:00:00Z',
  },
  {
    id: '2',
    username: 'developer1',
    email: 'dev1@company.com',
    role: 'developer',
    isActive: true,
    createdAt: '2026-02-10T10:00:00Z',
  },
  {
    id: '3',
    username: 'security1',
    email: 'sec1@company.com',
    role: 'security',
    isActive: false,
    createdAt: '2026-02-15T10:00:00Z',
  },
]

const mockUserListResponse: UserListResponse = {
  items: mockUsers,
  total: 3,
  offset: 0,
  limit: 10,
}

describe('UsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchUsers.mockResolvedValue(mockUserListResponse)
  })

  it('рендерит заголовок и кнопку Add User', async () => {
    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByText('Users')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add user/i })).toBeInTheDocument()
  })

  it('рендерит таблицу с пользователями', async () => {
    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Ждём загрузку данных — проверяем email вместо username
    // чтобы избежать конфликта с Admin badge
    await waitFor(() => {
      expect(screen.getByText('admin@company.com')).toBeInTheDocument()
    })

    // Проверяем что все пользователи отображаются
    expect(screen.getByText('developer1')).toBeInTheDocument()
    expect(screen.getByText('security1')).toBeInTheDocument()
  })

  it('отображает email в таблице', async () => {
    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('admin@company.com')).toBeInTheDocument()
    })

    expect(screen.getByText('dev1@company.com')).toBeInTheDocument()
  })

  it('отображает роли с цветными badges', async () => {
    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Admin')).toBeInTheDocument()
    })

    expect(screen.getByText('Developer')).toBeInTheDocument()
    expect(screen.getByText('Security')).toBeInTheDocument()
  })

  it('отображает статус Active/Inactive', async () => {
    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getAllByText('Active')).toHaveLength(2) // admin и developer1
    })

    expect(screen.getByText('Inactive')).toBeInTheDocument() // security1
  })

  it('вызывает обработчик клика при нажатии Add User', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    // Проверяем что кнопка существует и кликабельна
    const addButton = screen.getByRole('button', { name: /add user/i })
    expect(addButton).toBeEnabled()
    await user.click(addButton)

    // Модальное окно Ant Design рендерится в портале, поэтому
    // проверяем что заголовок модального окна появился (в document.body)
    await waitFor(() => {
      const modalTitle = document.querySelector('.ant-modal-title')
      expect(modalTitle).toBeInTheDocument()
    })
  })

  it('отображает кнопки Edit и Deactivate для каждой строки', async () => {
    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: /edit/i })).toHaveLength(3)
    })

    // Deactivate только для активных пользователей (2 из 3)
    expect(screen.getAllByRole('button', { name: /deactivate/i })).toHaveLength(2)
  })

  it('показывает пагинацию с общим количеством', async () => {
    renderWithMockAuth(<UsersPage />, {
      authValue: {
        user: { userId: '1', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText(/всего 3 пользователей/i)).toBeInTheDocument()
    })
  })
})
