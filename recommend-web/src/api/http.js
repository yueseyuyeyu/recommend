import axios from 'axios'
import { ElMessage } from 'element-plus'

// 所有请求统一走 /api，开发环境由 Vite 代理到 8080
const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data
    // 后端统一响应体 {code,message,data,timestamp}，code=0 才算成功
    if (result && typeof result.code === 'number') {
      if (result.code !== 0) {
        ElMessage.error(result.message || '请求失败')
        return Promise.reject(new Error(result.message || '请求失败'))
      }
      return result.data
    }
    return result
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
