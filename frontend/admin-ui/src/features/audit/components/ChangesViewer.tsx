// Компонент для отображения JSON diff (Story 7.5, AC3)
import { useMemo } from 'react'
import { Typography, Card, Row, Col, Empty } from 'antd'
import { useThemeContext } from '@/shared/providers/ThemeProvider'
import type { AuditAction } from '../types/audit.types'

const { Text, Title } = Typography

// Props для ChangesViewer
// Story 10.8: Поддержка generic changes без before/after структуры
interface ChangesViewerProps {
  // Новый prop: весь объект changes (Story 10.8)
  changes?: {
    before?: Record<string, unknown> | null
    after?: Record<string, unknown> | null
    [key: string]: unknown // для generic полей (previousStatus, newStatus, etc.)
  } | null
  // Legacy props для обратной совместимости (deprecated)
  before?: Record<string, unknown> | null
  after?: Record<string, unknown> | null
  action: AuditAction
}

/**
 * Базовые стили для JSON контейнера.
 */
const containerStyle = {
  fontFamily: 'monospace',
  fontSize: '12px',
  whiteSpace: 'pre-wrap' as const,
  wordBreak: 'break-all' as const,
  padding: '12px',
  borderRadius: '4px',
  maxHeight: '400px',
  overflow: 'auto',
}

/**
 * Цвета для JSON блоков в зависимости от темы.
 * Story 10.8: Добавлена поддержка тёмной темы.
 */
const JSON_COLORS = {
  light: {
    before: { backgroundColor: '#fff1f0', border: '1px solid #ffa39e', color: '#000000' },
    after: { backgroundColor: '#f6ffed', border: '1px solid #b7eb8f', color: '#000000' },
    single: { backgroundColor: '#f5f5f5', border: '1px solid #d9d9d9', color: '#000000' },
  },
  dark: {
    before: { backgroundColor: 'rgba(166, 29, 36, 0.15)', border: '1px solid #a61d24', color: '#ffffff' },
    after: { backgroundColor: 'rgba(73, 170, 25, 0.15)', border: '1px solid #49aa19', color: '#ffffff' },
    single: { backgroundColor: 'rgba(255, 255, 255, 0.08)', border: '1px solid #424242', color: '#ffffff' },
  },
}

/**
 * Форматирует JSON с подсветкой синтаксиса.
 */
function formatJson(obj: Record<string, unknown> | null | undefined): string {
  if (obj === null || obj === undefined) {
    return 'null'
  }
  return JSON.stringify(obj, null, 2)
}

/**
 * Компонент для отображения изменений в аудит-логе.
 *
 * Для updated событий показывает Before/After сравнение.
 * Для created/approved показывает только After.
 * Для deleted/rejected показывает только Before.
 * Для submitted показывает текущее состояние (after).
 *
 * JSON форматируется с syntax highlighting (AC3).
 */
export function ChangesViewer({ changes, before: legacyBefore, after: legacyAfter, action }: ChangesViewerProps) {
  const { isDark } = useThemeContext()

  // Story 10.8: Извлекаем before/after из changes объекта или используем legacy props
  const before = changes?.before ?? legacyBefore
  const after = changes?.after ?? legacyAfter

  // Story 10.8: Проверяем наличие структуры before/after в changes
  const hasBeforeAfterStructure = changes?.before !== undefined || changes?.after !== undefined

  // Выбираем цвета в зависимости от темы
  const colors = isDark ? JSON_COLORS.dark : JSON_COLORS.light

  // Определяем режим отображения в зависимости от действия
  const displayMode = useMemo(() => {
    // Story 10.8: Если changes передан без before/after — generic режим
    if (changes && !hasBeforeAfterStructure) {
      return 'generic'
    }

    // created, approved, submitted, published — показываем результат (after)
    if (action === 'created' || action === 'approved' || action === 'submitted' || action === 'published') {
      return 'after-only'
    }
    // deleted, rejected — показываем что было (before)
    if (action === 'deleted' || action === 'rejected') {
      return 'before-only'
    }
    // updated — показываем diff
    return 'diff'
  }, [action, changes, hasBeforeAfterStructure])

  // Story 10.8: Generic режим для changes без before/after (approved, rejected, submitted, rolledback)
  if (displayMode === 'generic') {
    return (
      <Card size="small" title="Детали изменения">
        <div style={{ ...containerStyle, ...colors.single }}>
          {formatJson(changes as Record<string, unknown>)}
        </div>
      </Card>
    )
  }

  // Если нет данных для отображения
  if (!before && !after) {
    return (
      <Empty
        description="Нет данных об изменениях"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }

  // Заголовки для разных типов действий
  const afterOnlyTitles: Record<string, string> = {
    created: 'Созданные данные',
    approved: 'Одобренные данные',
    submitted: 'Отправленные данные',
    published: 'Опубликованные данные',
  }

  const beforeOnlyTitles: Record<string, string> = {
    deleted: 'Удалённые данные',
    rejected: 'Отклонённые данные',
  }

  // Режим "только After" (created, approved, submitted)
  if (displayMode === 'after-only') {
    return (
      <Card size="small" title={afterOnlyTitles[action] || 'Данные'}>
        <div style={{ ...containerStyle, ...colors.after }}>
          {formatJson(after)}
        </div>
      </Card>
    )
  }

  // Режим "только Before" (deleted, rejected)
  if (displayMode === 'before-only') {
    return (
      <Card size="small" title={beforeOnlyTitles[action] || 'Данные'}>
        <div style={{ ...containerStyle, ...colors.before }}>
          {formatJson(before)}
        </div>
      </Card>
    )
  }

  // Режим diff (updated)
  return (
    <Card size="small" title="Изменения">
      <Row gutter={16}>
        <Col span={12}>
          <Title level={5} style={{ marginBottom: 8 }}>
            <Text type="danger">До изменения</Text>
          </Title>
          <div style={{ ...containerStyle, ...colors.before }}>
            {formatJson(before)}
          </div>
        </Col>
        <Col span={12}>
          <Title level={5} style={{ marginBottom: 8 }}>
            <Text type="success">После изменения</Text>
          </Title>
          <div style={{ ...containerStyle, ...colors.after }}>
            {formatJson(after)}
          </div>
        </Col>
      </Row>
    </Card>
  )
}
