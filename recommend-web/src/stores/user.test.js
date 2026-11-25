import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 只 mock 接口层，store 本身走真实逻辑
vi.mock('../api', () => ({
  login: vi.fn(async () => ({
    token: 'jwt-abc',
    id: 7,
    username: 'alice',
    nickname: '小艾',
    avatar: '/uploads/avatar/a.png',
    role: 2,
    // 后端不会下发密码，这里额外塞一个字段验证 store 的字段白名单
    password: 'should-never-be-stored'
  })),
  register: vi.fn(async () => ({ token: 'jwt-reg', id: 8, username: 'bob', nickname: '鲍勃', role: 1 }))
}))

import { login as loginApi } from '../api'
import { useUserStore } from './user'

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('user store', () => {
  it('登录后持久化令牌与展示字段，且不落盘敏感字段', async () => {
    const store = useUserStore()
    await store.login({ username: 'alice', password: 'secret' })

    expect(loginApi).toHaveBeenCalledWith({ username: 'alice', password: 'secret' })
    expect(store.isLoggedIn).toBe(true)
    expect(localStorage.getItem('token')).toBe('jwt-abc')

    const cached = JSON.parse(localStorage.getItem('userInfo'))
    expect(cached.nickname).toBe('小艾')
    // 白名单机制：多余字段一律不进内存与缓存
    expect(cached.password).toBeUndefined()
    expect(store.userInfo.password).toBeUndefined()
  })

  it('role=2 判定为管理员', async () => {
    const store = useUserStore()
    await store.login({})
    expect(store.isAdmin).toBe(true)
  })

  it('patchUserInfo 合并字段并同步缓存', async () => {
    const store = useUserStore()
    await store.login({})
    store.patchUserInfo({ nickname: '改名了', avatar: '/new.png' })

    expect(store.userInfo.nickname).toBe('改名了')
    expect(store.userInfo.username).toBe('alice')
    expect(JSON.parse(localStorage.getItem('userInfo')).nickname).toBe('改名了')
  })

  it('登出清空内存与本地存储', async () => {
    const store = useUserStore()
    await store.login({})
    store.logout()

    expect(store.isLoggedIn).toBe(false)
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
  })

  it('本地缓存损坏时降级为空并清掉脏数据，不让页面启动崩溃', () => {
    localStorage.setItem('userInfo', 'not-a-json')
    const store = useUserStore()
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
  })

  it('刷新页面后从缓存恢复登录态', async () => {
    const first = useUserStore()
    await first.login({})

    // 模拟刷新：新建 pinia 与 store，只依赖 localStorage
    setActivePinia(createPinia())
    const second = useUserStore()
    expect(second.isLoggedIn).toBe(true)
    expect(second.userInfo.nickname).toBe('小艾')
  })
})
