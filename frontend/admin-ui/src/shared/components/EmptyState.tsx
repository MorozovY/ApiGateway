// Переиспользуемый компонент EmptyState для пустых таблиц (Story 16.5)
import { Empty, Button, theme } from 'antd'
import type { ReactNode } from 'react'

/**
 * Props интерфейс для EmptyState.
 */
export interface EmptyStateProps {
  /** Кастомная иконка (опционально, вместо стандартного изображения) */
  icon?: ReactNode
  /** Заголовок (крупный текст) */
  title: string
  /** Описание (мелкий серый текст) */
  description?: string
  /** CTA кнопка (опционально) */
  action?: {
    label: string
    onClick: () => void
    type?: 'primary' | 'default'
  }
  /** Использовать простое изображение Ant Design вместо иконки */
  useSimpleImage?: boolean
}

/**
 * Переиспользуемый компонент EmptyState на базе Ant Design Empty.
 *
 * Поддерживает кастомные иконки, описание и CTA кнопку.
 * Центрирован и стилизован согласно Ant Design guidelines.
 *
 * @example
 * // С CTA кнопкой
 * <EmptyState
 *   title="Маршруты ещё не созданы"
 *   description="Создайте первый маршрут для начала работы"
 *   action={{ label: "Создать маршрут", onClick: handleCreate }}
 * />
 *
 * @example
 * // С кастомной иконкой (успех)
 * <EmptyState
 *   icon={<CheckCircleOutlined style={{ fontSize: 48, color: '#52c41a' }} />}
 *   title="Нет маршрутов на согласование"
 *   description="Все маршруты обработаны"
 * />
 */
export function EmptyState({
  icon,
  title,
  description,
  action,
  useSimpleImage = false,
}: EmptyStateProps) {
  // Получаем токены темы для поддержки dark mode
  const { token } = theme.useToken()

  // Определяем изображение: кастомная иконка или стандартное Ant Design
  const image = icon ?? (useSimpleImage ? Empty.PRESENTED_IMAGE_SIMPLE : Empty.PRESENTED_IMAGE_DEFAULT)

  return (
    <Empty
      image={image}
      description={
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 16, fontWeight: 500, marginBottom: description ? 8 : 0 }}>
            {title}
          </div>
          {description && (
            <div style={{ color: token.colorTextSecondary }}>
              {description}
            </div>
          )}
        </div>
      }
      style={{ padding: '48px 0' }}
    >
      {action && (
        <Button
          type={action.type ?? 'primary'}
          onClick={action.onClick}
        >
          {action.label}
        </Button>
      )}
    </Empty>
  )
}
