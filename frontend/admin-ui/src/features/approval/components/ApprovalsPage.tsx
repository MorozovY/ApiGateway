// Страница согласования маршрутов с inline-действиями (Story 4.6; Story 5.7, AC2)
import { useState, useMemo } from 'react'
import {
  Table,
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Drawer,
  Descriptions,
  Empty,
  Tooltip,
  Typography,
} from 'antd'
import { CheckOutlined, CloseOutlined, SearchOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ru'
import { METHOD_COLORS } from '@shared/constants'
import { usePendingRoutes, useApproveRoute, useRejectRoute } from '../hooks/useApprovals'
import type { PendingRoute } from '../types/approval.types'

// Настройка dayjs для относительного времени
dayjs.extend(relativeTime)
dayjs.locale('ru')

const { Text } = Typography

/**
 * Страница со списком pending маршрутов и inline-действиями Approve/Reject.
 *
 * Доступ только для ролей security и admin — контролируется ProtectedRoute в App.tsx (AC9).
 */
export function ApprovalsPage() {
  // Состояние поиска (Story 5.7, AC2)
  const [searchText, setSearchText] = useState('')

  // Состояние модального окна отклонения
  const [rejectModalVisible, setRejectModalVisible] = useState(false)
  const [selectedRoute, setSelectedRoute] = useState<PendingRoute | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  // Флаг для показа ошибки валидации только после попытки отправки
  const [submitted, setSubmitted] = useState(false)

  // Состояние Drawer с деталями маршрута
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [drawerRoute, setDrawerRoute] = useState<PendingRoute | null>(null)

  // Загрузка данных и мутации
  const { data: pendingRoutes, isLoading } = usePendingRoutes()

  // Клиентская фильтрация по path (Story 5.7, AC2)
  const filteredRoutes = useMemo(() => {
    if (!pendingRoutes || !searchText.trim()) {
      return pendingRoutes
    }
    const lowerSearch = searchText.toLowerCase()
    return pendingRoutes.filter((route) =>
      route.path.toLowerCase().includes(lowerSearch)
    )
  }, [pendingRoutes, searchText])
  const approveMutation = useApproveRoute()
  const rejectMutation = useRejectRoute()

  /**
   * Обработчик одобрения маршрута (без модального окна — AC2).
   */
  const handleApprove = (id: string) => {
    approveMutation.mutate(id)
  }

  /**
   * Открытие модального окна отклонения.
   */
  const handleOpenRejectModal = (route: PendingRoute) => {
    setSelectedRoute(route)
    setRejectModalVisible(true)
  }

  /**
   * Обработчик закрытия модального окна отклонения.
   */
  const handleRejectCancel = () => {
    setRejectModalVisible(false)
    setRejectReason('')
    setSelectedRoute(null)
    setSubmitted(false)
  }

  /**
   * Обработчик подтверждения отклонения с валидацией (AC4, AC5).
   */
  const handleRejectConfirm = async () => {
    if (!rejectReason.trim()) {
      // Показываем ошибку валидации — НЕ закрываем modal (AC5)
      setSubmitted(true)
      return
    }
    if (!selectedRoute) return

    try {
      await rejectMutation.mutateAsync({ id: selectedRoute.id, reason: rejectReason.trim() })
      setRejectModalVisible(false)
      setRejectReason('')
      setSelectedRoute(null)
      setSubmitted(false)
    } catch {
      // Ошибка обработана в useRejectRoute (message.error)
    }
  }

  /**
   * Открытие Drawer с деталями маршрута (AC6).
   */
  const handleOpenDrawer = (route: PendingRoute) => {
    setDrawerRoute(route)
    setDrawerVisible(true)
  }

  /**
   * Обработчик клавиатурной навигации по строкам таблицы (AC8).
   * A — одобрить, R — открыть окно отклонения.
   */
  const handleRowKeyDown = (e: React.KeyboardEvent, route: PendingRoute) => {
    if (e.key === 'a' || e.key === 'A') {
      handleApprove(route.id)
    } else if (e.key === 'r' || e.key === 'R') {
      handleOpenRejectModal(route)
    }
  }

  // Определение колонок таблицы (AC1)
  const columns: ColumnsType<PendingRoute> = [
    {
      title: 'Path',
      dataIndex: 'path',
      key: 'path',
      // Кликабельный path — открывает Drawer (AC6)
      render: (path: string, record: PendingRoute) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => handleOpenDrawer(record)}>
          {path}
        </Button>
      ),
    },
    {
      title: 'Upstream URL',
      dataIndex: 'upstreamUrl',
      key: 'upstreamUrl',
      ellipsis: true,
    },
    {
      title: 'Methods',
      dataIndex: 'methods',
      key: 'methods',
      render: (methods: string[]) => (
        <Space size={4} wrap>
          {methods.map((method) => (
            <Tag key={method} color={METHOD_COLORS[method] || 'default'}>
              {method}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Submitted By',
      dataIndex: 'creatorUsername',
      key: 'creatorUsername',
      render: (username: string | undefined) => username || '—',
    },
    {
      title: 'Submitted At',
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (submittedAt: string) => (
        <Tooltip title={dayjs(submittedAt).format('DD.MM.YYYY HH:mm')}>
          {dayjs(submittedAt).fromNow()}
        </Tooltip>
      ),
      sorter: (a, b) => dayjs(a.submittedAt).unix() - dayjs(b.submittedAt).unix(),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record: PendingRoute) => (
        <Space>
          {/* Одобрение без подтверждения (AC2) */}
          <Button
            type="primary"
            size="small"
            icon={<CheckOutlined />}
            loading={approveMutation.isPending}
            onClick={() => handleApprove(record.id)}
          >
            Approve
          </Button>
          {/* Отклонение с модальным окном (AC3) */}
          <Button
            danger
            size="small"
            icon={<CloseOutlined />}
            onClick={() => handleOpenRejectModal(record)}
          >
            Reject
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: '24px' }}>
      <Typography.Title level={3} style={{ marginBottom: 24 }}>
        Согласование маршрутов
      </Typography.Title>

      {/* Поле поиска (Story 5.7, AC2) */}
      <Input
        placeholder="Поиск по пути..."
        prefix={<SearchOutlined />}
        value={searchText}
        onChange={(e) => setSearchText(e.target.value)}
        allowClear
        style={{ marginBottom: 16, maxWidth: 300 }}
        data-testid="search-input"
      />

      {/* Таблица pending маршрутов (AC1) */}
      <Table<PendingRoute>
        dataSource={filteredRoutes}
        columns={columns}
        rowKey="id"
        loading={isLoading}
        locale={{
          // Пустое состояние (AC7)
          emptyText: (
            <Empty
              description="Нет маршрутов на согласовании"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ),
        }}
        onRow={(record) => ({
          // Клавиатурная навигация (AC8)
          tabIndex: 0,
          onKeyDown: (e) => handleRowKeyDown(e, record),
        })}
        pagination={false}
      />

      {/* Модальное окно отклонения (AC3, AC4, AC5) */}
      <Modal
        title={`Отклонить: ${selectedRoute?.path}`}
        open={rejectModalVisible}
        onCancel={handleRejectCancel}
        onOk={handleRejectConfirm}
        okText="Отклонить"
        okButtonProps={{ danger: true }}
        cancelText="Отмена"
        confirmLoading={rejectMutation.isPending}
        destroyOnHidden
      >
        <Form layout="vertical">
          <Form.Item
            label="Причина отклонения"
            required
            validateStatus={submitted && rejectReason.trim() === '' ? 'error' : ''}
            help={submitted && rejectReason.trim() === '' ? 'Укажите причину отклонения' : ''}
          >
            <Input.TextArea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              rows={4}
              placeholder="Опишите причину отклонения..."
              autoFocus
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Drawer с деталями маршрута (AC6) */}
      <Drawer
        title={`Маршрут: ${drawerRoute?.path}`}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
        width={480}
        footer={
          drawerRoute && (
            <Space>
              <Button
                type="primary"
                onClick={() => {
                  handleApprove(drawerRoute.id)
                  setDrawerVisible(false)
                }}
                loading={approveMutation.isPending}
              >
                Одобрить
              </Button>
              <Button
                danger
                onClick={() => {
                  setDrawerVisible(false)
                  setSelectedRoute(drawerRoute)
                  setRejectModalVisible(true)
                }}
              >
                Отклонить
              </Button>
            </Space>
          )
        }
      >
        {drawerRoute && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="Path">
              <Text code>{drawerRoute.path}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Upstream URL">
              {drawerRoute.upstreamUrl}
            </Descriptions.Item>
            <Descriptions.Item label="Methods">
              <Space size={4} wrap>
                {drawerRoute.methods.map((method) => (
                  <Tag key={method} color={METHOD_COLORS[method] || 'default'}>
                    {method}
                  </Tag>
                ))}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="Описание">
              {drawerRoute.description || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Отправил">
              {drawerRoute.creatorUsername || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Отправлено">
              <Tooltip title={dayjs(drawerRoute.submittedAt).format('DD.MM.YYYY HH:mm')}>
                {dayjs(drawerRoute.submittedAt).fromNow()}
              </Tooltip>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  )
}
