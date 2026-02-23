// Тесты для oidcConfig — маппинг ролей Keycloak и декодирование JWT
// Story 12.2: Admin UI — Keycloak Auth Migration

import { describe, it, expect } from 'vitest'
import { mapKeycloakRoles, extractKeycloakRoles, decodeJwtPayload, isKeycloakEnabled } from './oidcConfig'

describe('oidcConfig', () => {
  describe('mapKeycloakRoles', () => {
    it('возвращает admin для роли admin-ui:admin', () => {
      const roles = ['admin-ui:admin', 'default-roles-api-gateway']
      expect(mapKeycloakRoles(roles)).toBe('admin')
    })

    it('возвращает security для роли admin-ui:security', () => {
      const roles = ['admin-ui:security', 'default-roles-api-gateway']
      expect(mapKeycloakRoles(roles)).toBe('security')
    })

    it('возвращает developer для роли admin-ui:developer', () => {
      const roles = ['admin-ui:developer', 'default-roles-api-gateway']
      expect(mapKeycloakRoles(roles)).toBe('developer')
    })

    it('приоритет: admin > security > developer', () => {
      // Если у пользователя несколько ролей, admin имеет приоритет
      const rolesAdminAndSecurity = ['admin-ui:admin', 'admin-ui:security']
      expect(mapKeycloakRoles(rolesAdminAndSecurity)).toBe('admin')

      const rolesSecurityAndDev = ['admin-ui:security', 'admin-ui:developer']
      expect(mapKeycloakRoles(rolesSecurityAndDev)).toBe('security')
    })

    it('возвращает developer как fallback если нет admin-ui ролей', () => {
      const roles = ['default-roles-api-gateway', 'some-other-role']
      expect(mapKeycloakRoles(roles)).toBe('developer')
    })

    it('возвращает developer для пустого массива ролей', () => {
      expect(mapKeycloakRoles([])).toBe('developer')
    })
  })

  describe('decodeJwtPayload', () => {
    it('декодирует валидный JWT payload', () => {
      // Создаём простой JWT с payload
      const payload = { sub: 'user-123', preferred_username: 'testuser', email: 'test@example.com' }
      const payloadBase64 = btoa(JSON.stringify(payload))
      const fakeJwt = `header.${payloadBase64}.signature`

      const decoded = decodeJwtPayload(fakeJwt)
      expect(decoded.sub).toBe('user-123')
      expect(decoded.preferred_username).toBe('testuser')
      expect(decoded.email).toBe('test@example.com')
    })

    it('возвращает пустой объект для невалидного JWT (менее 3 частей)', () => {
      expect(decodeJwtPayload('invalid')).toEqual({})
      expect(decodeJwtPayload('only.two')).toEqual({})
    })

    it('возвращает пустой объект для невалидного base64', () => {
      const invalidJwt = 'header.!!!invalid-base64!!!.signature'
      expect(decodeJwtPayload(invalidJwt)).toEqual({})
    })

    it('возвращает пустой объект для невалидного JSON в payload', () => {
      // Валидный base64, но не JSON
      const notJsonBase64 = btoa('this is not json')
      const invalidJwt = `header.${notJsonBase64}.signature`
      expect(decodeJwtPayload(invalidJwt)).toEqual({})
    })

    it('обрабатывает base64url encoding (с - и _)', () => {
      // JWT использует base64url, а не стандартный base64
      const payload = { sub: 'test' }
      const payloadJson = JSON.stringify(payload)
      // Конвертируем в base64url (заменяем + на - и / на _)
      const payloadBase64url = btoa(payloadJson).replace(/\+/g, '-').replace(/\//g, '_')
      const jwt = `header.${payloadBase64url}.signature`

      const decoded = decodeJwtPayload(jwt)
      expect(decoded.sub).toBe('test')
    })
  })

  describe('extractKeycloakRoles', () => {
    it('извлекает роли из realm_access.roles в JWT', () => {
      const payload = {
        sub: 'user-123',
        realm_access: {
          roles: ['admin-ui:admin', 'default-roles-api-gateway']
        }
      }
      const payloadBase64 = btoa(JSON.stringify(payload))
      const jwt = `header.${payloadBase64}.signature`

      const roles = extractKeycloakRoles(jwt)
      expect(roles).toContain('admin-ui:admin')
      expect(roles).toContain('default-roles-api-gateway')
    })

    it('возвращает пустой массив если realm_access отсутствует', () => {
      const payload = { sub: 'user-123' }
      const payloadBase64 = btoa(JSON.stringify(payload))
      const jwt = `header.${payloadBase64}.signature`

      expect(extractKeycloakRoles(jwt)).toEqual([])
    })

    it('возвращает пустой массив если roles отсутствует в realm_access', () => {
      const payload = { sub: 'user-123', realm_access: {} }
      const payloadBase64 = btoa(JSON.stringify(payload))
      const jwt = `header.${payloadBase64}.signature`

      expect(extractKeycloakRoles(jwt)).toEqual([])
    })

    it('возвращает пустой массив для undefined accessToken', () => {
      expect(extractKeycloakRoles(undefined)).toEqual([])
    })

    it('возвращает пустой массив для пустой строки', () => {
      expect(extractKeycloakRoles('')).toEqual([])
    })

    it('возвращает пустой массив для невалидного JWT', () => {
      expect(extractKeycloakRoles('invalid-jwt')).toEqual([])
    })
  })

  describe('isKeycloakEnabled', () => {
    it('возвращает boolean значение', () => {
      const result = isKeycloakEnabled()
      expect(typeof result).toBe('boolean')
    })
  })
})
