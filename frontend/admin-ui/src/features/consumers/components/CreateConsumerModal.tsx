// Модальное окно создания consumer (Story 12.9, AC2)
import { useState } from 'react'
import { Modal, Form, Input, Alert } from 'antd'
import { useCreateConsumer } from '../hooks/useConsumers'
import type { CreateConsumerRequest } from '../types/consumer.types'
import SecretModal from './SecretModal'

interface CreateConsumerModalProps {
  open: boolean
  onClose: () => void
}

/**
 * Модальное окно для создания нового consumer.
 *
 * После успешного создания показывает secret в отдельном модальном окне.
 * ВАЖНО: Secret показывается только один раз!
 */
function CreateConsumerModal({ open, onClose }: CreateConsumerModalProps) {
  const [form] = Form.useForm<CreateConsumerRequest>()
  const createMutation = useCreateConsumer()

  // Состояние для отображения secret после создания
  const [secretModalOpen, setSecretModalOpen] = useState(false)
  const [createdData, setCreatedData] = useState<{ clientId: string; secret: string } | null>(null)

  // Обработчик отправки формы
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()

      createMutation.mutate(values, {
        onSuccess: (response) => {
          // Показываем secret в отдельном модальном окне
          setCreatedData({ clientId: response.clientId, secret: response.secret })
          setSecretModalOpen(true)
          form.resetFields()
          onClose()
        },
      })
    } catch (error) {
      // Validation failed
      console.error('Validation failed:', error)
    }
  }

  // Обработчик закрытия модального окна
  const handleCancel = () => {
    form.resetFields()
    onClose()
  }

  return (
    <>
      <Modal
        title="Create Consumer"
        open={open}
        onOk={handleSubmit}
        onCancel={handleCancel}
        confirmLoading={createMutation.isPending}
        okText="Create"
        cancelText="Cancel"
        destroyOnClose
      >
        <Alert
          message="Consumer Authentication"
          description="Consumer использует Client Credentials flow для аутентификации. Secret будет показан только один раз после создания."
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />

        <Form form={form} layout="vertical">
          <Form.Item
            name="clientId"
            label="Client ID"
            rules={[
              { required: true, message: 'Client ID обязателен' },
              {
                pattern: /^[a-z0-9](-?[a-z0-9])*$/,
                message: 'Client ID должен содержать только lowercase буквы, цифры и дефисы (без leading/trailing дефиса)',
              },
              {
                min: 3,
                max: 63,
                message: 'Client ID должен быть от 3 до 63 символов',
              },
            ]}
          >
            <Input placeholder="company-a, partner-api, mobile-app-v2" />
          </Form.Item>

          <Form.Item
            name="description"
            label="Description"
            rules={[
              {
                max: 255,
                message: 'Description не должно превышать 255 символов',
              },
            ]}
          >
            <Input.TextArea rows={3} placeholder="Описание consumer (опционально)" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Модальное окно для отображения secret */}
      {createdData && (
        <SecretModal
          open={secretModalOpen}
          clientId={createdData.clientId}
          secret={createdData.secret}
          onClose={() => {
            setSecretModalOpen(false)
            setCreatedData(null)
          }}
        />
      )}
    </>
  )
}

export default CreateConsumerModal
