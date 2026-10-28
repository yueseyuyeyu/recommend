<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProducts, getStatistics, updateProductStatus } from '../api'

const stats = ref({})
const products = ref([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const relistId = ref(null)
const actionLoading = ref(false)

const statCards = [
  { key: 'userCount', label: '用户数' },
  { key: 'productCount', label: '商品数' },
  { key: 'orderCount', label: '订单数' },
  { key: 'behaviorCount', label: '行为数' },
  { key: 'totalSales', label: '总销量' }
]

async function loadStats() {
  try {
    stats.value = (await getStatistics()) || {}
  } catch {
    stats.value = {}
  }
}

async function loadProducts() {
  loading.value = true
  try {
    // 后台暂无独立的商品列表接口，这里复用前台分页接口（只会返回上架商品）
    const data = await getProducts({ pageNum: pageNum.value, pageSize: pageSize.value })
    products.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function changeStatus(row, status) {
  const action = status === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定${action}商品「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  actionLoading.value = true
  try {
    await updateProductStatus(row.id, status)
    ElMessage.success(`已${action}`)
    await Promise.all([loadProducts(), loadStats()])
  } catch {
    // 错误提示由拦截器处理
  } finally {
    actionLoading.value = false
  }
}

async function handleRelist() {
  if (!relistId.value) {
    ElMessage.warning('请输入要上架的商品 ID')
    return
  }
  actionLoading.value = true
  try {
    await updateProductStatus(relistId.value, 1)
    ElMessage.success('已上架')
    relistId.value = null
    pageNum.value = 1
    await Promise.all([loadProducts(), loadStats()])
  } catch {
    // 错误提示由拦截器处理
  } finally {
    actionLoading.value = false
  }
}

function handlePageChange(page) {
  pageNum.value = page
  loadProducts()
}

onMounted(() => {
  loadStats()
  loadProducts()
})
</script>

<template>
  <div class="page">
    <h2 class="page-title">后台管理</h2>

    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.key" :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="muted">{{ card.label }}</div>
          <div class="stat-value">{{ stats[card.key] ?? '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="header-row">
          <span>商品上下架</span>
          <div class="relist">
            <el-input-number v-model="relistId" :min="1" :controls="false" placeholder="商品 ID" style="width: 120px" />
            <el-button :loading="actionLoading" @click="handleRelist">按 ID 上架</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="tip"
        title="商品列表复用前台接口，仅展示已上架商品；已下架商品可通过上方输入 ID 重新上架。"
      />

      <el-table v-loading="loading" :data="products" empty-text="暂无商品">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="商品名称" min-width="220" />
        <el-table-column prop="categoryName" label="类目" width="120" />
        <el-table-column label="价格" width="110">
          <template #default="{ row }">
            <span class="price">￥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="80" />
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架中' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              link
              type="danger"
              :loading="actionLoading"
              @click="changeStatus(row, 0)"
            >
              下架
            </el-button>
            <el-button
              v-else
              link
              type="primary"
              :loading="actionLoading"
              @click="changeStatus(row, 1)"
            >
              上架
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.relist {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tip {
  margin-bottom: 12px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
