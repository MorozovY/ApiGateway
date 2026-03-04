// Unit тесты для getCurrentWorkflowStep (Story 16.10)
import { describe, it, expect } from 'vitest'
import { getCurrentWorkflowStep } from './workflowStep'

describe('getCurrentWorkflowStep', () => {
  describe('Шаг 0: Создание', () => {
    it('возвращает 0 для /routes/new', () => {
      expect(getCurrentWorkflowStep('/routes/new')).toBe(0)
    })
  })

  describe('Шаг 1: Отправка на согласование', () => {
    it('возвращает 1 для /routes/:id с draft статусом', () => {
      expect(getCurrentWorkflowStep('/routes/123', 'draft')).toBe(1)
    })

    it('возвращает 1 для /routes/:id с rejected статусом', () => {
      expect(getCurrentWorkflowStep('/routes/abc-def', 'rejected')).toBe(1)
    })

    it('возвращает 1 для /routes/:id/edit', () => {
      expect(getCurrentWorkflowStep('/routes/123/edit')).toBe(1)
    })
  })

  describe('Шаг 2: Согласование', () => {
    it('возвращает 2 для /approvals', () => {
      expect(getCurrentWorkflowStep('/approvals')).toBe(2)
    })
  })

  describe('Шаг 3: Публикация', () => {
    it('возвращает 3 для /routes (список)', () => {
      expect(getCurrentWorkflowStep('/routes')).toBe(3)
    })

    it('возвращает 3 для /routes/:id с published статусом', () => {
      expect(getCurrentWorkflowStep('/routes/123', 'published')).toBe(3)
    })

    it('возвращает 3 для /routes/:id с pending статусом', () => {
      expect(getCurrentWorkflowStep('/routes/123', 'pending')).toBe(3)
    })
  })

  describe('Default случаи', () => {
    it('возвращает 3 для неизвестного пути', () => {
      expect(getCurrentWorkflowStep('/unknown')).toBe(3)
    })

    it('возвращает 3 для /routes/:id без статуса', () => {
      expect(getCurrentWorkflowStep('/routes/123')).toBe(3)
    })
  })
})
