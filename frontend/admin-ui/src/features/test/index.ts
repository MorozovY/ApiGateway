// Публичные экспорты feature test (Story 8.9)

export { TestPage } from './components/TestPage'
export { LoadGeneratorForm } from './components/LoadGeneratorForm'
export { LoadGeneratorProgress } from './components/LoadGeneratorProgress'
export { LoadGeneratorSummary } from './components/LoadGeneratorSummary'
export { useLoadGenerator } from './hooks/useLoadGenerator'
export type {
  LoadGeneratorConfig,
  LoadGeneratorState,
  // Экспортируем тип с alias чтобы избежать конфликта с компонентом LoadGeneratorSummary
  LoadGeneratorSummary as LoadGeneratorSummaryData,
  RouteOption,
} from './types/loadGenerator.types'
