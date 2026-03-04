// Конфигурация описаний страниц для PageInfoBlock (Story 15.4)
import type { PageDescription } from '../components/PageInfoBlock'

/**
 * Ключи страниц системы — строго типизированы.
 */
export type PageKey =
  | 'dashboard'
  | 'routes'
  | 'metrics'
  | 'approvals'
  | 'audit'
  | 'integrations'
  | 'users'
  | 'consumers'
  | 'rateLimits'
  | 'test'

/**
 * Описания всех страниц системы.
 *
 * Каждая страница имеет:
 * - title: заголовок вкладки
 * - description: краткое описание назначения
 * - features: список ключевых возможностей
 */
export const PAGE_DESCRIPTIONS: Record<PageKey, PageDescription> = {
  dashboard: {
    title: 'Dashboard',
    description: 'Главная страница с обзором системы',
    features: [
      'Приветствие с именем пользователя',
      'Отображение текущей роли',
      'Быстрый выход из системы (Logout)',
    ],
  },

  routes: {
    title: 'Routes',
    description: 'Управление маршрутами API Gateway',
    features: [
      'Создание и редактирование маршрутов',
      'Публикация маршрутов для согласования',
      'Фильтрация по статусу (draft, pending, published, rejected)',
      'Поиск по path и upstream URL',
      'Ctrl+N для быстрого создания нового маршрута',
    ],
  },

  metrics: {
    title: 'Metrics',
    description: 'Мониторинг производительности и трафика',
    features: [
      'Обзор ключевых метрик: RPS, latency, error rate',
      'Top routes по количеству запросов',
      'Выбор временного периода (5m, 15m, 1h, 6h, 24h)',
      'Health check статус сервисов',
      'Ссылка на Grafana для детального анализа',
    ],
  },

  approvals: {
    title: 'Approvals',
    description: 'Согласование маршрутов перед публикацией',
    features: [
      'Список маршрутов, ожидающих одобрения',
      'Одобрение маршрутов одним кликом',
      'Отклонение с указанием причины',
      'Просмотр деталей маршрута перед решением',
      'Клавиши A/R для быстрых действий',
    ],
  },

  audit: {
    title: 'Audit Logs',
    description: 'Журнал всех изменений в системе',
    features: [
      'История всех действий пользователей',
      'Фильтрация по пользователю, типу действия и дате',
      'Просмотр изменений (diff) в деталях',
      'Экспорт в CSV для анализа',
    ],
  },

  integrations: {
    title: 'Integrations',
    description: 'Отчёт по upstream интеграциям',
    features: [
      'Список всех внешних сервисов (upstream)',
      'Количество маршрутов для каждого сервиса',
      'Клик по сервису показывает связанные маршруты',
      'Экспорт отчёта в CSV',
    ],
  },

  users: {
    title: 'Users',
    description: 'Управление пользователями и ролями',
    features: [
      'Список пользователей системы',
      'Создание новых пользователей',
      'Назначение ролей (admin, security, developer)',
      'Редактирование профилей',
    ],
  },

  consumers: {
    title: 'Consumers',
    description: 'Управление API consumers',
    features: [
      'Список API consumers (клиентов)',
      'Создание новых consumers в Keycloak',
      'Генерация client secrets',
      'Настройка rate limit per-consumer',
      'Поиск по client ID',
    ],
  },

  rateLimits: {
    title: 'Rate Limits',
    description: 'Настройка политик ограничения трафика',
    features: [
      'Создание политик rate limit',
      'Настройка requests/period для каждой политики',
      'Просмотр маршрутов, использующих политику',
      'Редактирование и удаление политик (только admin)',
    ],
  },

  test: {
    title: 'Test',
    description: 'Генератор нагрузки для тестирования',
    features: [
      'Выбор опубликованного маршрута',
      'Настройка RPS и продолжительности',
      'Запуск и остановка генерации нагрузки',
      'Отслеживание прогресса в реальном времени',
      'Просмотр итогов после завершения',
    ],
  },
}
