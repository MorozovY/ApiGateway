// Типы пользователя и аутентификации
import type { UserRole } from '@shared/constants'

export interface User {
  userId: string
  username: string
  role: UserRole
}

export interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
}

export interface AuthContextType extends AuthState {
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  clearError: () => void
}

// Ответ API при успешном логине
export interface LoginResponse {
  userId: string
  username: string
  role: UserRole
}

// Ответ API при ошибке (RFC 7807)
export interface ApiError {
  type: string
  title: string
  status: number
  detail: string
  correlationId: string
}
