// Error Boundary для обработки ошибок загрузки lazy chunks
// Ловит ошибки динамической загрузки модулей и предлагает перезагрузку
import { Component, ReactNode } from 'react'
import { Result, Button } from 'antd'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error?: Error
}

// Error boundary для обработки ошибок загрузки lazy chunks
export class LazyErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false }
  }

  // Обновляем state при ошибке — компонент покажет fallback UI
  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  // Обработчик повторной попытки — перезагружает страницу
  handleRetry = () => {
    this.setState({ hasError: false, error: undefined })
    window.location.reload()
  }

  render() {
    if (this.state.hasError) {
      return (
        <Result
          status="error"
          title="Ошибка загрузки"
          subTitle="Не удалось загрузить компонент. Попробуйте обновить страницу."
          extra={
            <Button
              type="primary"
              onClick={this.handleRetry}
              data-testid="retry-button"
            >
              Обновить страницу
            </Button>
          }
          data-testid="lazy-error-boundary"
        />
      )
    }

    return this.props.children
  }
}
