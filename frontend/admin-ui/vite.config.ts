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
