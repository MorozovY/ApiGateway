// Страница обработки OIDC callback от Keycloak
// Story 12.2: Admin UI — Keycloak Auth Migration

import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Spin, Alert } from 'antd'
import { useAuth } from '../hooks/useAuth'

/**
 * CallbackPage — обрабатывает redirect от Keycloak после успешной аутентификации.
 * Показывает loading spinner пока OIDC library обрабатывает authorization code.
 */
export const CallbackPage: React.FC = () => {
  const { isAuthenticated, isLoading, error } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    // После успешного callback — redirect на dashboard
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true })
    }
  }, [isAuthenticated, navigate])

  // Ошибка аутентификации
  if (error) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          padding: 24,
        }}
      >
        <Alert
          type="error"
          showIcon
          message="Ошибка аутентификации"
          description={
            <>
              <p>{error}</p>
              <a href="/login">Попробовать снова</a>
            </>
          }
        />
      </div>
    )
  }

  // Loading state пока обрабатывается callback
  if (isLoading || !isAuthenticated) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          gap: 16,
        }}
      >
        <Spin size="large" />
        <p>Завершаем вход...</p>
      </div>
    )
  }

  return null
}
