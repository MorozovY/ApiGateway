import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
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
    // Разрешаем доступ с внешнего домена и внутренней сети
    allowedHosts: ['gateway.ymorozov.ru', 'localhost', '192.168.0.168'],
    // HMR через Nginx reverse proxy для внешнего доступа (Story 12.9.1 Hotfix)
    hmr: {
      // При доступе через gateway.ymorozov.ru используем порт 80 (Nginx)
      // При локальном доступе localhost:3000 работает напрямую
      clientPort: process.env.VITE_HMR_PORT ? parseInt(process.env.VITE_HMR_PORT) : 3000,
    },
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
