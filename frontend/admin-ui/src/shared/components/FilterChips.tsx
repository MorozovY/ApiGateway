// Переиспользуемый компонент FilterChips для отображения активных фильтров (Story 8.8)
import { Space, Tag } from 'antd'

/**
 * Интерфейс для одного chip фильтра.
 */
export interface FilterChip {
  /** Уникальный ключ chip (например: 'search', 'status', 'user') */
  key: string
  /** Отображаемый текст (например: 'Поиск: orders') */
  label: string
  /** Цвет Ant Design Tag (по умолчанию 'blue') */
  color?: string
  /** Callback при закрытии chip */
  onClose: () => void
}

/**
 * Props для компонента FilterChips.
 */
export interface FilterChipsProps {
  /** Массив активных фильтров для отображения */
  chips: FilterChip[]
  /** Дополнительный CSS класс */
  className?: string
}

/**
 * Компонент для отображения активных фильтров в виде closable Tags.
 *
 * Используется для унификации UI фильтров во всех таблицах.
 * Каждый chip показывает тип фильтра и значение с возможностью удаления.
 *
 * Цветовая схема по типу фильтра:
 * - Search: blue
 * - Status: по статусу (draft=gray, pending=gold, published=green, rejected=red)
 * - Role: purple
 * - User: cyan
 * - Entity Type: orange
 * - Action: magenta
 * - Date Range: geekblue
 * - Upstream: purple
 *
 * @param chips - массив активных фильтров
 * @param className - дополнительный CSS класс
 */
export function FilterChips({ chips, className }: FilterChipsProps) {
  // Не рендерим ничего если нет активных фильтров
  if (chips.length === 0) {
    return null
  }

  return (
    <Space wrap className={className} style={{ marginBottom: 8 }} data-testid="filter-chips">
      {chips.map((chip) => (
        <Tag
          key={chip.key}
          closable
          color={chip.color || 'blue'}
          onClose={chip.onClose}
        >
          {chip.label}
        </Tag>
      ))}
    </Space>
  )
}
