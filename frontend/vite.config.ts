import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 개발 중 프론트(:5173)에서 백엔드(:8080)를 같은 오리진처럼 호출 (CORS 회피)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
