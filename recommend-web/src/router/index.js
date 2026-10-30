import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/products', name: 'products', component: () => import('../views/ProductList.vue') },
  { path: '/products/:id', name: 'product-detail', component: () => import('../views/ProductDetail.vue') },
  { path: '/cart', name: 'cart', component: () => import('../views/Cart.vue'), meta: { requiresAuth: true } },
  { path: '/favorites', name: 'favorites', component: () => import('../views/Favorites.vue'), meta: { requiresAuth: true } },
  { path: '/profile', name: 'profile', component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/orders', name: 'orders', component: () => import('../views/Orders.vue'), meta: { requiresAuth: true } },
  { path: '/pay/:orderNo', name: 'pay', component: () => import('../views/Payment.vue'), meta: { requiresAuth: true } },
  { path: '/admin', name: 'admin', component: () => import('../views/Admin.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    ElMessage.warning('仅管理员可访问后台')
    return { path: '/' }
  }
  return true
})

export default router
