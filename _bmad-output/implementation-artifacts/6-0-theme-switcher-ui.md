# Story 6.0: Theme Switcher UI

Status: done

## Story

As a **User**,
I want to switch between dark and light themes in Admin UI,
so that I can use the interface comfortably in different lighting conditions.

## Acceptance Criteria

**AC1 — Theme switcher доступен на Dashboard:**

**Given** пользователь находится на /dashboard
**When** страница загружается
**Then** в header отображается кнопка/иконка переключения темы
**And** текущая тема визуально индицируется (солнце/луна)

**AC2 — Переключение темы работает:**

**Given** пользователь нажимает на theme switcher
**When** текущая тема light
**Then** тема меняется на dark
**And** все компоненты Ant Design применяют dark стили
**And** фон, текст, карточки соответствуют dark теме

**AC3 — Тема сохраняется в localStorage:**

**Given** пользователь выбрал dark тему
**When** пользователь перезагружает страницу или возвращается позже
**Then** выбранная тема восстанавливается из localStorage
**And** не происходит "мерцание" при загрузке (flash of unstyled content)

**AC4 — Системная тема по умолчанию:**

**Given** пользователь впервые открывает приложение
**When** в localStorage нет сохранённой темы
**Then** используется системная тема (prefers-color-scheme)
**And** если система в dark mode — применяется dark тема

## Tasks / Subtasks

- [x] Task 1: Создать ThemeProvider и useTheme hook (AC2, AC3, AC4)
  - [x] Создать `src/shared/hooks/useTheme.ts`
  - [x] Создать `src/shared/providers/ThemeProvider.tsx`
  - [x] Читать/сохранять тему в localStorage (key: `app-theme`)
  - [x] Определять системную тему через `window.matchMedia('(prefers-color-scheme: dark)')`

- [x] Task 2: Интегрировать Ant Design ConfigProvider для темы (AC2)
  - [x] Обернуть App в ConfigProvider с theme prop
  - [x] Использовать `theme.defaultAlgorithm` для light
  - [x] Использовать `theme.darkAlgorithm` для dark
  - [x] Настроить token overrides если нужно (primary color)

- [x] Task 3: Создать ThemeSwitcher компонент (AC1)
  - [x] Создать `src/shared/components/ThemeSwitcher.tsx`
  - [x] Иконка: солнце (light) / луна (dark)
  - [x] Использовать Ant Design icons: `SunOutlined`, `MoonOutlined`
  - [x] Добавить tooltip: "Переключить тему"

- [x] Task 4: Добавить ThemeSwitcher в MainLayout header (AC1)
  - [x] Разместить в правой части header рядом с user menu
  - [x] Стилизовать как icon button

- [x] Task 5: Предотвратить flash of unstyled content (AC3)
  - [x] Добавить inline script в index.html для раннего определения темы
  - [x] Установить class на `<html>` до рендера React

- [x] Task 6: Unit тесты
  - [x] Тест useTheme hook: toggle, persist, system default
  - [x] Тест ThemeSwitcher: renders, calls toggle

## Dev Notes

### Ant Design 5 Theme System

**ConfigProvider с темой:**
```tsx
import { ConfigProvider, theme } from 'antd'

function App() {
  const { isDark } = useTheme()

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1890ff',  // или ваш primary color
        },
      }}
    >
      {/* app content */}
    </ConfigProvider>
  )
}
```

### useTheme Hook Implementation

```typescript
// src/shared/hooks/useTheme.ts
import { useState, useEffect, useCallback } from 'react'

type Theme = 'light' | 'dark'

const STORAGE_KEY = 'app-theme'

function getSystemTheme(): Theme {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function getStoredTheme(): Theme | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(STORAGE_KEY) as Theme | null
}

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => {
    return getStoredTheme() ?? getSystemTheme()
  })

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, theme)
    // Обновляем class на html для CSS переменных если нужно
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme])

  const toggle = useCallback(() => {
    setTheme(prev => prev === 'light' ? 'dark' : 'light')
  }, [])

  return {
    theme,
    isDark: theme === 'dark',
    isLight: theme === 'light',
    toggle,
    setTheme,
  }
}
```

### ThemeProvider Implementation

```tsx
// src/shared/providers/ThemeProvider.tsx
import { createContext, useContext, ReactNode } from 'react'
import { useTheme } from '../hooks/useTheme'

type ThemeContextType = ReturnType<typeof useTheme>

const ThemeContext = createContext<ThemeContextType | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const themeValue = useTheme()

  return (
    <ThemeContext.Provider value={themeValue}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useThemeContext() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useThemeContext must be used within ThemeProvider')
  }
  return context
}
```

### ThemeSwitcher Component

```tsx
// src/shared/components/ThemeSwitcher.tsx
import { Button, Tooltip } from 'antd'
import { SunOutlined, MoonOutlined } from '@ant-design/icons'
import { useThemeContext } from '../providers/ThemeProvider'

export function ThemeSwitcher() {
  const { isDark, toggle } = useThemeContext()

  return (
    <Tooltip title={isDark ? 'Светлая тема' : 'Тёмная тема'}>
      <Button
        type="text"
        icon={isDark ? <SunOutlined /> : <MoonOutlined />}
        onClick={toggle}
        aria-label="Переключить тему"
      />
    </Tooltip>
  )
}
```

### Предотвращение FOUC (Flash of Unstyled Content)

**index.html — inline script перед React:**
```html
<!DOCTYPE html>
<html lang="ru">
  <head>
    <script>
      // Раннее определение темы до рендера React
      (function() {
        const stored = localStorage.getItem('app-theme');
        const system = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
        const theme = stored || system;
        document.documentElement.setAttribute('data-theme', theme);
        // Опционально: добавить class для body background
        if (theme === 'dark') {
          document.documentElement.style.backgroundColor = '#141414';
          document.documentElement.style.colorScheme = 'dark';
        }
      })();
    </script>
    <!-- ... -->
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

### Интеграция в App.tsx

```tsx
// src/App.tsx
import { ConfigProvider, theme as antTheme } from 'antd'
import { ThemeProvider, useThemeContext } from './shared/providers/ThemeProvider'

function AppContent() {
  const { isDark } = useThemeContext()

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
      }}
    >
      <RouterProvider router={router} />
    </ConfigProvider>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  )
}
```

### MainLayout Header Integration

```tsx
// src/layouts/MainLayout.tsx
import { ThemeSwitcher } from '../shared/components/ThemeSwitcher'

// В header section:
<Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
  <div>{/* logo/title */}</div>
  <Space>
    <ThemeSwitcher />
    <UserMenu />
  </Space>
</Header>
```

### Project Structure Notes

**Новые файлы:**
- `frontend/admin-ui/src/shared/hooks/useTheme.ts`
- `frontend/admin-ui/src/shared/providers/ThemeProvider.tsx`
- `frontend/admin-ui/src/shared/components/ThemeSwitcher.tsx`

**Модифицируемые файлы:**
- `frontend/admin-ui/src/App.tsx` — добавить ThemeProvider и ConfigProvider theme
- `frontend/admin-ui/src/layouts/MainLayout.tsx` — добавить ThemeSwitcher в header
- `frontend/admin-ui/index.html` — добавить inline script для FOUC prevention

### Testing

```bash
# Unit тесты
cd frontend/admin-ui
npm run test -- useTheme
npm run test -- ThemeSwitcher

# Manual testing
npm run dev
# Проверить:
# 1. Кнопка в header
# 2. Переключение темы
# 3. Перезагрузка страницы — тема сохранилась
# 4. Очистить localStorage, перезагрузить — системная тема
```

### References

- [Ant Design 5 Dark Mode](https://ant.design/docs/react/customize-theme#use-dark-theme)
- [Ant Design ConfigProvider](https://ant.design/components/config-provider)
- [prefers-color-scheme](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-color-scheme)

### Git Context

**Паттерн коммита:** `feat: implement Story 6.0 — Theme Switcher UI`

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

1. **Task 1 (useTheme + ThemeProvider):** Создан useTheme hook с поддержкой localStorage persistence, системной темы (prefers-color-scheme), toggle и setTheme. ThemeProvider предоставляет context для всего приложения.

2. **Task 2 (ConfigProvider integration):** main.tsx обновлён — добавлен ThemeProvider, создан ThemedApp компонент который использует useThemeContext для передачи isDark в ConfigProvider с darkAlgorithm/defaultAlgorithm.

3. **Task 3 (ThemeSwitcher component):** Создан компонент с Button type="text", иконками SunOutlined/MoonOutlined, tooltip и data-testid для E2E тестов.

4. **Task 4 (MainLayout integration):** ThemeSwitcher добавлен в header перед user info. Добавлены theme-aware цвета фона для Header и Content.

5. **Task 5 (FOUC prevention):** index.html обновлён — добавлен inline script для раннего определения темы, установки data-theme атрибута и backgroundColor до рендера React.

6. **Task 6 (Unit tests):** 31 тест для theme system — все проходят. Покрыты: useTheme (13), ThemeSwitcher (6), ThemeProvider (6), Integration (6).

### File List

**Новые файлы:**
- `frontend/admin-ui/src/shared/hooks/useTheme.ts`
- `frontend/admin-ui/src/shared/hooks/useTheme.test.ts`
- `frontend/admin-ui/src/shared/hooks/index.ts`
- `frontend/admin-ui/src/shared/providers/ThemeProvider.tsx`
- `frontend/admin-ui/src/shared/providers/ThemeProvider.test.tsx`
- `frontend/admin-ui/src/shared/providers/ThemeIntegration.test.tsx`
- `frontend/admin-ui/src/shared/providers/index.ts`
- `frontend/admin-ui/src/shared/components/ThemeSwitcher.tsx`
- `frontend/admin-ui/src/shared/components/ThemeSwitcher.test.tsx`
- `frontend/admin-ui/src/shared/components/index.ts`
- `frontend/admin-ui/src/shared/index.ts`

**Модифицированные файлы:**
- `frontend/admin-ui/src/main.tsx` — добавлен ThemeProvider и ThemedApp
- `frontend/admin-ui/src/layouts/MainLayout.tsx` — добавлен ThemeSwitcher и theme-aware стили
- `frontend/admin-ui/src/layouts/AuthLayout.tsx` — добавлены theme-aware стили для login страницы
- `frontend/admin-ui/index.html` — добавлен FOUC prevention script с валидацией localStorage

## Change Log

- 2026-02-19: Code Review — исправлены H1 (FOUC validation), добавлены тесты M1-M4 (ThemeProvider, Integration, listener tests)
- 2026-02-19: Fix — AuthLayout обновлён для поддержки dark theme (login страница)
- 2026-02-19: Story 6.0 реализована — Theme Switcher UI с dark/light поддержкой, localStorage persistence, FOUC prevention

