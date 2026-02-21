import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  // Base path для деплоя на подпуть /ApiGateway/
  base: '/ApiGateway/',
  plugins: [react()],
  resolve: {
    alias: {
      '@/': `${path.resolve(__dirname, './src')}/`,
      '@features/': `${path.resolve(__dirname, './src/features')}/`,
      '@shared/': `${path.resolve(__dirname, './src/shared')}/`,
      '@layouts/': `${path.resolve(__dirname, './src/layouts')}/`,
    },
  },
  server: {
    port: 3000,
    host: true, // Доступ снаружи контейнера (для Docker)
    // Разрешаем доступ с внешнего домена (защита от DNS rebinding)
    allowedHosts: ['ymorozov.ru', 'localhost'],
    // HMR отключён для совместимости с доступом через ymorozov.ru
    // Hot reload не работает, но страница загружается
    hmr: false,
    proxy: {
      '/api': {
        // В Docker используем имя сервиса, локально — localhost
        target: process.env.VITE_API_URL || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
    watch: {
      // Для работы HMR в Docker с volume mounts
      usePolling: true,
    },
  },
})
