<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCategories, getProducts, reportBehavior } from '../api'
import { useUserStore } from '../stores/user'
import { useFavoriteStore } from '../stores/favorite'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const favoriteStore = useFavoriteStore()

const categories = ref([])
const products = ref([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
// 商品栅格宽屏为 5 列，每页取 5 的倍数（3 行）才不会在末行留空格；
// 原先的 12 是按早期 4 列布局定的，栅格改 5 列后没同步
const pageSize = ref(15)

const query = reactive({
  keyword: '',
  categoryId: null,
  minPrice: null,
  maxPrice: null,
  sort: 'default'
})

const sortOptions = [
  { value: 'default', label: '默认排序' },
  { value: 'sales', label: '销量优先' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' }
]

// 两级类目树拍平成下拉选项
const categoryOptions = computed(() => {
  const options = []
  for (const top of categories.value) {
    options.push({ id: top.id, name: top.name })
    for (const child of top.children || []) {
      options.push({ id: child.id, name: `　${child.name}` })
    }
  }
  return options
})

/**
 * 从 URL 查询参数初始化筛选条件。
 *
 * <p>首页搜索框、分类菜单、轮播图链接都是通过 query 跳转到本页的，
 * 不读 query 就会出现「点了搜索但列表没变」的问题。
 */
function syncQueryFromRoute() {
  query.keyword = route.query.keyword || ''
  query.categoryId = route.query.categoryId ? Number(route.query.categoryId) : null
  query.sort = route.query.sort || 'default'
  pageNum.value = 1
}

async function loadCategories() {
  try {
    categories.value = (await getCategories()) || []
  } catch {
    categories.value = []
  }
}

async function loadProducts() {
  loading.value = true
  try {
    const data = await getProducts({
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined,
      minPrice: query.minPrice ?? undefined,
      maxPrice: query.maxPrice ?? undefined,
      sort: query.sort,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    products.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadProducts()
}

function handleReset() {
  query.keyword = ''
  query.categoryId = null
  query.minPrice = null
  query.maxPrice = null
  query.sort = 'default'
  pageNum.value = 1
  loadProducts()
}

function handlePageChange(page) {
  pageNum.value = page
  loadProducts()
}

function openProduct(item) {
  if (userStore.isLoggedIn) {
    reportBehavior({ productId: item.id, behaviorType: 'CLICK' }).catch(() => {})
  }
  router.push(`/products/${item.id}`)
}

async function toggleFavorite(item) {
  try {
    const nowFavorited = await favoriteStore.toggle(item.id)
    ElMessage.success(nowFavorited ? '已加入收藏夹' : '已取消收藏')
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

// 同一页面内从首页再跳一次（query 变化但组件复用）时也能刷新
watch(
  () => route.query,
  () => {
    if (route.path === '/products') {
      syncQueryFromRoute()
      loadProducts()
    }
  }
)

onMounted(async () => {
  syncQueryFromRoute()
  if (userStore.isLoggedIn) favoriteStore.load()
  loadCategories()
  loadProducts()
})
</script>

<template>
  <div class="page">
    <h2 class="page-title">商品列表</h2>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="商品名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类目">
          <el-select v-model="query.categoryId" placeholder="全部类目" clearable style="width: 160px">
            <el-option
              v-for="option in categoryOptions"
              :key="option.id"
              :label="option.name"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="query.minPrice" :min="0" :controls="false" placeholder="最低价" style="width: 100px" />
          <span class="price-sep">-</span>
          <el-input-number v-model="query.maxPrice" :min="0" :controls="false" placeholder="最高价" style="width: 100px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="query.sort" style="width: 150px">
            <el-option v-for="item in sortOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading" class="card-grid">
      <ProductCard
        v-for="item in products"
        :key="item.id"
        :item="item"
        :favorited="favoriteStore.isFavorited(item.id)"
        :show-reason="false"
        @open="openProduct"
        @toggle-favorite="toggleFavorite"
      />
    </div>

    <el-empty v-if="!loading && products.length === 0" description="没有找到商品" />

    <div class="pagination">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

.price-sep {
  margin: 0 6px;
  color: #909399;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  min-height: 80px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 1100px) {
  .card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
