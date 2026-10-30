<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  addCart,
  getProduct,
  getReviews,
  getSimilarRecommend,
  reportBehavior,
  submitReview
} from '../api'
import { useUserStore } from '../stores/user'
import { useFavoriteStore } from '../stores/favorite'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const favoriteStore = useFavoriteStore()

const product = ref(null)
const similar = ref([])
const reviews = ref([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewPageSize = 10
const quantity = ref(1)
const loading = ref(false)
const adding = ref(false)
const submittingReview = ref(false)

const reviewForm = reactive({
  rating: 5,
  content: ''
})

const rateTexts = ['很差', '较差', '一般', '满意', '非常满意']

const productId = computed(() => Number(route.params.id))

async function loadProduct() {
  loading.value = true
  try {
    product.value = await getProduct(productId.value)
  } catch {
    product.value = null
  }
  try {
    similar.value = (await getSimilarRecommend(productId.value, 8)) || []
  } catch {
    similar.value = []
  }
  reviewPage.value = 1
  await loadReviews()
  loading.value = false

  // 浏览行为上报需要登录态，失败不影响页面
  if (userStore.isLoggedIn) {
    reportBehavior({ productId: productId.value, behaviorType: 'VIEW' }).catch(() => {})
  }
}

async function loadReviews() {
  try {
    const data = await getReviews({
      productId: productId.value,
      pageNum: reviewPage.value,
      pageSize: reviewPageSize
    })
    reviews.value = data?.records || []
    reviewTotal.value = data?.total || 0
  } catch {
    reviews.value = []
    reviewTotal.value = 0
  }
}

function handleReviewPageChange(page) {
  reviewPage.value = page
  loadReviews()
}

async function handleSubmitReview() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再发表评价')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!reviewForm.rating) {
    ElMessage.warning('请先选择评分')
    return
  }
  submittingReview.value = true
  try {
    await submitReview({
      productId: productId.value,
      rating: reviewForm.rating,
      content: reviewForm.content.trim() || null
    })
    ElMessage.success('评价已提交')
    reviewForm.rating = 5
    reviewForm.content = ''
    reviewPage.value = 1
    await loadReviews()
  } catch {
    // 重复评价等错误由拦截器统一提示
  } finally {
    submittingReview.value = false
  }
}

async function handleAddCart() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再加入购物车')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  adding.value = true
  try {
    await addCart({ productId: productId.value, quantity: quantity.value })
    ElMessage.success('已加入购物车')
  } catch {
    // 错误提示由拦截器处理
  } finally {
    adding.value = false
  }
}

/**
 * 切换收藏。
 *
 * @param {object} [target] 传入商品对象时收藏该商品（相似推荐卡片用），
 *                          不传则收藏当前详情页商品
 */
async function toggleFavorite(target) {
  const item = target || product.value
  if (!item) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能收藏商品')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const nowFavorited = await favoriteStore.toggle(item.id)
    ElMessage.success(nowFavorited ? '已加入收藏夹' : '已取消收藏')
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

function openSimilar(item) {
  if (userStore.isLoggedIn) {
    reportBehavior({ productId: item.id, behaviorType: 'CLICK' }).catch(() => {})
  }
  router.push(`/products/${item.id}`)
}

watch(productId, () => loadProduct(), { immediate: true })
</script>

<template>
  <div v-loading="loading" class="page">
    <el-empty v-if="!loading && !product" description="商品不存在或已下架" />

    <template v-if="product">
      <el-card shadow="never" class="detail-card">
        <div class="detail">
          <div class="detail-cover">
            <el-image v-if="product.mainImage" :src="product.mainImage" fit="cover" class="detail-img">
              <template #error>
                <div class="image-placeholder">暂无图片</div>
              </template>
            </el-image>
            <div v-else class="image-placeholder detail-img">暂无图片</div>
          </div>
          <div class="detail-info">
            <h2 class="product-name">{{ product.name }}</h2>
            <div class="muted">{{ product.subtitle }}</div>
            <div class="price-line">
              <span class="price big">￥{{ product.price }}</span>
              <span v-if="product.originalPrice" class="original-price">￥{{ product.originalPrice }}</span>
            </div>
            <div class="meta">
              <el-tag size="small" type="info">{{ product.categoryName || '未分类' }}</el-tag>
              <span class="muted">销量 {{ product.sales }}</span>
              <span class="muted">库存 {{ product.stock }}</span>
              <el-tag v-if="product.status !== 1" size="small" type="danger">已下架</el-tag>
            </div>
            <p class="description">{{ product.description || '暂无商品描述' }}</p>
            <div class="buy-row">
              <el-input-number v-model="quantity" :min="1" :max="product.stock || 1" />
              <el-button type="primary" :loading="adding" @click="handleAddCart">加入购物车</el-button>
              <el-button
                :type="favoriteStore.isFavorited(product.id) ? 'danger' : 'default'"
                :plain="favoriteStore.isFavorited(product.id)"
                @click="toggleFavorite()"
              >
                <svg
                  class="fav-btn-icon"
                  :class="{ 'is-on': favoriteStore.isFavorited(product.id) }"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    d="M12 20.7l-1.45-1.32C5.4 14.6 2 11.5 2 8.2 2 5.6 4.1 3.5 6.7 3.5c1.5 0 2.9.7 3.8 1.8.9-1.1 2.3-1.8 3.8-1.8 2.6 0 4.7 2.1 4.7 4.7 0 3.3-3.4 6.4-8.55 11.18L12 20.7z"
                  />
                </svg>
                {{ favoriteStore.isFavorited(product.id) ? '已收藏' : '收藏' }}
              </el-button>
            </div>
          </div>
        </div>
      </el-card>

      <h3 class="section-title">相似推荐</h3>
      <div class="card-grid">
        <ProductCard
          v-for="item in similar"
          :key="item.id"
          :item="item"
          :favorited="favoriteStore.isFavorited(item.id)"
          @open="openSimilar"
          @toggle-favorite="toggleFavorite"
        />
      </div>
      <el-empty v-if="similar.length === 0" description="暂无相似商品推荐" />

      <h3 class="section-title">商品评价（{{ reviewTotal }}）</h3>
      <el-card shadow="never" class="review-form-card">
        <template #header>发表评价</template>
        <el-form label-width="72px" @submit.prevent>
          <el-form-item label="评分">
            <el-rate v-model="reviewForm.rating" show-text :texts="rateTexts" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input
              v-model="reviewForm.content"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="说说这件商品的使用体验（可不填）"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submittingReview" @click="handleSubmitReview">
              提交评价
            </el-button>
            <span class="muted">每人每件商品只能评价一次</span>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card v-for="review in reviews" :key="review.id" shadow="never" class="review-card">
        <div class="review-head">
          <el-rate :model-value="review.rating" disabled />
          <span class="muted">{{ review.username || `用户 ${review.userId}` }}</span>
          <span class="muted">{{ (review.createTime || '').replace('T', ' ') }}</span>
        </div>
        <div class="review-content">{{ review.content || '该用户未填写评价内容' }}</div>
      </el-card>
      <el-empty v-if="reviews.length === 0" description="暂无评价" />
      <div v-if="reviewTotal > reviewPageSize" class="pagination">
        <el-pagination
          layout="prev, pager, next"
          :total="reviewTotal"
          :current-page="reviewPage"
          :page-size="reviewPageSize"
          @current-change="handleReviewPageChange"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail {
  display: flex;
  gap: 24px;
}

.detail-cover {
  width: 320px;
  flex-shrink: 0;
}

.detail-img {
  width: 320px;
  height: 320px;
  min-height: 320px;
}

.detail-info {
  flex: 1;
}

.product-name {
  margin: 0 0 8px;
}

.price-line {
  margin: 16px 0;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.big {
  font-size: 26px;
}

.original-price {
  color: #909399;
  text-decoration: line-through;
}

.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.description {
  color: #606266;
  line-height: 1.7;
  white-space: pre-wrap;
}

.buy-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}

/* 收藏按钮里的小爱心 */
.fav-btn-icon {
  width: 15px;
  height: 15px;
  margin-right: 4px;
  vertical-align: -2px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linejoin: round;
}

.fav-btn-icon.is-on {
  fill: currentColor;
}

.product-card {
  cursor: pointer;
}

.similar-cover {
  height: 140px;
  overflow: hidden;
}

.similar-img {
  width: 100%;
  height: 140px;
  display: block;
}

.name {
  margin-top: 8px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.reason {
  margin-top: 8px;
  padding: 4px 8px;
  background: #f0f9eb;
  color: #67c23a;
  font-size: 12px;
  border-radius: 4px;
}

.review-card {
  margin-bottom: 12px;
}

.review-form-card {
  margin-bottom: 16px;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-content {
  margin-top: 8px;
  color: #606266;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
