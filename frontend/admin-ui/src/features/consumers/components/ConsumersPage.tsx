// Страница управления consumers (Story 12.9, AC1)
import { useState } from 'react'
import { Button, Input, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import ConsumersTable from './ConsumersTable'
import CreateConsumerModal from './CreateConsumerModal'
import { useDebouncedValue } from '@shared/hooks/useDebouncedValue'

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
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          Consumers
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
          Create Consumer
        </Button>
      </div>

      {/* Поиск по client ID (AC9) */}
      <div style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="Search by client ID..."
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          allowClear
          style={{ width: 300 }}
        />
      </div>

      <ConsumersTable search={debouncedSearch} />

      <CreateConsumerModal open={isCreateModalOpen} onClose={handleModalClose} />
    </div>
  )
}

export default ConsumersPage
