import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期把后端路径代理到本地 Spring Boot，避免跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 显式监听所有地址（含 IPv4 127.0.0.1 与 IPv6 ::1）。
    // 不设 host 时 Node 17+ 会把 localhost 解析成 ::1，dev server 只绑 IPv6，
    // 用 127.0.0.1:5173 访问会直接「无法访问此网站」。
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 用户上传的头像由后端以静态资源形式提供，必须一并代理。
      // 少了这一条，/uploads/xxx.png 会命中 dev server 自己并返回 index.html
      // （Content-Type 是 text/html），<img> 加载失败后 el-avatar 静默回退成
      // 昵称首字，表现就是「提示上传成功但头像没变」。
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
