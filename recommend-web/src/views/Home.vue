<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCarousels, getCategories, getRecommendFeed, reportBehavior } from '../api'
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

/**
 * 一级类目图标（线性图标，24×24，描边不填充）。
 * 按类目名称关键字匹配，匹配不到时用通用「盒子」图标兜底。
 */
const ICON_GROUPS = [
  { keys: ['手机', '数码', '通信'], paths: ['M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z', 'M10.5 18.5h3'] },
  { keys: ['电脑', '办公'], paths: ['M5 5h14v10H5z', 'M3 18h18'] },
  { keys: ['家电', '电器'], paths: ['M4 6h16v10H4z', 'M9 20h6'] },
  { keys: ['服饰', '女装', '男装', '鞋包'], paths: ['M9 3l3 2 3-2 4 2-1.5 3.5V20H6.5V8.5L5 5z'] },
  { keys: ['美妆', '个护', '化妆'], paths: ['M12 3c3.2 4 6 7 6 10a6 6 0 0 1-12 0c0-3 2.8-6 6-10z'] },
  { keys: ['食品', '生鲜', '零食'], paths: ['M7 8h10l-1 11H8z', 'M9 5h6'] },
  { keys: ['运动', '户外'], paths: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M3.6 9h16.8', 'M12 3.2c-3 4.4-3 13.2 0 17.6'] },
  { keys: ['图书', '文娱', '书'], paths: ['M5 4h6v16H5z', 'M13 4h6v16h-6z'] },
  { keys: ['家居', '日用', '家装'], paths: ['M5 11V8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v3', 'M3.5 11h17v6H3.5z', 'M7 17v2', 'M17 17v2'] },
  { keys: ['母婴', '玩具'], paths: ['M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9z', 'M9.8 15c.6.6 1.4.9 2.2.9s1.6-.3 2.2-.9'] }
]
const FALLBACK_ICON = ['M4 7h16v13H4z', 'M4 11h16']

function iconPaths(name) {
  const group = ICON_GROUPS.find((item) => item.keys.some((k) => (name || '').includes(k)))
  return group ? group.paths : FALLBACK_ICON
}

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

function bannerStyle(banner) {
  if (banner.imageUrl) {
    return {
      backgroundImage: `url(${banner.imageUrl})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center'
    }
  }
  return { background: banner.bgColor || 'linear-gradient(135deg,#ff6a00,#ee0979)' }
}

function goBanner(banner) {
  router.push(banner.linkUrl || '/products')
}

/* ---------- 推荐流（游标分页 + 无限滚动） ---------- */
const PAGE_SIZE = 10
const feedList = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const totalCount = ref(0)
const loadingFirst = ref(false)
const loadingMore = ref(false)

const sentinel = ref(null)
let observer = null

function resetFeed() {
  feedList.value = []
  cursor.value = null
  hasMore.value = true
  totalCount.value = 0
}

/**
 * 加载下一页。
 *
 * 游标分页：首次请求不带 cursor，服务端返回 nextCursor；
 * 之后原样回传该游标取下一段。单次翻页开销与已翻多少页无关
 * （服务端只算一次排行池并缓存，翻页退化为内存切片）。
 */
async function loadNextPage() {
  if (loadingMore.value || loadingFirst.value || !hasMore.value) return

  const isFirstPage = cursor.value === null && feedList.value.length === 0
  if (isFirstPage) loadingFirst.value = true
  else loadingMore.value = true

  try {
    const page = await getRecommendFeed(cursor.value, PAGE_SIZE)
    const records = page?.records || []

    // 兜底去重：算法池刷新等极端情况下可能重复，前端再挡一层
    const existing = new Set(feedList.value.map((item) => item.id))
    const fresh = records.filter((item) => !existing.has(item.id))

    feedList.value = [...feedList.value, ...fresh]
    cursor.value = page?.nextCursor ?? null
    totalCount.value = page?.total ?? totalCount.value
    hasMore.value = Boolean(page?.hasMore) && cursor.value !== null

    // 本页全是重复数据时停止，避免无限触发
    if (fresh.length === 0) {
      hasMore.value = false
    }
  } catch {
    hasMore.value = false
  } finally {
    loadingFirst.value = false
    loadingMore.value = false
  }
}

function setupObserver() {
  if (!sentinel.value || typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) loadNextPage()
    },
    { rootMargin: '240px' }
  )
  observer.observe(sentinel.value)
}

/* ---------- 收藏 ---------- */
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

const loadedCount = computed(() => feedList.value.length)

// 登录状态变化后推荐口径不同（游客走热门），重置列表按新身份重新拉取
watch(
  () => userStore.isLoggedIn,
  async () => {
    resetFeed()
    if (userStore.isLoggedIn) await favoriteStore.load()
    await loadNextPage()
  }
)

onMounted(async () => {
  await Promise.all([loadCategories(), loadCarousels()])
  if (userStore.isLoggedIn) favoriteStore.load()
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
          <el-link
            v-for="word in ['手机', '耳机', '图书', '运动', '家居']"
            :key="word"
            :underline="false"
            class="hot-word"
            @click="keyword = word; doSearch()"
          >
            {{ word }}
          </el-link>
        </div>
      </div>
    </section>

    <!-- ============ 分类菜单 + 轮播图 ============ -->
    <section class="hero">
      <aside class="category-menu">
        <div class="menu-head">全部分类</div>
        <ul class="cat-list">
          <li
            v-for="category in categories"
            :key="category.id"
            class="cat-row"
            @mouseenter="activeCategoryId = category.id"
            @mouseleave="activeCategoryId = null"
            @click="goCategory(category)"
          >
            <svg class="cat-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path v-for="(d, index) in iconPaths(category.name)" :key="index" :d="d" />
            </svg>

            <span class="cat-text">
              <span class="cat-name">{{ category.name }}</span>
              <span v-for="child in category.children || []" :key="child.id" class="cat-sub">
                / {{ child.name }}
              </span>
            </span>

            <span class="cat-arrow">›</span>

            <!-- 悬停展开的二级面板 -->
            <div
              v-if="activeCategoryId === category.id && (category.children || []).length"
              class="cat-flyout"
              @click.stop
            >
              <div class="flyout-head">{{ category.name }}</div>
              <div class="flyout-links">
                <span
                  v-for="child in category.children"
                  :key="child.id"
                  class="flyout-link"
                  @click="goCategory(child)"
                >
                  {{ child.name }}
                </span>
              </div>
              <div class="flyout-foot" @click="goCategory(category)">查看全部 ›</div>
            </div>
          </li>
        </ul>
        <div v-if="categories.length === 0" class="menu-empty">暂无分类</div>
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

    <!-- ============ 推荐流 ============ -->
    <section class="feed">
      <div class="feed-header">
        <h3 class="feed-title">{{ userStore.isLoggedIn ? '为你推荐' : '热门推荐' }}</h3>
        <span v-if="!userStore.isLoggedIn" class="feed-hint">
          登录后可根据你的行为生成个性化推荐
          <el-button link type="primary" size="small" @click="router.push('/login')">去登录</el-button>
        </span>
        <span v-else class="feed-hint">持续下滑，不断发现更多</span>
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

      <div ref="sentinel" class="sentinel"></div>
      <div v-if="loadingMore" class="feed-footer">正在加载更多…</div>
      <div v-else-if="!hasMore && feedList.length > 0" class="feed-footer">
        已经到底啦 · 共 {{ loadedCount }} 件
      </div>
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

/* ---------- 分类菜单（淘宝首页风格） ---------- */
.hero {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.category-menu {
  position: relative;
  width: 232px;
  flex: 0 0 232px;
  height: 380px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  /* 二级面板要溢出到右侧，不能裁切 */
  overflow: visible;
}

.menu-head {
  flex: 0 0 34px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #f5f5f5;
}

.cat-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  min-height: 0;
}

.cat-row {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  min-height: 0;
  cursor: pointer;
  transition: background 0.15s;
}

/* 淘宝式虚线分隔，第一行之外都画上边线 */
.cat-row + .cat-row {
  border-top: 1px dotted #ebeef5;
}

.cat-row:hover {
  background: #fff7f2;
}

.cat-icon {
  flex: 0 0 15px;
  width: 15px;
  height: 15px;
  fill: none;
  stroke: #909399;
  stroke-width: 1.5;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: stroke 0.15s;
}

.cat-row:hover .cat-icon {
  stroke: #ff5000;
}

.cat-text {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  line-height: 1.2;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s;
}

.cat-row:hover .cat-text {
  color: #ff5000;
}

.cat-name {
  font-weight: 500;
}

.cat-sub {
  color: #9b9b9b;
  font-weight: 400;
}

.cat-row:hover .cat-sub {
  color: #ff8a4d;
}

.cat-arrow {
  flex: 0 0 auto;
  color: #d0d0d0;
  font-size: 13px;
  line-height: 1;
}

/* 二级悬浮面板 */
.cat-flyout {
  position: absolute;
  left: 100%;
  top: -1px;
  z-index: 30;
  min-width: 230px;
  min-height: 100%;
  padding: 12px 14px 14px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-left: none;
  border-radius: 0 8px 8px 0;
  box-shadow: 6px 8px 20px rgba(0, 0, 0, 0.08);
}

.flyout-head {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.flyout-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.flyout-link {
  font-size: 12.5px;
  color: #666;
  cursor: pointer;
  transition: color 0.15s;
}

.flyout-link:hover {
  color: #ff5000;
}

.flyout-foot {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px dashed #f0f0f0;
  font-size: 12px;
  color: #ff5000;
  cursor: pointer;
}

.menu-empty {
  padding: 20px 14px;
  font-size: 12px;
  color: #c0c4cc;
}

/* ---------- 轮播 ---------- */
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
