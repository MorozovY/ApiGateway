// Страница согласования маршрутов с inline-действиями (Story 4.6; Story 5.7, AC2; Story 8.8; Story 10.2; Story 15.4; Story 16.5 — empty state; Story 16.9 — shortcuts hint; Story 16.10 — WorkflowIndicator)
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
  Tooltip,
  Typography,
  Card,
} from 'antd'
import { CheckOutlined, CloseOutlined, SearchOutlined, CloseCircleOutlined, ReloadOutlined, CheckCircleOutlined, EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons'
import { FilterChips, type FilterChip } from '@shared/components/FilterChips'
import { PageInfoBlock, WorkflowIndicator } from '@shared/components'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'
import { EmptyState } from '@shared/components/EmptyState'
import { useWorkflowIndicator } from '@shared/hooks'
import { highlightSearchTerm } from '@shared/utils'
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

  // Загрузка данных и мутации (Story 10.2 — refetch для manual refresh, isFetching для loading state)
  const { data: pendingRoutes, isLoading, isFetching, refetch } = usePendingRoutes()

  // Story 16.10: Hook для управления видимостью WorkflowIndicator
  const { visible: workflowVisible, toggle: toggleWorkflow } = useWorkflowIndicator()

  // Клиентская фильтрация по path и upstream URL (Story 5.7, AC2; Story 8.7, AC1)
  const filteredRoutes = useMemo(() => {
    if (!pendingRoutes || !searchText.trim()) {
      return pendingRoutes
    }
    const lowerSearch = searchText.toLowerCase()
    return pendingRoutes.filter((route) =>
      route.path.toLowerCase().includes(lowerSearch) ||
      route.upstreamUrl.toLowerCase().includes(lowerSearch)
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
      title: 'Путь',
      dataIndex: 'path',
      key: 'path',
      // Кликабельный path — открывает Drawer (AC6), подсветка поиска (Story 8.7)
      render: (path: string, record: PendingRoute) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => handleOpenDrawer(record)}>
          {highlightSearchTerm(path, searchText)}
        </Button>
      ),
    },
    {
      title: 'URL сервиса',
      dataIndex: 'upstreamUrl',
      key: 'upstreamUrl',
      ellipsis: true,
      // Подсветка поиска (Story 8.7)
      render: (upstreamUrl: string) => highlightSearchTerm(upstreamUrl, searchText),
    },
    {
      title: 'Методы',
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
      title: 'Отправил',
      dataIndex: 'creatorUsername',
      key: 'creatorUsername',
      render: (username: string | undefined) => username || '—',
    },
    {
      title: 'Отправлено',
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
      title: 'Действия',
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
            Одобрить
          </Button>
          {/* Отклонение с модальным окном (AC3) */}
          <Button
            danger
            size="small"
            icon={<CloseOutlined />}
            onClick={() => handleOpenRejectModal(record)}
          >
            Отклонить
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <Card>
      {/* Заголовок страницы (Story 15.6 — унификация) */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <CheckCircleOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Typography.Title level={3} style={{ margin: 0 }}>
              Согласования
            </Typography.Title>
          </Space>
          <Space>
            {/* Story 16.10: Кнопка toggle для WorkflowIndicator (AC3) */}
            <Tooltip title={workflowVisible ? 'Скрыть workflow' : 'Показать workflow'}>
              <Button
                type="text"
                icon={workflowVisible ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                onClick={toggleWorkflow}
                data-testid="workflow-toggle"
                aria-label={workflowVisible ? 'Скрыть workflow' : 'Показать workflow'}
              />
            </Tooltip>
            {/* Кнопка ручного обновления (AC3) */}
            <Button
              icon={<ReloadOutlined />}
              onClick={() => refetch()}
              loading={isFetching}
              disabled={isFetching}
              data-testid="refresh-button"
            >
              Обновить
            </Button>
          </Space>
        </Space>
      </div>

      {/* Story 16.10: WorkflowIndicator между header и PageInfoBlock (AC6) */}
      {workflowVisible && <WorkflowIndicator currentStep={2} />}

      {/* Инфо-блок (Story 15.4) */}
      <PageInfoBlock pageKey="approvals" {...PAGE_DESCRIPTIONS.approvals} />

      {/* Панель фильтров (Story 8.8) */}
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Поиск по path, upstream..."
          prefix={<SearchOutlined />}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          allowClear
          style={{ width: 280 }}
          data-testid="search-input"
        />
        {searchText && (
          <Button
            type="text"
            icon={<CloseCircleOutlined />}
            onClick={() => setSearchText('')}
          >
            Сбросить фильтры
          </Button>
        )}
      </Space>

      {/* FilterChips для активных фильтров (Story 8.8) */}
      <FilterChips
        chips={[
          ...(searchText
            ? [{
                key: 'search',
                label: `Поиск: ${searchText}`,
                onClose: () => setSearchText(''),
              } as FilterChip]
            : []),
        ]}
      />

      {/* Таблица pending маршрутов (AC1) */}
      {/* Story 16.5 AC2: кастомный empty state с позитивным тоном */}
      <Table<PendingRoute>
        dataSource={filteredRoutes}
        columns={columns}
        rowKey="id"
        loading={isLoading}
        locale={{
          emptyText: (
            <EmptyState
              icon={<CheckCircleOutlined style={{ fontSize: 48, color: '#52c41a' }} />}
              title="Нет маршрутов на согласование"
              description="Все маршруты обработаны"
            />
          ),
        }}
        onRow={(record) => ({
          // Клавиатурная навигация (AC8)
          tabIndex: 0,
          onKeyDown: (e) => handleRowKeyDown(e, record),
        })}
        pagination={false}
        footer={() => (
          // Story 16.9 AC5: подсказка о keyboard shortcuts
          <Text type="secondary" data-testid="keyboard-shortcuts-hint">
            💡 Клавиши: A — одобрить, R — отклонить (при фокусе на строке)
          </Text>
        )}
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
    </Card>
  )
}
