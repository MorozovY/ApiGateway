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

  // Story 9.3, AC4 — role-based route protection
  describe('Role-based route protection (AC4)', () => {
    const adminUser = {
      userId: '1',
      username: 'admin',
      role: 'admin' as const,
    }

    const securityUser = {
      userId: '2',
      username: 'security',
      role: 'security' as const,
    }

    const developerUser = {
      userId: '3',
      username: 'developer',
      role: 'developer' as const,
    }

    const adminAuthValue: Partial<AuthContextType> = {
      isAuthenticated: true,
      user: adminUser,
      isLoading: false,
    }

    const securityAuthValue: Partial<AuthContextType> = {
      isAuthenticated: true,
      user: securityUser,
      isLoading: false,
    }

    const developerAuthValue: Partial<AuthContextType> = {
      isAuthenticated: true,
      user: developerUser,
      isLoading: false,
    }

    it('admin может получить доступ к /test', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/test"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="test-page">Test Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: adminAuthValue,
          initialEntries: ['/test'],
        }
      )

      expect(screen.getByTestId('test-page')).toBeInTheDocument()
    })

    it('developer редиректится на /dashboard при попытке доступа к /test', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/test"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="test-page">Test Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: developerAuthValue,
          initialEntries: ['/test'],
        }
      )

      // Test page недоступен
      expect(screen.queryByTestId('test-page')).not.toBeInTheDocument()
      // Редирект на dashboard
      expect(screen.getByTestId('dashboard')).toBeInTheDocument()
    })

    it('security редиректится на /dashboard при попытке доступа к /test', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/test"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="test-page">Test Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: securityAuthValue,
          initialEntries: ['/test'],
        }
      )

      expect(screen.queryByTestId('test-page')).not.toBeInTheDocument()
      expect(screen.getByTestId('dashboard')).toBeInTheDocument()
    })

    it('admin может получить доступ к /rate-limits', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/rate-limits"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="rate-limits-page">Rate Limits Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: adminAuthValue,
          initialEntries: ['/rate-limits'],
        }
      )

      expect(screen.getByTestId('rate-limits-page')).toBeInTheDocument()
    })

    it('developer редиректится на /dashboard при попытке доступа к /rate-limits', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/rate-limits"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="rate-limits-page">Rate Limits Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: developerAuthValue,
          initialEntries: ['/rate-limits'],
        }
      )

      expect(screen.queryByTestId('rate-limits-page')).not.toBeInTheDocument()
      expect(screen.getByTestId('dashboard')).toBeInTheDocument()
    })

    it('security редиректится на /dashboard при попытке доступа к /rate-limits', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/rate-limits"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="rate-limits-page">Rate Limits Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: securityAuthValue,
          initialEntries: ['/rate-limits'],
        }
      )

      expect(screen.queryByTestId('rate-limits-page')).not.toBeInTheDocument()
      expect(screen.getByTestId('dashboard')).toBeInTheDocument()
    })

    // AC4 — /users route protection
    it('admin может получить доступ к /users', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/users"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="users-page">Users Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: adminAuthValue,
          initialEntries: ['/users'],
        }
      )

      expect(screen.getByTestId('users-page')).toBeInTheDocument()
    })

    it('developer редиректится на /dashboard при попытке доступа к /users', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/users"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="users-page">Users Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: developerAuthValue,
          initialEntries: ['/users'],
        }
      )

      expect(screen.queryByTestId('users-page')).not.toBeInTheDocument()
      expect(screen.getByTestId('dashboard')).toBeInTheDocument()
    })

    it('security редиректится на /dashboard при попытке доступа к /users', () => {
      renderWithMockAuth(
        <Routes>
          <Route path="/dashboard" element={<div data-testid="dashboard">Dashboard</div>} />
          <Route
            path="/users"
            element={
              <ProtectedRoute requiredRole="admin">
                <div data-testid="users-page">Users Page</div>
              </ProtectedRoute>
            }
          />
        </Routes>,
        {
          authValue: securityAuthValue,
          initialEntries: ['/users'],
        }
      )

      expect(screen.queryByTestId('users-page')).not.toBeInTheDocument()
      expect(screen.getByTestId('dashboard')).toBeInTheDocument()
    })
  })
})
