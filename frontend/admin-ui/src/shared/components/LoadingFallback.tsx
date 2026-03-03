// Компонент отображения загрузки для Suspense fallback
// Используется при lazy-загрузке route компонентов
import { Spin } from 'antd'

// Fallback компонент для Suspense — отображается во время загрузки lazy chunk
export function LoadingFallback() {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100%',
        minHeight: '200px',
        gap: '16px',
      }}
      data-testid="loading-fallback"
    >
      <Spin size="large" />
      <span style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Загрузка...</span>
    </div>
  )
}
