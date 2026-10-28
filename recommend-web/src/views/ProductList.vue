<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCategories, getProducts, reportBehavior } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const categories = ref([])
const products = ref([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(12)

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

onMounted(() => {
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
      <el-card
        v-for="item in products"
        :key="item.id"
        class="product-card"
        shadow="hover"
        @click="openProduct(item)"
      >
        <div class="cover">
          <el-image v-if="item.mainImage" :src="item.mainImage" fit="cover" class="cover-img">
            <template #error>
              <div class="image-placeholder">暂无图片</div>
            </template>
          </el-image>
          <div v-else class="image-placeholder">暂无图片</div>
        </div>
        <div class="name">{{ item.name }}</div>
        <div class="muted">{{ item.subtitle }}</div>
        <div class="row">
          <span class="price">￥{{ item.price }}</span>
          <span class="muted">销量 {{ item.sales }}</span>
        </div>
        <div class="row">
          <el-tag v-if="item.categoryName" size="small" type="info">{{ item.categoryName }}</el-tag>
          <span class="muted">库存 {{ item.stock }}</span>
        </div>
      </el-card>
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

.product-card {
  cursor: pointer;
}

.cover {
  height: 160px;
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 160px;
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

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
