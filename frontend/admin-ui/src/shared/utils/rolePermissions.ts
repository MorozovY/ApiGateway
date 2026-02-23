// Централизованные helper-функции для проверки прав доступа (Story 11.6)
import type { UserRole } from '../constants/roles'

/**
 * Минимальный интерфейс пользователя для проверки прав.
 */
export interface MinimalUser {
  userId?: string
  role?: UserRole
}

/**
 * Минимальный интерфейс маршрута для проверки прав.
 */
export interface MinimalRoute {
  status: string
  createdBy?: string
}

/**
 * Проверяет роль Admin.
 *
 * @param user - пользователь для проверки
 * @returns true если пользователь имеет роль admin
 */
export const isAdmin = (user?: MinimalUser): boolean => {
  return user?.role === 'admin'
}

/**
 * Проверяет роль Developer.
 *
 * @param user - пользователь для проверки
 * @returns true если пользователь имеет роль developer
 */
export const isDeveloper = (user?: MinimalUser): boolean => {
  return user?.role === 'developer'
}

/**
 * Проверяет роль Admin или Security.
 *
 * @param user - пользователь для проверки
 * @returns true если пользователь имеет роль admin или security
 */
export const isAdminOrSecurity = (user?: MinimalUser): boolean => {
  return user?.role === 'security' || user?.role === 'admin'
}

/**
 * Проверяет, может ли пользователь одобрять маршруты.
 * Одобрение доступно только Security и Admin.
 *
 * @param user - пользователь для проверки
 * @returns true если пользователь может одобрять маршруты
 */
export const canApprove = (user?: MinimalUser): boolean => {
  return isAdminOrSecurity(user)
}

/**
 * Проверяет, может ли пользователь откатить маршрут.
 * Rollback доступен Security/Admin для published маршрутов.
 *
 * @param route - маршрут для проверки
 * @param user - пользователь для проверки
 * @returns true если пользователь может откатить маршрут
 */
export const canRollback = (route: MinimalRoute, user?: MinimalUser): boolean => {
  return route.status === 'published' && isAdminOrSecurity(user)
}

/**
 * Проверяет, может ли пользователь удалить маршрут.
 * Delete доступен автору или Admin для draft маршрутов.
 *
 * @param route - маршрут для проверки
 * @param user - пользователь для проверки
 * @returns true если пользователь может удалить маршрут
 */
export const canDelete = (route: MinimalRoute, user?: MinimalUser): boolean => {
  if (route.status !== 'draft') return false
  return route.createdBy === user?.userId || isAdmin(user)
}

/**
 * Проверяет, может ли пользователь редактировать маршрут.
 * Редактирование доступно автору или Admin для draft маршрутов.
 *
 * @param route - маршрут для проверки
 * @param user - пользователь для проверки
 * @returns true если пользователь может редактировать маршрут
 */
export const canModify = (route: MinimalRoute, user?: MinimalUser): boolean => {
  if (route.status !== 'draft') return false
  return route.createdBy === user?.userId || isAdmin(user)
}
