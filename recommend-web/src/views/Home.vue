<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getCarousels,
  getCategories,
  getRecommendFeed,
  reportBehavior
} from '../api'
import { useUserStore } from '../stores/user'
import { useFavoriteStore } from '../stores/favorite'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()
const userStore = useUserStore()
const favoriteStore = useFavoriteStore()

/* ---------- 搜索 ---------- */
const keyword = ref('')

function doSearch() {
  const kw = keyword.value.trim()
  router.push({ path: '/products', query: kw ? { keyword: kw } : {} })
}

/* ---------- 分类菜单 ---------- */
const categories = ref([])
const activeCategoryId = ref(null)

async function loadCategories() {
  try {
    categories.value = (await getCategories()) || []
  } catch {
    categories.value = []
  }
}

function goCategory(category) {
  router.push({ path: '/products', query: { categoryId: category.id } })
}

/* ---------- 轮播图 ---------- */
const carousels = ref([])

async function loadCarousels() {
  try {
    carousels.value = (await getCarousels()) || []
  } catch {
    carousels.value = []
  }
}

/** 有图片用图片，没有就用数据库里存的 CSS 渐变 */
function bannerStyle(banner) {
  if (banner.imageUrl) {
    return { backgroundImage: `url(${banner.imageUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' }
  }
  return { background: banner.bgColor || 'linear-gradient(135deg,#ff6a00,#ee0979)' }
}

function goBanner(banner) {
  router.push(banner.linkUrl || '/products')
}

/* ---------- 推荐流（无限滚动） ---------- */
const PAGE_SIZE = 10
const feedList = ref([])
const pageNum = ref(0)
const total = ref(0)
const loadingFirst = ref(false)
const loadingMore = ref(false)
const noMore = ref(false)

const sentinel = ref(null)
let observer = null

const hasMore = computed(() => feedList.value.length < total.value)

async function loadNextPage() {
  if (loadingMore.value || loadingFirst.value || noMore.value) return

  const nextPage = pageNum.value + 1
  const isFirst = nextPage === 1
  if (isFirst) loadingFirst.value = true
  else loadingMore.value = true

  try {
    const page = await getRecommendFeed(nextPage, PAGE_SIZE)
    const records = page?.records || []

    /**
     * 去重：分页推荐理论上不会重复，但算法降级/补位切换时可能出现，
     * 前端再挡一层，避免无限滚动里冒出重复卡片。
     */
    const existing = new Set(feedList.value.map((item) => item.id))
    const fresh = records.filter((item) => !existing.has(item.id))

    feedList.value = [...feedList.value, ...fresh]
    pageNum.value = nextPage
    total.value = page?.total ?? 0

    // 没有新数据，或已翻到总数，停止继续加载
    if (fresh.length === 0 || feedList.value.length >= total.value) {
      noMore.value = true
    }
  } catch {
    noMore.value = true
  } finally {
    loadingFirst.value = false
    loadingMore.value = false
  }
}

/** 触底自动加载下一页 */
function setupObserver() {
  if (!sentinel.value || typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) loadNextPage()
    },
    { rootMargin: '200px' }
  )
  observer.observe(sentinel.value)
}

/* ---------- 收藏 ---------- */
/** 收藏状态统一由 favoriteStore 管理，这里只负责提示与异常兜底 */
async function toggleFavorite(item) {
  try {
    const nowFavorited = await favoriteStore.toggle(item.id)
    ElMessage.success(nowFavorited ? '已加入收藏夹' : '已取消收藏')
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

/* ---------- 打开商品 ---------- */
function openProduct(item) {
  if (userStore.isLoggedIn) {
    reportBehavior({ productId: item.id, behaviorType: 'CLICK' }).catch(() => {})
  }
  router.push(`/products/${item.id}`)
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadCarousels()])
  if (userStore.isLoggedIn) await favoriteStore.load()
  await loadNextPage()
  setupObserver()
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})
</script>

<template>
  <div class="home">
    <!-- ============ 搜索区 ============ -->
    <section class="search-hero">
      <div class="search-inner">
        <div class="search-bar">
          <el-input
            v-model="keyword"
            placeholder="搜索你想要的商品"
            size="large"
            clearable
            @keyup.enter="doSearch"
          >
            <template #append>
              <el-button type="primary" @click="doSearch">搜索</el-button>
            </template>
          </el-input>
        </div>
        <div class="hot-words">
          <span class="hot-label">热门：</span>
          <el-link v-for="word in ['手机', '耳机', '图书', '运动', '家居']" :key="word" :underline="false"
                   class="hot-word" @click="keyword = word; doSearch()">
            {{ word }}
          </el-link>
        </div>
      </div>
    </section>

    <!-- ============ 分类菜单 + 轮播图 ============ -->
    <section class="hero">
      <aside class="category-menu">
        <div class="menu-title">全部分类</div>
        <div
          v-for="category in categories"
          :key="category.id"
          class="cat-item"
          @mouseenter="activeCategoryId = category.id"
          @mouseleave="activeCategoryId = null"
          @click="goCategory(category)"
        >
          <span class="cat-name">{{ category.name }}</span>
          <span class="cat-arrow">›</span>

          <!-- 二级类目悬浮展开 -->
          <div
            v-if="activeCategoryId === category.id && category.children && category.children.length"
            class="submenu"
            @click.stop
          >
            <div
              v-for="child in category.children"
              :key="child.id"
              class="submenu-item"
              @click="goCategory(child)"
            >
              {{ child.name }}
            </div>
          </div>
        </div>
        <el-empty v-if="categories.length === 0" description="暂无分类" :image-size="60" />
      </aside>

      <div class="banner">
        <el-carousel v-if="carousels.length" height="380px" :interval="4500" arrow="hover">
          <el-carousel-item v-for="banner in carousels" :key="banner.id">
            <div class="banner-item" :style="bannerStyle(banner)" @click="goBanner(banner)">
              <h2 class="banner-title">{{ banner.title }}</h2>
              <p class="banner-subtitle">{{ banner.subtitle }}</p>
            </div>
          </el-carousel-item>
        </el-carousel>
        <div v-else class="banner-placeholder">暂无轮播图</div>
      </div>
    </section>

    <!-- ============ 推荐流（无限滚动） ============ -->
    <section class="feed">
      <div class="feed-header">
        <h3 class="feed-title">{{ userStore.isLoggedIn ? '为你推荐' : '热门推荐' }}</h3>
        <span class="feed-hint" v-if="!userStore.isLoggedIn">
          登录后可根据你的行为生成个性化推荐
          <el-button link type="primary" size="small" @click="router.push('/login')">去登录</el-button>
        </span>
        <span class="feed-hint" v-else>持续下滑，不断发现更多</span>
      </div>

      <div v-loading="loadingFirst" class="card-grid">
        <ProductCard
          v-for="item in feedList"
          :key="item.id"
          :item="item"
          :favorited="favoriteStore.isFavorited(item.id)"
          @open="openProduct"
          @toggle-favorite="toggleFavorite"
        />
      </div>

      <el-empty
        v-if="!loadingFirst && feedList.length === 0"
        description="暂无推荐商品，请先初始化种子数据"
      />

      <!-- 触底哨兵：进入视口即加载下一页 -->
      <div ref="sentinel" class="sentinel"></div>
      <div v-if="loadingMore" class="feed-footer">正在加载更多…</div>
      <div v-else-if="noMore && feedList.length > 0" class="feed-footer">已经到底啦</div>
    </section>
  </div>
</template>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px 48px;
}

/* ---------- 搜索区 ---------- */
.search-hero {
  padding: 24px 0 16px;
}

.search-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.search-bar {
  width: 620px;
  max-width: 100%;
}

.hot-words {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.hot-label {
  color: #909399;
}

.hot-word {
  font-size: 12px;
  color: #606266;
}

/* ---------- 分类菜单 + 轮播 ---------- */
.hero {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.category-menu {
  position: relative;
  width: 200px;
  flex: 0 0 200px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 8px 0 12px;
}

.menu-title {
  padding: 4px 16px 10px;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  border-bottom: 1px solid #f5f5f5;
  margin-bottom: 4px;
}

.cat-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 16px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.cat-item:hover {
  background: #fff3ec;
  color: #e6531f;
}

.cat-arrow {
  color: #c0c4cc;
  font-size: 14px;
}

/* 二级菜单浮层 */
.submenu {
  position: absolute;
  left: 100%;
  top: 0;
  z-index: 20;
  min-width: 180px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-left: none;
  border-radius: 0 8px 8px 0;
  box-shadow: 4px 6px 16px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.submenu-item {
  padding: 4px 10px;
  font-size: 13px;
  color: #606266;
  border-radius: 4px;
  cursor: pointer;
}

.submenu-item:hover {
  background: #fff3ec;
  color: #e6531f;
}

.banner {
  flex: 1;
  min-width: 0;
  border-radius: 8px;
  overflow: hidden;
}

.banner-item {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 56px;
  color: #fff;
  cursor: pointer;
}

.banner-title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 1px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
}

.banner-subtitle {
  margin: 12px 0 0;
  font-size: 15px;
  opacity: 0.92;
  line-height: 1.6;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.15);
}

.banner-placeholder {
  height: 380px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
  border-radius: 8px;
}

/* ---------- 推荐流 ---------- */
.feed {
  margin-top: 28px;
}

.feed-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}

.feed-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.feed-hint {
  font-size: 12px;
  color: #909399;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  min-height: 80px;
}

.sentinel {
  height: 1px;
}

.feed-footer {
  padding: 18px 0 4px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}

@media (max-width: 1100px) {
  .card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .category-menu {
    display: none;
  }

  .card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .banner-title {
    font-size: 22px;
  }
}
</style>
