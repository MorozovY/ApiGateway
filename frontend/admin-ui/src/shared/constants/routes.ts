// Общие константы для маршрутов (Story 3.6 — Code Review Refactoring)
import type { RouteStatus } from '@features/routes'

/**
 * Цвета для badges статусов маршрутов.
 * Используются в RoutesTable и RouteDetailsCard.
 */
export const STATUS_COLORS: Record<RouteStatus, string> = {
  draft: 'default',
  pending: 'processing',
  published: 'success',
  rejected: 'error',
}

/**
 * Человекочитаемые названия статусов на русском языке.
 * Единый источник истины для всего приложения.
 */
export const STATUS_LABELS: Record<RouteStatus, string> = {
  draft: 'Черновик',
  pending: 'На согласовании',
  published: 'Опубликован',
  rejected: 'Отклонён',
}

/**
 * Цвета для HTTP методов.
 * Покрывает все стандартные HTTP методы.
 */
export const METHOD_COLORS: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
  HEAD: 'cyan',
  OPTIONS: 'geekblue',
  TRACE: 'magenta',
}
