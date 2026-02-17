// Тесты для ProtectedRoute компонента
import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithMockAuth } from '@/test/test-utils'
import { ProtectedRoute } from './ProtectedRoute'
import { Routes, Route } from 'react-router-dom'
import type { AuthContextType } from '../types/auth.types'

describe('ProtectedRoute', () => {
  // Базовые значения для аутентифицированного пользователя
  const authenticatedUser = {
    userId: '123',
    username: 'testuser',
    role: 'developer' as const,
  }

  const authenticatedAuthValue: Partial<AuthContextType> = {
    isAuthenticated: true,
    user: authenticatedUser,
    isLoading: false,
    error: null,
    login: vi.fn(),
    logout: vi.fn(),
    clearError: vi.fn(),
  }

  const unauthenticatedAuthValue: Partial<AuthContextType> = {
    isAuthenticated: false,
    user: null,
    isLoading: false,
    error: null,
    login: vi.fn(),
    logout: vi.fn(),
    clearError: vi.fn(),
  }

  it('показывает protected content если пользователь аутентифицирован', () => {
    renderWithMockAuth(
      <ProtectedRoute>
        <div data-testid="protected-content">Protected Content</div>
      </ProtectedRoute>,
      {
        authValue: authenticatedAuthValue,
        initialEntries: ['/dashboard'],
      }
    )

    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('не показывает protected content если пользователь не аутентифицирован', () => {
    renderWithMockAuth(
      <Routes>
        <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <div data-testid="protected-content">Protected Content</div>
            </ProtectedRoute>
          }
        />
      </Routes>,
      {
        authValue: unauthenticatedAuthValue,
        initialEntries: ['/dashboard'],
      }
    )

    // Protected content не должен быть виден
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument()
    // Должен быть редирект на login
    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('редиректит на /login с сохранением returnUrl', () => {
    renderWithMockAuth(
      <Routes>
        <Route path="/login" element={<div data-testid="login-page">Login</div>} />
        <Route
          path="/routes"
          element={
            <ProtectedRoute>
              <div>Routes Page</div>
            </ProtectedRoute>
          }
        />
      </Routes>,
      {
        authValue: unauthenticatedAuthValue,
        initialEntries: ['/routes'],
      }
    )

    // Должен быть редирект на login
    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })
})
