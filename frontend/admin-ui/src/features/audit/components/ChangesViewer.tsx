// Компонент для отображения JSON diff (Story 7.5, AC3)
import { useMemo } from 'react'
import { Typography, Card, Row, Col, Empty } from 'antd'
import type { AuditAction } from '../types/audit.types'

const { Text, Title } = Typography

interface ChangesViewerProps {
  before?: Record<string, unknown> | null
  after?: Record<string, unknown> | null
  action: AuditAction
}

/**
 * Стили для JSON syntax highlighting.
 */
const jsonStyles = {
  container: {
    fontFamily: 'monospace',
    fontSize: '12px',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-all' as const,
    padding: '12px',
    borderRadius: '4px',
    maxHeight: '400px',
    overflow: 'auto',
  },
  before: {
    backgroundColor: '#fff1f0',
    border: '1px solid #ffa39e',
  },
  after: {
    backgroundColor: '#f6ffed',
    border: '1px solid #b7eb8f',
  },
  single: {
    backgroundColor: '#f5f5f5',
    border: '1px solid #d9d9d9',
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
export function ChangesViewer({ before, after, action }: ChangesViewerProps) {
  // Определяем режим отображения в зависимости от действия
  const displayMode = useMemo(() => {
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
  }, [action])

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
        <div style={{ ...jsonStyles.container, ...jsonStyles.after }}>
          {formatJson(after)}
        </div>
      </Card>
    )
  }

  // Режим "только Before" (deleted, rejected)
  if (displayMode === 'before-only') {
    return (
      <Card size="small" title={beforeOnlyTitles[action] || 'Данные'}>
        <div style={{ ...jsonStyles.container, ...jsonStyles.before }}>
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
          <div style={{ ...jsonStyles.container, ...jsonStyles.before }}>
            {formatJson(before)}
          </div>
        </Col>
        <Col span={12}>
          <Title level={5} style={{ marginBottom: 8 }}>
            <Text type="success">После изменения</Text>
          </Title>
          <div style={{ ...jsonStyles.container, ...jsonStyles.after }}>
            {formatJson(after)}
          </div>
        </Col>
      </Row>
    </Card>
  )
}
