// Тесты для Keycloak Direct Access Grants API
// Story 12.2: Admin UI — Keycloak Auth Migration
// Story 12.9.1: Legacy cookie auth удалён

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { keycloakLogin, keycloakRefreshToken, keycloakLogout } from './keycloakApi'

// Мокаем environment variables
vi.stubEnv('VITE_KEYCLOAK_URL', 'http://localhost:8180')
vi.stubEnv('VITE_KEYCLOAK_REALM', 'api-gateway')
vi.stubEnv('VITE_KEYCLOAK_CLIENT_ID', 'gateway-admin-ui')

describe('keycloakApi', () => {
  const mockFetch = vi.fn()
  const originalFetch = global.fetch

  beforeEach(() => {
    global.fetch = mockFetch
    mockFetch.mockClear()
  })

  afterEach(() => {
    global.fetch = originalFetch
  })

  describe('keycloakLogin', () => {
    const mockTokenResponse = {
      access_token: 'mock-access-token',
      refresh_token: 'mock-refresh-token',
      expires_in: 300,
      refresh_expires_in: 1800,
      token_type: 'Bearer',
      scope: 'openid profile email',
    }

    it('отправляет правильный запрос на token endpoint', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockTokenResponse),
      })

      await keycloakLogin('testuser', 'password123')

      expect(mockFetch).toHaveBeenCalledTimes(1)
      const [url, options] = mockFetch.mock.calls[0]

      expect(url).toBe('http://localhost:8180/realms/api-gateway/protocol/openid-connect/token')
      expect(options.method).toBe('POST')
      expect(options.headers['Content-Type']).toBe('application/x-www-form-urlencoded')

      const body = new URLSearchParams(options.body)
      expect(body.get('grant_type')).toBe('password')
      expect(body.get('client_id')).toBe('gateway-admin-ui')
      expect(body.get('username')).toBe('testuser')
      expect(body.get('password')).toBe('password123')
      expect(body.get('scope')).toBe('openid profile email')
    })

    it('возвращает токены при успешной аутентификации', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockTokenResponse),
      })

      const result = await keycloakLogin('testuser', 'password123')

      expect(result).toEqual(mockTokenResponse)
    })

    it('выбрасывает ошибку "Неверные учётные данные" при invalid_grant', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        json: () => Promise.resolve({
          error: 'invalid_grant',
          error_description: 'Invalid user credentials',
        }),
      })

      await expect(keycloakLogin('wrong', 'credentials')).rejects.toThrow('Неверные учётные данные')
    })

    it('выбрасывает ошибку конфигурации при invalid_client', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        json: () => Promise.resolve({
          error: 'invalid_client',
          error_description: 'Client not found',
        }),
      })

      await expect(keycloakLogin('user', 'pass')).rejects.toThrow('Ошибка конфигурации клиента Keycloak')
    })

    it('выбрасывает error_description для других ошибок', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        json: () => Promise.resolve({
          error: 'some_error',
          error_description: 'Custom error message',
        }),
      })

      await expect(keycloakLogin('user', 'pass')).rejects.toThrow('Custom error message')
    })

    it('выбрасывает fallback сообщение если response не JSON', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        json: () => Promise.reject(new Error('Not JSON')),
      })

      await expect(keycloakLogin('user', 'pass')).rejects.toThrow('Ошибка аутентификации')
    })
  })

  describe('keycloakRefreshToken', () => {
    const mockRefreshResponse = {
      access_token: 'new-access-token',
      refresh_token: 'new-refresh-token',
      expires_in: 300,
      refresh_expires_in: 1800,
      token_type: 'Bearer',
      scope: 'openid profile email',
    }

    it('отправляет правильный запрос для обновления токена', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockRefreshResponse),
      })

      await keycloakRefreshToken('old-refresh-token')

      expect(mockFetch).toHaveBeenCalledTimes(1)
      const [url, options] = mockFetch.mock.calls[0]

      expect(url).toBe('http://localhost:8180/realms/api-gateway/protocol/openid-connect/token')

      const body = new URLSearchParams(options.body)
      expect(body.get('grant_type')).toBe('refresh_token')
      expect(body.get('client_id')).toBe('gateway-admin-ui')
      expect(body.get('refresh_token')).toBe('old-refresh-token')
    })

    it('возвращает новые токены при успешном refresh', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockRefreshResponse),
      })

      const result = await keycloakRefreshToken('old-refresh-token')

      expect(result).toEqual(mockRefreshResponse)
    })

    it('выбрасывает ошибку при неудачном refresh', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
      })

      await expect(keycloakRefreshToken('expired-token')).rejects.toThrow('Не удалось обновить токен')
    })
  })

  describe('keycloakLogout', () => {
    it('отправляет запрос на logout endpoint', async () => {
      mockFetch.mockResolvedValueOnce({ ok: true })

      await keycloakLogout('refresh-token-to-invalidate')

      expect(mockFetch).toHaveBeenCalledTimes(1)
      const [url, options] = mockFetch.mock.calls[0]

      expect(url).toBe('http://localhost:8180/realms/api-gateway/protocol/openid-connect/logout')
      expect(options.method).toBe('POST')

      const body = new URLSearchParams(options.body)
      expect(body.get('client_id')).toBe('gateway-admin-ui')
      expect(body.get('refresh_token')).toBe('refresh-token-to-invalidate')
    })

    it('не выбрасывает ошибку при неудачном logout (graceful)', async () => {
      mockFetch.mockRejectedValueOnce(new Error('Network error'))

      // Не должен выбросить ошибку
      await expect(keycloakLogout('token')).resolves.toBeUndefined()
    })

    it('не выбрасывает ошибку при HTTP ошибке logout', async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 500 })

      // Не должен выбросить ошибку (catch в .catch())
      await expect(keycloakLogout('token')).resolves.toBeUndefined()
    })
  })
})
