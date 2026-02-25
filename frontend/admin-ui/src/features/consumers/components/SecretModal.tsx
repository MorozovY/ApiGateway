// Модальное окно для отображения client secret (Story 12.9, AC2, AC3)
import { Modal, Alert, Input, Button, Space, Typography, message as antMessage } from 'antd'
import { CopyOutlined } from '@ant-design/icons'

const { Paragraph, Text } = Typography

interface SecretModalProps {
  open: boolean
  clientId: string
  secret: string
  onClose: () => void
}

/**
 * Модальное окно для отображения client secret.
 *
 * ВАЖНО: Secret показывается только один раз после создания или ротации!
 * Пользователь должен сохранить его немедленно.
 */
function SecretModal({ open, clientId, secret, onClose }: SecretModalProps) {
  // Обработчик копирования secret
  const handleCopySecret = () => {
    navigator.clipboard.writeText(secret)
    antMessage.success('Secret скопирован в буфер обмена')
  }

  return (
    <Modal
      title="Client Secret"
      open={open}
      onCancel={onClose}
      footer={[
        <Button key="close" type="primary" onClick={onClose}>
          Закрыть
        </Button>,
      ]}
      destroyOnClose
    >
      <Alert
        message="ВАЖНО: Сохраните этот secret сейчас!"
        description="Этот secret будет показан только один раз и не может быть получен позже. Сохраните его в безопасном месте."
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Paragraph>
        <Text strong>Client ID:</Text> <Text code>{clientId}</Text>
      </Paragraph>

      <Paragraph>
        <Text strong>Client Secret:</Text>
      </Paragraph>

      <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
        <Input.Password value={secret} readOnly data-testid="consumer-secret-display" />
        <Button icon={<CopyOutlined />} onClick={handleCopySecret} data-testid="copy-secret-button">
          Copy
        </Button>
      </Space.Compact>

      <Alert
        message="Использование Client Credentials Flow"
        description={
          <div>
            <p>Для аутентификации отправьте POST запрос:</p>
            <pre style={{ background: '#f5f5f5', padding: 8, borderRadius: 4 }}>
              {`POST ${window.location.protocol}//${window.location.hostname}:8180/realms/api-gateway/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=${clientId}&client_secret=<YOUR_SECRET>`}
            </pre>
          </div>
        }
        type="info"
        style={{ marginTop: 16 }}
      />
    </Modal>
  )
}

export default SecretModal
