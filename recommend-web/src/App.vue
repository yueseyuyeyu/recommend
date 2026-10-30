<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
    if (loggedIn) favoriteStore.load()
    else favoriteStore.reset()
  },
  { immediate: true }
)

function handleLogout() {
  userStore.logout()
  favoriteStore.reset()
  ElMessage.success('已退出登录')
  router.push('/')
}

onMounted(() => {
  if (userStore.isLoggedIn) favoriteStore.load()
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
          <!-- 头像 + 昵称整体可点，进入个人中心 -->
          <div class="user-chip" @click="router.push('/profile')">
            <el-avatar :size="26" :src="userStore.userInfo?.avatar || undefined" class="chip-avatar">
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
  gap: 6px;
  padding: 3px 8px 3px 3px;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.user-chip:hover {
  background: #f5f7fa;
}

.chip-avatar {
  background: #ff5000;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  flex: 0 0 auto;
}
</style>
