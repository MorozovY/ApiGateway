import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { visualizer } from 'rollup-plugin-visualizer'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    // Включаем visualizer только при analyze mode (npm run build:analyze)
    mode === 'analyze' &&
      visualizer({
        filename: 'dist/stats.html',
        open: true,
        gzipSize: true,
        brotliSize: true,
        template: 'treemap', // или 'sunburst', 'network'
      }),
  ].filter(Boolean),
  resolve: {
    alias: {
      '@/': `${path.resolve(__dirname, './src')}/`,
      '@features/': `${path.resolve(__dirname, './src/features')}/`,
      '@shared/': `${path.resolve(__dirname, './src/shared')}/`,
      '@layouts/': `${path.resolve(__dirname, './src/layouts')}/`,
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Vendor chunks — стабильные библиотеки, редко меняются, хорошо кэшируются
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          // Antd split: core отдельно от icons (icons ~100KB)
          'vendor-antd': ['antd'],
          'vendor-antd-icons': ['@ant-design/icons'],
          'vendor-charts': ['@ant-design/charts'],
          'vendor-utils': ['axios', 'dayjs', 'zod', '@tanstack/react-query'],
          'vendor-auth': ['oidc-client-ts', 'react-oidc-context'],
          'vendor-forms': ['react-hook-form', '@hookform/resolvers'],
        },
      },
    },
    // Предупреждение если chunk > 500KB
    chunkSizeWarningLimit: 500,
  },
  server: {
    port: 3000,
    host: true, // Доступ снаружи контейнера (для Docker)
    // Разрешаем доступ с внешнего домена и внутренней сети
    allowedHosts: ['gateway.ymorozov.ru', 'localhost', '192.168.0.168'],
    // HMR через Traefik reverse proxy для внешнего доступа (Story 13.8)
    hmr: {
      // При доступе через gateway.ymorozov.ru используем порт 443 (Traefik HTTPS)
      // При локальном доступе localhost:3000 работает напрямую
      clientPort: (() => {
        const port = parseInt(process.env.VITE_HMR_PORT || '3000', 10)
        return Number.isNaN(port) ? 3000 : port
      })(),
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
}))
