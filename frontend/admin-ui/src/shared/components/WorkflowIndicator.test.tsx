// Unit тесты для WorkflowIndicator (Story 16.10)
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WorkflowIndicator } from './WorkflowIndicator'

describe('WorkflowIndicator', () => {
  it('отображает 4 шага workflow', () => {
    render(<WorkflowIndicator currentStep={0} />)

    expect(screen.getByText('Создание')).toBeInTheDocument()
    expect(screen.getByText('Отправка')).toBeInTheDocument()
    expect(screen.getByText('Согласование')).toBeInTheDocument()
    expect(screen.getByText('Публикация')).toBeInTheDocument()
  })

  it('имеет data-testid для E2E тестов', () => {
    render(<WorkflowIndicator currentStep={0} />)

    expect(screen.getByTestId('workflow-indicator')).toBeInTheDocument()
  })

  it('принимает prop currentStep', () => {
    const { rerender } = render(<WorkflowIndicator currentStep={0} />)
    expect(screen.getByTestId('workflow-indicator')).toBeInTheDocument()

    rerender(<WorkflowIndicator currentStep={2} />)
    expect(screen.getByTestId('workflow-indicator')).toBeInTheDocument()
  })

  it('принимает размер small по умолчанию', () => {
    render(<WorkflowIndicator currentStep={1} />)
    // Проверяем что компонент рендерится с size=small (класс Ant Design)
    const indicator = screen.getByTestId('workflow-indicator')
    expect(indicator.querySelector('.ant-steps-small')).toBeInTheDocument()
  })

  it('отображает иконки для каждого шага', () => {
    render(<WorkflowIndicator currentStep={0} />)

    // Иконки рендерятся внутри .ant-steps-icon
    const steps = screen.getByTestId('workflow-indicator')
    const icons = steps.querySelectorAll('.ant-steps-icon')
    expect(icons.length).toBe(4)
  })
})
