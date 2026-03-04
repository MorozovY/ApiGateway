// Утилита для подсветки поискового термина в тексте (Story 16.10 — code review refactoring)
import type { ReactNode } from 'react'

/**
 * Подсвечивает поисковый термин в тексте.
 *
 * Возвращает React элемент с подсвеченным совпадением в теге <mark>.
 * Поиск регистронезависимый.
 *
 * @param text - исходный текст
 * @param searchTerm - поисковый термин для подсветки (опционально)
 * @returns React элемент с подсвеченным текстом или исходный текст
 */
export function highlightSearchTerm(text: string, searchTerm: string | undefined): ReactNode {
  if (!searchTerm || !text) {
    return text
  }

  const lowerText = text.toLowerCase()
  const lowerSearch = searchTerm.toLowerCase()
  const index = lowerText.indexOf(lowerSearch)

  if (index === -1) {
    return text
  }

  const before = text.slice(0, index)
  const match = text.slice(index, index + searchTerm.length)
  const after = text.slice(index + searchTerm.length)

  return (
    <>
      {before}
      <mark style={{ backgroundColor: '#ffc069', padding: 0 }}>{match}</mark>
      {after}
    </>
  )
}
