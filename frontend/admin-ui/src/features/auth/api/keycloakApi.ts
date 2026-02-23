// Keycloak Direct Access Grants API
// Story 12.2: Admin UI — Keycloak Auth Migration
// Позволяет аутентифицироваться через Keycloak API без редиректа на Keycloak UI

import { isKeycloakEnabled } from '../config/oidcConfig'

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID

/**
 * Ответ от Keycloak token endpoint
 */
export interface KeycloakTokenResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  refresh_expires_in: number
  token_type: string
  scope: string
}

/**
 * Ошибка от Keycloak
 */
export interface KeycloakError {
  error: string
  error_description: string
}

/**
 * Аутентификация через Keycloak Direct Access Grants (Resource Owner Password Credentials).
 * Позволяет использовать нашу форму логина вместо редиректа на Keycloak UI.
 *
 * @param username - Имя пользователя
 * @param password - Пароль
 * @returns Токены от Keycloak
 * @throws Error при неудачной аутентификации
 */
export async function keycloakLogin(
  username: string,
  password: string
): Promise<KeycloakTokenResponse> {
  if (!isKeycloakEnabled()) {
    throw new Error('Keycloak is not enabled')
  }

  const tokenUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`

  const params = new URLSearchParams({
    grant_type: 'password',
    client_id: KEYCLOAK_CLIENT_ID,
    username,
    password,
    scope: 'openid profile email',
  })

  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  })

  if (!response.ok) {
    const error: KeycloakError = await response.json().catch(() => ({
      error: 'unknown_error',
      error_description: 'Ошибка аутентификации',
    }))

    // Преобразуем Keycloak ошибки в понятные сообщения
    if (error.error === 'invalid_grant') {
      throw new Error('Неверные учётные данные')
    }
    if (error.error === 'invalid_client') {
      throw new Error('Ошибка конфигурации клиента Keycloak')
    }
    throw new Error(error.error_description || 'Ошибка аутентификации')
  }

  return response.json()
}

/**
 * Обновление токенов через refresh_token.
 *
 * @param refreshToken - Refresh token от предыдущей аутентификации
 * @returns Новые токены
 */
export async function keycloakRefreshToken(
  refreshToken: string
): Promise<KeycloakTokenResponse> {
  if (!isKeycloakEnabled()) {
    throw new Error('Keycloak is not enabled')
  }

  const tokenUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`

  const params = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: KEYCLOAK_CLIENT_ID,
    refresh_token: refreshToken,
  })

  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  })

  if (!response.ok) {
    throw new Error('Не удалось обновить токен')
  }

  return response.json()
}

/**
 * Logout через Keycloak (invalidate tokens).
 *
 * @param refreshToken - Refresh token для инвалидации
 */
export async function keycloakLogout(refreshToken: string): Promise<void> {
  if (!isKeycloakEnabled()) {
    return
  }

  const logoutUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`

  const params = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    refresh_token: refreshToken,
  })

  // Logout может завершиться с ошибкой, но это не критично
  await fetch(logoutUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  }).catch(() => {
    // Игнорируем ошибки logout
  })
}
