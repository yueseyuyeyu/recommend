import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import ProductCard from './ProductCard.vue'

function mountCard(item, extraProps = {}) {
  return mount(ProductCard, {
    props: { item, ...extraProps },
    global: {
      // 卡片外层是 el-popover 的 reference 插槽，不注册 Element Plus 的话
      // 整个 .card 都不会渲染，点击测试也就无从下手
      plugins: [createPinia(), ElementPlus]
    }
  })
}

describe('ProductCard', () => {
  const item = {
    id: 12,
    name: '5G手机 精选款 012',
    subtitle: '手机数码 · 手机',
    price: 2999.5,
    sales: 321
  }

  it('渲染商品名、价格与销量', () => {
    const wrapper = mountCard(item)
    const text = wrapper.text()
    expect(text).toContain('5G手机 精选款 012')
    expect(text).toContain('2999.5')
    expect(text).toContain('321')
  })

  it('点击卡片抛出 open 事件并带上商品 id，交给父组件决定跳转', async () => {
    const wrapper = mountCard(item)
    await wrapper.find('.card').trigger('click')

    const emitted = wrapper.emitted('open')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0].id).toBe(12)
  })

  it('showReason 为真且有推荐理由时展示理由标签', () => {
    const wrapper = mountCard(
      { ...item, reason: '与您兴趣相似的 20 位用户也在关注' },
      { showReason: true }
    )
    expect(wrapper.text()).toContain('与您兴趣相似的 20 位用户也在关注')
  })

  it('sales 缺失时不渲染 undefined', () => {
    const wrapper = mountCard({ ...item, sales: undefined })
    expect(wrapper.text()).not.toContain('undefined')
  })
})
