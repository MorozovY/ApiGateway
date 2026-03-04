// Компонент для отображения HTTP методов со сворачиванием (Story 16.4, AC3)
import { useState } from 'react'
import { Space, Tag, Button } from 'antd'
import { METHOD_COLORS } from '@shared/constants'

/**
 * Максимальное количество методов для отображения без сворачивания.
 */
const MAX_VISIBLE_METHODS = 3

interface CollapsibleMethodsProps {
  /** Массив HTTP методов для отображения */
  methods: string[]
  /** Пороговое значение для сворачивания (по умолчанию 3) */
  maxVisible?: number
}

/**
 * Компонент CollapsibleMethods — отображение HTTP методов со сворачиванием.
 *
 * Если методов больше maxVisible, показывает первые maxVisible + кнопку "ещё +N".
 * По клику разворачивает все методы.
 */
export function CollapsibleMethods({
  methods,
  maxVisible = MAX_VISIBLE_METHODS,
}: CollapsibleMethodsProps) {
  const [expanded, setExpanded] = useState(false)

  // Если методов мало — показываем все
  if (methods.length <= maxVisible) {
    return (
      <Space size={4} wrap>
        {methods.map((method) => (
          <Tag key={method} color={METHOD_COLORS[method] || 'default'}>
            {method}
          </Tag>
        ))}
      </Space>
    )
  }

  // Показываем свёрнутый или развёрнутый вид
  const visibleMethods = expanded ? methods : methods.slice(0, maxVisible)
  const hiddenCount = methods.length - maxVisible

  return (
    <Space size={4} wrap>
      {visibleMethods.map((method) => (
        <Tag key={method} color={METHOD_COLORS[method] || 'default'}>
          {method}
        </Tag>
      ))}
      {!expanded && (
        <Button
          type="link"
          size="small"
          onClick={() => setExpanded(true)}
          style={{ padding: '0 4px', height: 'auto', lineHeight: 'inherit' }}
          data-testid="methods-expand-button"
        >
          ещё +{hiddenCount}
        </Button>
      )}
      {expanded && (
        <Button
          type="link"
          size="small"
          onClick={() => setExpanded(false)}
          style={{ padding: '0 4px', height: 'auto', lineHeight: 'inherit' }}
          data-testid="methods-collapse-button"
        >
          свернуть
        </Button>
      )}
    </Space>
  )
}
