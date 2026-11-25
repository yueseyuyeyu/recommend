import { beforeEach, describe, expect, it, vi } from 'vitest'

// http.js 在模块加载时就引用了 ElMessage，必须先 mock 再导入
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn() }
}))

import { ElMessage } from 'element-plus'
import http from './http'

/** 直接取 axios 实例上注册的拦截器，避免为了测试再加一层 mock server */
const requestFulfilled = http.interceptors.request.handlers[0].fulfilled
const responseFulfilled = http.interceptors.response.handlers[0].fulfilled
const responseRejected = http.interceptors.response.handlers[0].rejected

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  // jsdom 不允许真正跳转，替换成可观测的假对象
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { pathname: '/profile', search: '?tab=info', href: '' }
  })
})

describe('请求拦截器', () => {
  it('有令牌时自动带上 Bearer 头', () => {
    localStorage.setItem('token', 'abc123')
    const config = requestFulfilled({ headers: {} })
    expect(config.headers.Authorization).toBe('Bearer abc123')
  })

  it('没有令牌时不加头', () => {
    const config = requestFulfilled({ headers: {} })
    expect(config.headers.Authorization).toBeUndefined()
  })
})

describe('响应拦截器', () => {
  it('code=0 时直接返回 data，调用方不用层层解包', () => {
    const result = responseFulfilled({ data: { code: 0, message: 'success', data: { id: 9 } } })
    expect(result).toEqual({ id: 9 })
  })

  it('非 0 业务码弹出后端给出的提示并 reject', async () => {
    await expect(
      responseFulfilled({ data: { code: 2002, message: '用户名或密码错误' } })
    ).rejects.toThrow('用户名或密码错误')
    expect(ElMessage.error).toHaveBeenCalledWith('用户名或密码错误')
  })

  it('code=1002 清空登录态并带 redirect 跳登录页', async () => {
    localStorage.setItem('token', 't')
    localStorage.setItem('userInfo', '{"id":1}')

    await expect(
      responseFulfilled({ data: { code: 1002, message: '未登录或登录已过期' } })
    ).rejects.toThrow()

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    // 跳转目标要带上当前页面，登录后才能回到原处
    expect(window.location.href).toBe('/login?redirect=%2Fprofile%3Ftab%3Dinfo')
    expect(ElMessage.warning).toHaveBeenCalledWith('登录已过期，请重新登录')
  })

  it('已经在登录页时不再重复跳转，避免刷新死循环', async () => {
    window.location.pathname = '/login'
    await expect(
      responseFulfilled({ data: { code: 1002, message: '未登录或登录已过期' } })
    ).rejects.toThrow()
    expect(window.location.href).toBe('')
  })

  it('网络异常统一提示并 reject', async () => {
    const error = { response: { data: { message: '服务暂时不可用' } }, message: 'Request failed' }
    await expect(responseRejected(error)).rejects.toBe(error)
    expect(ElMessage.error).toHaveBeenCalledWith('服务暂时不可用')
  })
})
