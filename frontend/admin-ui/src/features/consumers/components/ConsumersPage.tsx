// Страница управления consumers (Story 12.9, AC1; Story 15.4 — добавлен PageInfoBlock; Story 15.6 — унификация заголовка; Story 16.5 — empty state)
import { useState } from 'react'
import { Button, Input, Typography, Space, Card } from 'antd'
import { PlusOutlined, UserSwitchOutlined } from '@ant-design/icons'
import ConsumersTable from './ConsumersTable'
import CreateConsumerModal from './CreateConsumerModal'
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue'
import { PageInfoBlock } from '@shared/components/PageInfoBlock'
import { PAGE_DESCRIPTIONS } from '@shared/config/pageDescriptions'

const { Title } = Typography

/**
 * Страница управления API consumers.
 *
 * Включает таблицу consumers с пагинацией, поиском и кнопку создания.
 */
function ConsumersPage() {
  // Состояние модального окна создания
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)

  // Состояние поиска (debounced)
  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebouncedValue(searchInput, 300)

  // Открытие модального окна для создания
  const handleCreate = () => {
    setIsCreateModalOpen(true)
  }

  // Закрытие модального окна
  const handleModalClose = () => {
    setIsCreateModalOpen(false)
  }

  return (
    <Card>
      {/* Заголовок страницы (Story 15.6 — унификация) */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <UserSwitchOutlined style={{ fontSize: 24, color: '#1890ff' }} />
            <Title level={3} style={{ margin: 0 }}>
              Потребители
            </Title>
          </Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate} data-testid="create-consumer-button">
            Новый потребитель
          </Button>
        </Space>
      </div>

      {/* Инфо-блок (Story 15.4) */}
      <PageInfoBlock pageKey="consumers" {...PAGE_DESCRIPTIONS.consumers} />

      {/* Поиск по client ID (AC9) */}
      <div style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="Поиск по client ID..."
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          allowClear
          style={{ width: 300 }}
          data-testid="consumer-search-input"
        />
      </div>

      {/* Story 16.5 AC4: передаём callback для empty state CTA */}
      <ConsumersTable search={debouncedSearch} onCreateClick={handleCreate} />

      <CreateConsumerModal open={isCreateModalOpen} onClose={handleModalClose} />
    </Card>
  )
}

export default ConsumersPage
