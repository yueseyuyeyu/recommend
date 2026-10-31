<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getNotifications,
  getUnreadCount,
  logout as logoutApi,
  markAllNotificationsRead,
  markNotificationRead
} from './api'
import { useUserStore } from './stores/user'
import { useFavoriteStore } from './stores/favorite'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const favoriteStore = useFavoriteStore()

// 商品详情页沿用商品列表的高亮，登录/注册页不高亮任何菜单
const activeMenu = computed(() => {
  if (route.path.startsWith('/products')) return '/products'
  if (route.path.startsWith('/login') || route.path.startsWith('/register')) return ''
  return route.path
})

/** 无头像时用昵称/用户名首字兜底 */
const avatarText = computed(() => {
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || '?'
  return name.trim().charAt(0).toUpperCase()
})

/** 登录状态变化时同步收藏数据：登录则加载，退出则清空 */
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      favoriteStore.load()
      refreshUnread()
    } else {
      favoriteStore.reset()
      notifications.value = []
      unreadCount.value = 0
    }
  },
  { immediate: true }
)

async function handleLogout() {
  // 先请求服务端注销，让令牌进入黑名单立即失效。
  // 网络不通也要保证本地能退出——否则用户会卡在"退不出去"的状态。
  try {
    await logoutApi()
  } catch {
    // 忽略：本地清理优先
  }
  userStore.logout()
  favoriteStore.reset()
  notifications.value = []
  unreadCount.value = 0
  ElMessage.success('已退出登录')
  router.push('/')
}

/* ---------- 站内通知 ---------- */
const notifications = ref([])
const unreadCount = ref(0)
const notifyVisible = ref(false)
const notifyLoading = ref(false)
let notifyTimer = null

async function refreshUnread() {
  if (!userStore.isLoggedIn) {
    unreadCount.value = 0
    return
  }
  try {
    unreadCount.value = Number(await getUnreadCount()) || 0
  } catch {
    unreadCount.value = 0
  }
}

async function loadNotifications() {
  if (!userStore.isLoggedIn) return
  notifyLoading.value = true
  try {
    const page = await getNotifications(1, 8)
    notifications.value = page?.records || []
  } catch {
    notifications.value = []
  } finally {
    notifyLoading.value = false
  }
}

async function handleNotificationClick(item) {
  if (item.isRead !== 1) {
    try {
      await markNotificationRead(item.id)
      item.isRead = 1
      await refreshUnread()
    } catch {
      // 标记失败不影响跳转
    }
  }
  // 通知都是订单相关，直接去订单页
  router.push('/orders')
}

async function handleReadAll() {
  try {
    await markAllNotificationsRead()
    ElMessage.success('已全部标记为已读')
    await Promise.all([loadNotifications(), refreshUnread()])
  } catch {
    // 错误提示由拦截器处理
  }
}

function formatNotifyTime(time) {
  return (time || '').replace('T', ' ').slice(5, 16)
}

onBeforeUnmount(() => {
  if (notifyTimer) clearInterval(notifyTimer)
})

onMounted(() => {
  if (userStore.isLoggedIn) {
    favoriteStore.load()
    refreshUnread()
  }
  // 每 60 秒拉一次未读数，够用且不会给后端造成压力
  notifyTimer = setInterval(refreshUnread, 60_000)
})
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <div class="brand" @click="router.push('/')">商品推荐系统</div>

      <el-menu mode="horizontal" router :default-active="activeMenu" :ellipsis="false" class="nav">
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/products">商品</el-menu-item>
        <el-menu-item index="/cart">购物车</el-menu-item>
        <el-menu-item index="/orders">订单</el-menu-item>

        <!-- 收藏夹：登录后才显示，带数量角标 -->
        <el-menu-item v-if="userStore.isLoggedIn" index="/favorites">
          <span class="fav-item">
            <svg class="fav-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M12 20.7l-1.45-1.32C5.4 14.6 2 11.5 2 8.2 2 5.6 4.1 3.5 6.7 3.5c1.5 0 2.9.7 3.8 1.8.9-1.1 2.3-1.8 3.8-1.8 2.6 0 4.7 2.1 4.7 4.7 0 3.3-3.4 6.4-8.55 11.18L12 20.7z"
              />
            </svg>
            收藏夹
            <span v-if="favoriteStore.count > 0" class="fav-badge">{{ favoriteStore.count }}</span>
          </span>
        </el-menu-item>

        <!-- 后台：仅管理员可见（后端另有 ROLE_ADMIN 校验，前端隐藏只是减少误点） -->
        <el-menu-item v-if="userStore.isAdmin" index="/admin">后台</el-menu-item>
      </el-menu>

      <div class="user-area">
        <template v-if="userStore.isLoggedIn">
          <!-- 通知铃铛 -->
          <el-popover
            v-model:visible="notifyVisible"
            trigger="click"
            placement="bottom-end"
            :width="360"
            popper-class="notify-popper"
            @show="loadNotifications"
          >
            <template #reference>
              <button class="bell" type="button" title="消息通知">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M18 16v-5a6 6 0 1 0-12 0v5l-2 2h16l-2-2z" />
                  <path d="M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2z" />
                </svg>
                <span v-if="unreadCount > 0" class="bell-badge">
                  {{ unreadCount > 99 ? '99+' : unreadCount }}
                </span>
              </button>
            </template>

            <div class="notify-head">
              <span class="notify-title">消息通知</span>
              <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="handleReadAll">
                全部已读
              </el-button>
            </div>

            <div v-loading="notifyLoading" class="notify-list">
              <div
                v-for="item in notifications"
                :key="item.id"
                class="notify-item"
                :class="{ 'is-unread': item.isRead !== 1 }"
                @click="handleNotificationClick(item)"
              >
                <div class="notify-item-head">
                  <span class="notify-item-title">{{ item.title }}</span>
                  <span class="notify-time">{{ formatNotifyTime(item.createTime) }}</span>
                </div>
                <div class="notify-content">{{ item.content }}</div>
              </div>

              <div v-if="!notifyLoading && notifications.length === 0" class="notify-empty">
                暂无消息
              </div>
            </div>
          </el-popover>

          <!-- 头像 + 昵称整体可点，进入个人中心 -->
          <div class="user-chip" @click="router.push('/profile')">
            <el-avatar :size="36" :src="userStore.userInfo?.avatar || undefined" class="chip-avatar">
              {{ avatarText }}
            </el-avatar>
            <span class="muted">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
          </div>
          <el-tag v-if="userStore.isAdmin" size="small" type="danger">管理员</el-tag>
          <el-button link type="primary" @click="handleLogout">退出</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="router.push('/login')">登录</el-button>
          <el-button link @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </header>

  <main>
    <router-view />
  </main>
</template>

<style scoped>
.app-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  height: 60px;
}

.brand {
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  color: #e6531f;
}

.nav {
  flex: 1;
  border-bottom: none;
}

.fav-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.fav-icon {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linejoin: round;
}

.fav-badge {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px 3px 3px;
  border-radius: 20px;
  cursor: pointer;
  transition: background 0.15s;
}

.user-chip:hover {
  background: #f5f7fa;
}

.chip-avatar {
  background: #ff5000;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  flex: 0 0 auto;
  border: 1px solid #ffe0d0;
}

/* ---------- 通知铃铛 ---------- */
.bell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s;
}

.bell:hover {
  background: #f5f7fa;
}

.bell svg {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: #606266;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.bell-badge {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
}

.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid #f5f5f5;
}

.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.notify-list {
  max-height: 320px;
  overflow-y: auto;
  min-height: 60px;
}

.notify-item {
  padding: 10px 4px;
  border-bottom: 1px solid #fafafa;
  cursor: pointer;
  transition: background 0.15s;
}

.notify-item:hover {
  background: #fafafa;
}

.notify-item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.notify-item-title {
  font-size: 13px;
  color: #303133;
}

/* 未读用左侧圆点 + 加粗标题提示，不靠整块底色 */
.notify-item.is-unread .notify-item-title {
  font-weight: 600;
}

.notify-item.is-unread .notify-item-title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  border-radius: 50%;
  background: #f56c6c;
  vertical-align: 2px;
}

.notify-time {
  flex: 0 0 auto;
  font-size: 11px;
  color: #b1b3b8;
}

.notify-content {
  margin-top: 3px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.notify-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #c0c4cc;
}
</style>
