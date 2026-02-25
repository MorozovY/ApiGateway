// Hook для debounce значения (Story 12.9)
import { useEffect, useState } from 'react'

/**
 * Debounce значения с указанной задержкой.
 *
 * @param value - значение для debounce
 * @param delay - задержка в миллисекундах
 * @returns debounced значение
 */
export function useDebouncedValue<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value)

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value)
    }, delay)

    return () => {
      clearTimeout(timer)
    }
  }, [value, delay])

  return debouncedValue
}
