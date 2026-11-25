import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端单测配置。与 vite.config.js 分开：那个负责开发/构建，这个只管测试环境。
export default defineConfig({
  plugins: [vue()],
  test: {
    // 组件与 store 都会碰 localStorage / window，需要 DOM 环境
    environment: 'jsdom',
    include: ['src/**/*.test.js'],
    // 不开启 globals：显式从 vitest 导入 describe/it/expect，
    // 编辑器与静态检查都能直接识别，不依赖额外类型声明
    globals: false
  }
})
