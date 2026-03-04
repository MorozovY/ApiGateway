// Unit тесты для getCurrentWorkflowStep (Story 16.10)
import { describe, it, expect } from 'vitest'
import { getCurrentWorkflowStep, WORKFLOW_STEP } from './workflowStep'

describe('getCurrentWorkflowStep', () => {
  describe('Шаг 0: Создание', () => {
    it('возвращает CREATION для /routes/new', () => {
      expect(getCurrentWorkflowStep('/routes/new')).toBe(WORKFLOW_STEP.CREATION)
    })
  })

  describe('Шаг 1: Отправка на согласование', () => {
    it('возвращает SUBMISSION для /routes/:id с draft статусом', () => {
      expect(getCurrentWorkflowStep('/routes/123', 'draft')).toBe(WORKFLOW_STEP.SUBMISSION)
    })

    it('возвращает SUBMISSION для /routes/:id с rejected статусом', () => {
      expect(getCurrentWorkflowStep('/routes/abc-def', 'rejected')).toBe(WORKFLOW_STEP.SUBMISSION)
    })

    it('возвращает SUBMISSION для /routes/:id/edit', () => {
      expect(getCurrentWorkflowStep('/routes/123/edit')).toBe(WORKFLOW_STEP.SUBMISSION)
    })
  })

  describe('Шаг 2: Согласование', () => {
    it('возвращает APPROVAL для /approvals', () => {
      expect(getCurrentWorkflowStep('/approvals')).toBe(WORKFLOW_STEP.APPROVAL)
    })
  })

  describe('Шаг 3: Публикация', () => {
    it('возвращает PUBLICATION для /routes (список)', () => {
      expect(getCurrentWorkflowStep('/routes')).toBe(WORKFLOW_STEP.PUBLICATION)
    })

    it('возвращает PUBLICATION для /routes/:id с published статусом', () => {
      expect(getCurrentWorkflowStep('/routes/123', 'published')).toBe(WORKFLOW_STEP.PUBLICATION)
    })

    it('возвращает PUBLICATION для /routes/:id с pending статусом', () => {
      expect(getCurrentWorkflowStep('/routes/123', 'pending')).toBe(WORKFLOW_STEP.PUBLICATION)
    })
  })

  describe('Default случаи', () => {
    it('возвращает PUBLICATION для неизвестного пути', () => {
      expect(getCurrentWorkflowStep('/unknown')).toBe(WORKFLOW_STEP.PUBLICATION)
    })

    it('возвращает PUBLICATION для /routes/:id без статуса', () => {
      expect(getCurrentWorkflowStep('/routes/123')).toBe(WORKFLOW_STEP.PUBLICATION)
    })
  })

  describe('WORKFLOW_STEP константы', () => {
    it('имеют правильные числовые значения', () => {
      expect(WORKFLOW_STEP.CREATION).toBe(0)
      expect(WORKFLOW_STEP.SUBMISSION).toBe(1)
      expect(WORKFLOW_STEP.APPROVAL).toBe(2)
      expect(WORKFLOW_STEP.PUBLICATION).toBe(3)
    })
  })
})
