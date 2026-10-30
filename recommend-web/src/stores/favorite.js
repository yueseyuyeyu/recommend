import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { addFavorite, getFavoriteIds, removeFavorite } from '../api'

/**
 * 收藏状态仓库。
 *
 * <p>收藏状态需要在「首页卡片、详情页、收藏夹页、导航栏角标」四处同步，
 * 如果各页面自己维护一份，取消收藏后角标不会更新。
 * 因此统一收口到这里：一次加载全量 ID，各处只读，改只走 {@link toggle}。
 */
export const useFavoriteStore = defineStore('favorite', () => {
  /** 已收藏的商品 ID 集合 */
  const ids = ref(new Set())

  /** 收藏总数，供导航栏角标使用 */
  const count = computed(() => ids.value.size)

  /** 某个商品是否已收藏 */
  function isFavorited(productId) {
    return ids.value.has(productId)
  }

  /** 登录后加载全量已收藏 ID；未登录时清空 */
  async function load() {
    try {
      const list = (await getFavoriteIds()) || []
      ids.value = new Set(list.map(Number))
    } catch {
      ids.value = new Set()
    }
  }

  /** 退出登录时清空，避免下一个账号看到上一个账号的红心 */
  function reset() {
    ids.value = new Set()
  }

  /**
   * 切换收藏状态。
   *
   * <p>采用乐观更新：先改本地状态让爱心立刻变色，请求失败再回滚。
   * 收藏是高频轻操作，等 200ms 网络往返才变色体验很差。
   *
   * @param {number} productId 商品 ID
   * @returns {Promise<boolean>} 切换后是否为「已收藏」
   */
  async function toggle(productId) {
    const wasFavorited = ids.value.has(productId)
    const optimistic = new Set(ids.value)
    if (wasFavorited) optimistic.delete(productId)
    else optimistic.add(productId)
    ids.value = optimistic

    try {
      if (wasFavorited) await removeFavorite(productId)
      else await addFavorite(productId)
      return !wasFavorited
    } catch (error) {
      // 回滚
      const rollback = new Set(ids.value)
      if (wasFavorited) rollback.add(productId)
      else rollback.delete(productId)
      ids.value = rollback
      throw error
    }
  }

  return { ids, count, isFavorited, load, reset, toggle }
})
