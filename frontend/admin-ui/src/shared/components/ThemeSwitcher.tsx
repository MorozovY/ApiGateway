// Компонент переключения темы (light/dark)
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
        data-testid="theme-switcher"
      />
    </Tooltip>
  )
}
