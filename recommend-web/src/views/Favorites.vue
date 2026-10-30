<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFavorites, removeFavorite, reportBehavior } from '../api'
import { useFavoriteStore } from '../stores/favorite'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()
const favoriteStore = useFavoriteStore()

const PAGE_SIZE = 10
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)

const pages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

/** 收藏记录里的商品 ID 叫 productId，统一映射成卡片需要的 id */
const cards = computed(() =>
  list.value.map((item) => ({
    id: item.productId,
    name: item.name,
    subtitle: item.subtitle,
    mainImage: item.mainImage,
    price: item.price,
    sales: item.sales,
    reason: item.productStatus === 0 ? '该商品已下架' : '',
    offShelf: item.productStatus === 0
  }))
)

async function load() {
  loading.value = true
  try {
    const page = await getFavorites({ pageNum: pageNum.value, pageSize: PAGE_SIZE })
    list.value = page?.records || []
    total.value = page?.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(page) {
  pageNum.value = page
  load()
}

function openProduct(item) {
  if (item.offShelf) {
    ElMessage.warning('该商品已下架')
    return
  }
  reportBehavior({ productId: item.id, behaviorType: 'CLICK' }).catch(() => {})
  router.push(`/products/${item.id}`)
}

/** 收藏夹里点爱心 = 取消收藏，成功后从列表移除 */
async function handleUnfavorite(item) {
  try {
    await removeFavorite(item.id)
    ElMessage.success('已取消收藏')
    // 同步全局收藏状态，让导航栏角标立刻减一
    await favoriteStore.load()
    // 移除后当前页可能空了，回退一页
    if (list.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    load()
  } catch {
    load()
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="header">
      <h2 class="title">我的收藏</h2>
      <span class="count">共 {{ total }} 件</span>
    </div>

    <div v-loading="loading" class="card-grid">
      <ProductCard
        v-for="item in cards"
        :key="item.id"
        :item="item"
        :favorited="true"
        :show-reason="false"
        @open="openProduct"
        @toggle-favorite="handleUnfavorite"
      />
    </div>

    <el-empty
      v-if="!loading && list.length === 0"
      description="还没有收藏任何商品，去首页逛逛吧"
    >
      <el-button type="primary" @click="router.push('/')">去首页</el-button>
    </el-empty>

    <div v-if="total > PAGE_SIZE" class="pager">
      <el-pagination
        layout="prev, pager, next"
        :current-page="pageNum"
        :page-size="PAGE_SIZE"
        :total="total"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.count {
  font-size: 13px;
  color: #909399;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  min-height: 80px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 24px;
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
