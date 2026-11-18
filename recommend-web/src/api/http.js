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
        // 令牌失效：清掉本地登录态并回到登录页，否则用户会一直看到错误却仍是“已登录”
        if (result.code === 1002) {
          clearLoginState()
          if (!window.location.pathname.startsWith('/login')) {
            const redirect = encodeURIComponent(window.location.pathname + window.location.search)
            window.location.href = `/login?redirect=${redirect}`
          }
          ElMessage.warning('登录已过期，请重新登录')
          return Promise.reject(new Error(result.message || '登录已过期'))
        }
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

/** 清除本地登录态。key 必须与 stores/user.js 保持一致 */
function clearLoginState() {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}

export default http
