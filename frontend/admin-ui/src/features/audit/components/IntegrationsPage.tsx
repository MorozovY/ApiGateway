// Страница Integrations Report (Story 7.6, AC3, AC4, AC5, AC6, AC9)
import { useEffect, useRef } from 'react'
import { Navigate } from 'react-router-dom'
import { Card, Button, Space, Typography, App } from 'antd'
import { DownloadOutlined, ClusterOutlined } from '@ant-design/icons'
import { useAuth } from '@features/auth'
import { useUpstreams } from '../hooks/useUpstreams'
import { UpstreamsTable } from './UpstreamsTable'
import { exportUpstreamReport } from '../utils/exportUpstreamReport'

const { Title } = Typography

/**
 * Страница отчёта по интеграциям (Upstream Services Report).
 *
 * Особенности:
 * - Таблица unique upstream hosts с количеством маршрутов (AC3)
 * - Click-through на /routes?upstream={host} (AC4)
 * - Export Report в CSV (AC5)
 * - Role-based access: только security и admin (AC6)
 * - Empty state для пустого списка (AC9)
 */
export function IntegrationsPage() {
  const { user } = useAuth()
  const { message } = App.useApp()
  const { data } = useUpstreams()
  const accessDeniedShown = useRef(false)

  // Проверка доступа (AC6) — показываем сообщение только один раз
  const isAccessDenied = user?.role === 'developer'

  // Показываем сообщение об отказе доступа перед редиректом
  useEffect(() => {
    if (isAccessDenied && !accessDeniedShown.current) {
      accessDeniedShown.current = true
      message.error('Недостаточно прав для просмотра отчёта по интеграциям')
    }
  }, [isAccessDenied, message])

  // Developer role редиректится на главную (AC6)
  if (isAccessDenied) {
    return <Navigate to="/" replace />
  }

  // Обработчик экспорта отчёта (AC5)
  const handleExport = async () => {
    if (!data?.upstreams?.length) {
      message.warning('Нет данных для экспорта')
      return
    }

    try {
      await exportUpstreamReport(data.upstreams, message)
    } catch {
      // Ошибка уже обработана в exportUpstreamReport
    }
  }

  return (
    <Card>
      {/* Заголовок страницы */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <ClusterOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Title level={3} style={{ margin: 0 }}>
              Integrations Report
            </Title>
          </Space>

          {/* Кнопка экспорта (AC5) */}
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={handleExport}
            disabled={!data?.upstreams?.length}
          >
            Export Report
          </Button>
        </Space>
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
          Обзор внешних сервисов (upstream) и маршрутов, которые к ним обращаются
        </Typography.Text>
      </div>

      {/* Таблица upstream сервисов */}
      <UpstreamsTable />
    </Card>
  )
}
