import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, register as registerApi } from '../api'

const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'

function readCachedUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY)) || null
  } catch {
    // 本地缓存损坏时直接丢弃，避免页面启动崩溃
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref(readCachedUser())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 2)

  // 仅持久化展示所需字段，密码等敏感信息不落盘
  function saveUser(data) {
    token.value = data.token || ''
    userInfo.value = {
      id: data.id,
      username: data.username,
      nickname: data.nickname,
      role: data.role
    }
    localStorage.setItem(TOKEN_KEY, token.value)
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
  }

  async function login(payload) {
    const data = await loginApi(payload)
    saveUser(data)
    return data
  }

  async function register(payload) {
    const data = await registerApi(payload)
    saveUser(data)
    return data
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, userInfo, isLoggedIn, isAdmin, login, register, logout }
})
