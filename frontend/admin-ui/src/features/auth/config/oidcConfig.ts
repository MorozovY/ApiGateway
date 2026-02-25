// Конфигурация OIDC для Keycloak
// Story 12.2: Admin UI — Keycloak Auth Migration
// Story 12.9.1: Legacy cookie auth и feature flag удалены

import type { UserManagerSettings } from 'oidc-client-ts'

/**
 * Настройки OIDC для Keycloak.
 * Используется react-oidc-context для интеграции с React.
 */
export const oidcConfig: UserManagerSettings = {
  // Keycloak authority URL (issuer)
  authority: `${import.meta.env.VITE_KEYCLOAK_URL}/realms/${import.meta.env.VITE_KEYCLOAK_REALM}`,

  // Client ID (настроен в Keycloak как public client)
  client_id: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,

  // Redirect URI после успешной аутентификации
  redirect_uri: `${window.location.origin}/callback`,

  // Redirect URI после logout
  post_logout_redirect_uri: window.location.origin,

  // OAuth2 scopes
  scope: 'openid profile email',

  // Authorization Code flow (PKCE включён автоматически для public clients)
  response_type: 'code',

  // Автоматическое обновление токена (AC3: Silent Token Refresh)
  automaticSilentRenew: true,

  // Загружать информацию о пользователе из userinfo endpoint
  loadUserInfo: true,

  // Хранение токенов в sessionStorage (более безопасно чем localStorage)
  userStore: undefined, // По умолчанию sessionStorage

  // Интервал проверки истечения токена (в секундах)
  accessTokenExpiringNotificationTimeInSeconds: 60,
}

/**
 * Маппинг Keycloak roles → Admin UI roles.
 * Роли приходят в JWT claim `realm_access.roles`.
 *
 * @param keycloakRoles - Массив ролей из Keycloak JWT
 * @returns Роль для Admin UI (admin | security | developer)
 */
export type AdminUiRole = 'admin' | 'security' | 'developer'

export const mapKeycloakRoles = (keycloakRoles: string[]): AdminUiRole => {
  // Приоритет: admin > security > developer
  if (keycloakRoles.includes('admin-ui:admin')) {
    return 'admin'
  }
  if (keycloakRoles.includes('admin-ui:security')) {
    return 'security'
  }
  if (keycloakRoles.includes('admin-ui:developer')) {
    return 'developer'
  }
  // Fallback на developer если роли не найдены
  return 'developer'
}

/**
 * Декодирует JWT payload (без проверки подписи).
 * Используется для извлечения claims из access_token.
 * Безопасно обрабатывает malformed JWT.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) {
      return {}
    }
    // Декодируем payload (вторая часть JWT)
    const payload = parts[1]
    if (!payload) {
      return {}
    }
    // Base64url decode
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    console.error('Failed to decode JWT payload')
    return {}
  }
}

/**
 * Извлекает роли из access_token Keycloak.
 * Роли хранятся в claim `realm_access.roles` внутри access_token.
 *
 * @param accessToken - Access token от Keycloak
 * @returns Массив ролей Keycloak
 */
export const extractKeycloakRoles = (accessToken: string | undefined): string[] => {
  if (!accessToken) {
    return []
  }

  const payload = decodeJwtPayload(accessToken)

  // Keycloak хранит роли в realm_access.roles
  const realmAccess = payload.realm_access as { roles?: string[] } | undefined
  const roles = realmAccess?.roles ?? []

  return roles
}
