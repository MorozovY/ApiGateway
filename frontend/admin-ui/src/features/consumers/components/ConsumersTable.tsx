// Таблица consumers с пагинацией и действиями (Story 12.9, AC1, AC4-9)
import { useState } from 'react'
import { Table, Tag, Button, Space, Popconfirm, Typography } from 'antd'
import {
  KeyOutlined,
  StopOutlined,
  CheckCircleOutlined,
  ThunderboltOutlined,
  LineChartOutlined,
} from '@ant-design/icons'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { useConsumers, useRotateSecret, useDisableConsumer, useEnableConsumer } from '../hooks/useConsumers'
import type { Consumer } from '../types/consumer.types'
import SecretModal from './SecretModal'
import ConsumerRateLimitModal from './ConsumerRateLimitModal'

const { Text } = Typography

interface ConsumersTableProps {
  search?: string
}

/**
 * Размер страницы по умолчанию.
 */
const DEFAULT_PAGE_SIZE = 20

/**
 * Таблица consumers с пагинацией и действиями.
 *
 * Колонки: Client ID, Status, Rate Limit, Created, Actions
 * Expandable row для details (description, created date, "View Metrics" link)
 */
function ConsumersTable({ search }: ConsumersTableProps) {
  const navigate = useNavigate()

  // Состояние пагинации
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: DEFAULT_PAGE_SIZE,
  })

  // Состояние модальных окон
  const [secretModalOpen, setSecretModalOpen] = useState(false)
  const [secretData, setSecretData] = useState<{ clientId: string; secret: string } | null>(null)
  const [rateLimitModalOpen, setRateLimitModalOpen] = useState(false)
  const [selectedConsumerId, setSelectedConsumerId] = useState<string | null>(null)

  // Вычисляем offset для API
  const offset = (pagination.current - 1) * pagination.pageSize

  // Загрузка данных
  const { data, isLoading } = useConsumers({
    offset,
    limit: pagination.pageSize,
    search: search || undefined,
  })

  // Мутации
  const rotateSecretMutation = useRotateSecret()
  const disableMutation = useDisableConsumer()
  const enableMutation = useEnableConsumer()

  // Обработчик изменения пагинации
  const handleTableChange = (newPagination: TablePaginationConfig) => {
    setPagination({
      current: newPagination.current || 1,
      pageSize: newPagination.pageSize || DEFAULT_PAGE_SIZE,
    })
  }

  // Обработчик ротации secret (AC3)
  const handleRotateSecret = (clientId: string) => {
    rotateSecretMutation.mutate(clientId, {
      onSuccess: (response) => {
        setSecretData({ clientId: response.clientId, secret: response.secret })
        setSecretModalOpen(true)
      },
    })
  }

  // Обработчик деактивации (AC4)
  const handleDisable = (clientId: string) => {
    disableMutation.mutate(clientId)
  }

  // Обработчик активации (AC5)
  const handleEnable = (clientId: string) => {
    enableMutation.mutate(clientId)
  }

  // Обработчик открытия модального окна rate limit (AC8)
  const handleSetRateLimit = (consumerId: string) => {
    setSelectedConsumerId(consumerId)
    setRateLimitModalOpen(true)
  }

  // Обработчик View Metrics (AC7)
  const handleViewMetrics = (consumerId: string) => {
    navigate(`/metrics?consumer_id=${consumerId}`)
  }

  // Expandable row render (AC6)
  const expandedRowRender = (consumer: Consumer) => {
    return (
      <div style={{ paddingLeft: 48 }}>
        <p>
          <strong>Description:</strong> {consumer.description || '—'}
        </p>
        <p>
          <strong>Created:</strong> {new Date(consumer.createdTimestamp).toLocaleString()}
        </p>
        <p>
          <Button
            type="link"
            icon={<LineChartOutlined />}
            onClick={() => handleViewMetrics(consumer.clientId)}
            style={{ padding: 0 }}
          >
            View Metrics
          </Button>
        </p>
      </div>
    )
  }

  // Определение колонок (AC1)
  const columns: ColumnsType<Consumer> = [
    {
      title: 'Client ID',
      dataIndex: 'clientId',
      key: 'clientId',
      sorter: (a, b) => a.clientId.localeCompare(b.clientId),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      key: 'status',
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>{enabled ? 'Active' : 'Disabled'}</Tag>
      ),
      filters: [
        { text: 'Active', value: true },
        { text: 'Disabled', value: false },
      ],
      onFilter: (value, record) => record.enabled === value,
    },
    {
      title: 'Rate Limit',
      key: 'rateLimit',
      render: (_, record) => {
        if (!record.rateLimit) {
          return <Text type="secondary">—</Text>
        }
        return (
          <Text>
            {record.rateLimit.requestsPerSecond} req/s, burst {record.rateLimit.burstSize}
          </Text>
        )
      },
    },
    {
      title: 'Created',
      dataIndex: 'createdTimestamp',
      key: 'created',
      render: (timestamp: number) => new Date(timestamp).toLocaleDateString('ru-RU'),
      sorter: (a, b) => a.createdTimestamp - b.createdTimestamp,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space size="small">
          {/* Rotate Secret (AC3) */}
          <Popconfirm
            title="Ротировать secret?"
            description="Старый secret станет невалидным."
            onConfirm={() => handleRotateSecret(record.clientId)}
            okText="Да"
            cancelText="Нет"
          >
            <Button
              size="small"
              icon={<KeyOutlined />}
              loading={rotateSecretMutation.isPending}
            >
              Rotate Secret
            </Button>
          </Popconfirm>

          {/* Disable / Enable (AC4, AC5) */}
          {record.enabled ? (
            <Popconfirm
              title="Деактивировать consumer?"
              description="Consumer не сможет аутентифицироваться."
              onConfirm={() => handleDisable(record.clientId)}
              okText="Да"
              cancelText="Нет"
            >
              <Button
                size="small"
                icon={<StopOutlined />}
                loading={disableMutation.isPending}
                danger
              >
                Disable
              </Button>
            </Popconfirm>
          ) : (
            <Button
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleEnable(record.clientId)}
              loading={enableMutation.isPending}
            >
              Enable
            </Button>
          )}

          {/* Set Rate Limit (AC8) */}
          <Button
            size="small"
            icon={<ThunderboltOutlined />}
            onClick={() => handleSetRateLimit(record.clientId)}
          >
            Set Rate Limit
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Table<Consumer>
        columns={columns}
        dataSource={data?.items || []}
        loading={isLoading}
        rowKey="clientId"
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: data?.total || 0,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} consumers`,
        }}
        onChange={handleTableChange}
        expandable={{
          expandedRowRender,
          rowExpandable: () => true,
        }}
      />

      {/* Модальное окно для отображения secret после ротации */}
      {secretData && (
        <SecretModal
          open={secretModalOpen}
          clientId={secretData.clientId}
          secret={secretData.secret}
          onClose={() => {
            setSecretModalOpen(false)
            setSecretData(null)
          }}
        />
      )}

      {/* Модальное окно для управления rate limit */}
      {selectedConsumerId && (
        <ConsumerRateLimitModal
          open={rateLimitModalOpen}
          consumerId={selectedConsumerId}
          onClose={() => {
            setRateLimitModalOpen(false)
            setSelectedConsumerId(null)
          }}
        />
      )}
    </>
  )
}

export default ConsumersTable
