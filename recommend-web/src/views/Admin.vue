<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCarousel,
  createProduct,
  deleteCarousel,
  deleteCategory,
  deleteProduct,
  evaluateRecommend,
  getAdminCarousels,
  getAdminOrders,
  getAdminProducts,
  getAdminUsers,
  getCategories,
  getStatistics,
  saveCategory,
  updateCarousel,
  updateCarouselStatus,
  updateOrderStatus,
  updateProduct,
  updateProductStatus,
  updateUserStatus
} from '../api'

const ORDER_STATUS = [
  { value: 0, label: '待支付', tag: 'warning' },
  { value: 1, label: '已支付', tag: 'success' },
  { value: 2, label: '已发货', tag: 'primary' },
  { value: 3, label: '已完成', tag: 'info' },
  { value: 4, label: '已取消', tag: 'info' }
]

const activeTab = ref('overview')

/* ================= 概览与评测 ================= */
const stats = ref({})
const evaluation = ref(null)
const evaluating = ref(false)

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

async function handleEvaluate() {
  evaluating.value = true
  try {
    evaluation.value = await evaluateRecommend(10)
  } catch {
    evaluation.value = null
  } finally {
    evaluating.value = false
  }
}

function percent(value) {
  return typeof value === 'number' ? `${(value * 100).toFixed(2)}%` : '-'
}

/* ================= 商品管理 ================= */
const products = ref([])
const productTotal = ref(0)
const productPage = ref(1)
const productPageSize = ref(10)
const productLoading = ref(false)
const productSaving = ref(false)
const productDialog = ref(false)
const productFilter = reactive({ keyword: '', status: '' })

const productForm = reactive({
  id: null,
  name: '',
  categoryId: null,
  subtitle: '',
  mainImage: '',
  price: 0,
  originalPrice: 0,
  stock: 0,
  status: 1,
  description: ''
})

async function loadProducts() {
  productLoading.value = true
  try {
    const data = await getAdminProducts({
      keyword: productFilter.keyword.trim() || undefined,
      status: productFilter.status === '' ? undefined : productFilter.status,
      pageNum: productPage.value,
      pageSize: productPageSize.value
    })
    products.value = data?.records || []
    productTotal.value = data?.total || 0
  } catch {
    products.value = []
    productTotal.value = 0
  } finally {
    productLoading.value = false
  }
}

function handleProductSearch() {
  productPage.value = 1
  loadProducts()
}

function handleProductPageChange(page) {
  productPage.value = page
  loadProducts()
}

function openCreateProduct() {
  Object.assign(productForm, {
    id: null,
    name: '',
    categoryId: null,
    subtitle: '',
    mainImage: '',
    price: 0,
    originalPrice: 0,
    stock: 0,
    status: 1,
    description: ''
  })
  productDialog.value = true
}

function openEditProduct(row) {
  Object.assign(productForm, {
    id: row.id,
    name: row.name,
    categoryId: row.categoryId,
    subtitle: row.subtitle || '',
    mainImage: row.mainImage || '',
    price: Number(row.price) || 0,
    originalPrice: Number(row.originalPrice) || 0,
    stock: row.stock ?? 0,
    status: row.status ?? 1,
    description: row.description || ''
  })
  productDialog.value = true
}

async function handleSaveProduct() {
  if (!productForm.name.trim()) {
    ElMessage.warning('请输入商品名称')
    return
  }
  if (!productForm.categoryId) {
    ElMessage.warning('请选择类目')
    return
  }
  const payload = {
    categoryId: productForm.categoryId,
    name: productForm.name.trim(),
    subtitle: productForm.subtitle.trim() || null,
    mainImage: productForm.mainImage.trim() || null,
    price: productForm.price,
    originalPrice: productForm.originalPrice || null,
    stock: productForm.stock,
    status: productForm.status,
    description: productForm.description.trim() || null
  }
  productSaving.value = true
  try {
    if (productForm.id) {
      await updateProduct(productForm.id, payload)
      ElMessage.success('商品已更新')
    } else {
      await createProduct(payload)
      ElMessage.success('商品已新增')
    }
    productDialog.value = false
    await Promise.all([loadProducts(), loadStats()])
  } catch {
    // 错误提示由拦截器处理
  } finally {
    productSaving.value = false
  }
}

async function handleToggleProduct(row) {
  const target = row.status === 1 ? 0 : 1
  const action = target === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定${action}商品「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateProductStatus(row.id, target)
    ElMessage.success(`已${action}`)
    await loadProducts()
  } catch {
    // 错误提示由拦截器处理
  }
}

async function handleDeleteProduct(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除商品「${row.name}」吗？删除为逻辑删除，历史订单不受影响。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteProduct(row.id)
    ElMessage.success('商品已删除')
    await Promise.all([loadProducts(), loadStats()])
  } catch {
    // 错误提示由拦截器处理
  }
}

/* ================= 类目管理 ================= */
const MAX_CATEGORY_LEVEL = 3

const categories = ref([])
const categoryOptions = ref([])
/** id -> 层级，用于表格展示与「还能不能再往下加」的判断 */
const categoryLevels = ref({})
const categoryDialog = ref(false)
const categorySaving = ref(false)
const categoryForm = reactive({ name: '', parentId: 0, sortOrder: 0 })

/**
 * 递归拍平类目树。
 *
 * 用全角空格做缩进，是为了在原生 el-select 下拉里也能看出层级；
 * 同时把每个节点的层级记下来，表格和「新增子类目」按钮都要用。
 */
function flattenTree(nodes, depth = 1, options = [], levels = {}) {
  for (const node of nodes) {
    const prefix = depth === 1 ? '' : `${'　'.repeat(depth - 1)}└ `
    options.push({ value: node.id, label: `${prefix}${node.name}`, depth })
    levels[node.id] = depth
    if (node.children && node.children.length) {
      flattenTree(node.children, depth + 1, options, levels)
    }
  }
  return { options, levels }
}

async function loadCategories() {
  try {
    const tree = (await getCategories()) || []
    categories.value = tree
    const { options, levels } = flattenTree(tree)
    categoryOptions.value = options
    categoryLevels.value = levels
  } catch {
    categories.value = []
    categoryOptions.value = []
    categoryLevels.value = {}
  }
}

function levelOf(row) {
  return categoryLevels.value[row.id] || 1
}

function levelName(level) {
  return ['一级', '二级', '三级', '四级'][level - 1] || `${level} 级`
}

/** 父级选定后，将要创建的层级 */
const pendingLevelName = computed(() => {
  if (!categoryForm.parentId) return '一级'
  return levelName((categoryLevels.value[categoryForm.parentId] || 1) + 1)
})

function openCreateCategory(parentId = 0) {
  Object.assign(categoryForm, { name: '', parentId, sortOrder: 0 })
  categoryDialog.value = true
}

async function handleSaveCategory() {
  if (!categoryForm.name.trim()) {
    ElMessage.warning('请输入类目名称')
    return
  }
  categorySaving.value = true
  try {
    await saveCategory({
      name: categoryForm.name.trim(),
      parentId: categoryForm.parentId || 0,
      sortOrder: categoryForm.sortOrder || 0
    })
    ElMessage.success('类目已保存')
    categoryDialog.value = false
    await loadCategories()
  } catch {
    // 错误提示由拦截器处理
  } finally {
    categorySaving.value = false
  }
}

async function handleDeleteCategory(row) {
  try {
    await ElMessageBox.confirm(`确定删除类目「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteCategory(row.id)
    ElMessage.success('类目已删除')
    await loadCategories()
  } catch {
    // 错误提示由拦截器处理
  }
}

/* ================= 用户管理 ================= */
const users = ref([])
const userTotal = ref(0)
const userPage = ref(1)
const userPageSize = ref(10)
const userLoading = ref(false)
const userKeyword = ref('')

async function loadUsers() {
  userLoading.value = true
  try {
    const data = await getAdminUsers({
      keyword: userKeyword.value.trim() || undefined,
      pageNum: userPage.value,
      pageSize: userPageSize.value
    })
    users.value = data?.records || []
    userTotal.value = data?.total || 0
  } catch {
    users.value = []
    userTotal.value = 0
  } finally {
    userLoading.value = false
  }
}

function handleUserSearch() {
  userPage.value = 1
  loadUsers()
}

function handleUserPageChange(page) {
  userPage.value = page
  loadUsers()
}

async function handleToggleUser(row) {
  const target = row.status === 1 ? 0 : 1
  const action = target === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.username}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateUserStatus(row.id, target)
    ElMessage.success(`已${action}`)
    await loadUsers()
  } catch {
    // 错误提示由拦截器处理
  }
}

/* ================= 订单管理 ================= */
const adminOrders = ref([])
const orderTotal = ref(0)
const orderPage = ref(1)
const orderPageSize = ref(10)
const orderLoading = ref(false)
const orderStatus = ref('')

async function loadAdminOrders() {
  orderLoading.value = true
  try {
    const data = await getAdminOrders({
      status: orderStatus.value === '' ? undefined : orderStatus.value,
      pageNum: orderPage.value,
      pageSize: orderPageSize.value
    })
    adminOrders.value = data?.records || []
    orderTotal.value = data?.total || 0
  } catch {
    adminOrders.value = []
    orderTotal.value = 0
  } finally {
    orderLoading.value = false
  }
}

/**
 * 订单状态流转。
 *
 * 不做「下拉任选状态」了：那样可以把待支付直接改成已完成，
 * 或者把已发货倒回待支付。现在只暴露当前状态允许的下一步按钮，
 * 服务端也会按状态机再校验一次。
 */
async function handleOrderAction(row, targetStatus, actionName) {
  try {
    await ElMessageBox.confirm(
      `确定将订单 ${row.orderNo} 标记为「${actionName}」吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateOrderStatus(row.orderNo, targetStatus)
    ElMessage.success(`订单已${actionName}`)
    await Promise.all([loadAdminOrders(), loadStats()])
  } catch {
    // 失败时重新拉取，保证界面与真实状态一致
    await loadAdminOrders()
  }
}

function statusTag(status) {
  return ORDER_STATUS.find((item) => item.value === status)?.tag || 'info'
}

function handleOrderSearch() {
  orderPage.value = 1
  loadAdminOrders()
}

function handleOrderPageChange(page) {
  orderPage.value = page
  loadAdminOrders()
}

function itemsSummary(row) {
  return (row.items || []).map((item) => `${item.productName}×${item.quantity}`).join('、')
}

function formatTime(time) {
  return (time || '').replace('T', ' ')
}

/* ================= 轮播图管理 ================= */
const carousels = ref([])
const carouselLoading = ref(false)
const carouselSaving = ref(false)
const carouselDialog = ref(false)

const carouselForm = reactive({
  id: null,
  title: '',
  subtitle: '',
  imageUrl: '',
  bgColor: 'linear-gradient(135deg,#ff6a00,#ee0979)',
  linkUrl: '/products',
  sortOrder: 0,
  status: 1
})

/** 渐变预设，避免管理员手写 CSS 出错 */
const GRADIENT_PRESETS = [
  { label: '橙红', value: 'linear-gradient(135deg,#ff6a00,#ee0979)' },
  { label: '青绿', value: 'linear-gradient(135deg,#1d976c,#93f9b9)' },
  { label: '天蓝', value: 'linear-gradient(135deg,#2193b0,#6dd5ed)' },
  { label: '紫罗兰', value: 'linear-gradient(135deg,#8e2de2,#4a00e0)' },
  { label: '深灰', value: 'linear-gradient(135deg,#3a3d40,#181818)' }
]

async function loadCarousels() {
  carouselLoading.value = true
  try {
    carousels.value = (await getAdminCarousels()) || []
  } catch {
    carousels.value = []
  } finally {
    carouselLoading.value = false
  }
}

function resetCarouselForm() {
  carouselForm.id = null
  carouselForm.title = ''
  carouselForm.subtitle = ''
  carouselForm.imageUrl = ''
  carouselForm.bgColor = GRADIENT_PRESETS[0].value
  carouselForm.linkUrl = '/products'
  carouselForm.sortOrder = 0
  carouselForm.status = 1
}

function openCarouselDialog(row) {
  resetCarouselForm()
  if (row) {
    Object.assign(carouselForm, {
      id: row.id,
      title: row.title,
      subtitle: row.subtitle || '',
      imageUrl: row.imageUrl || '',
      bgColor: row.bgColor || GRADIENT_PRESETS[0].value,
      linkUrl: row.linkUrl || '',
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1
    })
  }
  carouselDialog.value = true
}

async function submitCarousel() {
  if (!carouselForm.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  carouselSaving.value = true
  try {
    const payload = {
      title: carouselForm.title.trim(),
      subtitle: carouselForm.subtitle.trim() || null,
      imageUrl: carouselForm.imageUrl.trim() || null,
      bgColor: carouselForm.bgColor,
      linkUrl: carouselForm.linkUrl.trim() || null,
      sortOrder: carouselForm.sortOrder ?? 0,
      status: carouselForm.status
    }
    if (carouselForm.id) {
      await updateCarousel(carouselForm.id, payload)
      ElMessage.success('已更新')
    } else {
      await createCarousel(payload)
      ElMessage.success('已新增')
    }
    carouselDialog.value = false
    loadCarousels()
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    carouselSaving.value = false
  }
}

async function handleDeleteCarousel(row) {
  try {
    await ElMessageBox.confirm(`确定删除轮播图「${row.title}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteCarousel(row.id)
    ElMessage.success('已删除')
    loadCarousels()
  } catch {
    // 拦截器已提示
  }
}

async function handleCarouselStatusChange(row) {
  try {
    await updateCarouselStatus(row.id, row.status)
    ElMessage.success(row.status === 1 ? '已启用' : '已停用')
  } catch {
    // 失败回滚显示
    row.status = row.status === 1 ? 0 : 1
  }
}

/* ================= 生命周期 ================= */
watch(activeTab, (tab) => {
  if (tab === 'products') {
    loadProducts()
    loadCategories()
  } else if (tab === 'categories') {
    loadCategories()
  } else if (tab === 'users') {
    loadUsers()
  } else if (tab === 'orders') {
    loadAdminOrders()
  } else if (tab === 'carousels') {
    loadCarousels()
  }
})

onMounted(() => {
  loadStats()
  loadCategories()
  loadProducts()
})
</script>

<template>
  <div class="page">
    <h2 class="page-title">后台管理</h2>

    <el-tabs v-model="activeTab">
      <!-- ============ 概览 ============ -->
      <el-tab-pane label="概览" name="overview">
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
              <span>推荐效果离线评测</span>
              <el-button type="primary" :loading="evaluating" @click="handleEvaluate">
                运行评测
              </el-button>
            </div>
          </template>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            class="tip"
            title="采用留出法：80% 行为做训练，20% 做测试，TopN=10，实时计算 Precision / Recall / F1 / 覆盖率。"
          />
          <el-descriptions v-if="evaluation" :column="3" border>
            <el-descriptions-item label="Precision">{{ percent(evaluation.precision) }}</el-descriptions-item>
            <el-descriptions-item label="Recall">{{ percent(evaluation.recall) }}</el-descriptions-item>
            <el-descriptions-item label="F1">{{ percent(evaluation.f1) }}</el-descriptions-item>
            <el-descriptions-item label="覆盖率">
              {{ percent(evaluation.coverage) }}
              <span class="muted">
                （{{ evaluation.recommendedItemCount }}/{{ evaluation.totalItemCount }}）
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="参评用户数">{{ evaluation.evaluatedUserCount }}</el-descriptions-item>
            <el-descriptions-item label="平均流行度">
              {{ Number(evaluation.avgPopularity || 0).toFixed(3) }}
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="点击「运行评测」查看推荐算法指标" />
        </el-card>
      </el-tab-pane>

      <!-- ============ 商品管理 ============ -->
      <el-tab-pane label="商品管理" name="products">
        <el-card shadow="never">
          <template #header>
            <div class="header-row">
              <div class="filters">
                <el-input
                  v-model="productFilter.keyword"
                  placeholder="商品名称"
                  clearable
                  style="width: 180px"
                  @keyup.enter="handleProductSearch"
                />
                <el-select v-model="productFilter.status" style="width: 130px">
                  <el-option label="全部状态" value="" />
                  <el-option label="上架中" :value="1" />
                  <el-option label="已下架" :value="0" />
                </el-select>
                <el-button type="primary" @click="handleProductSearch">查询</el-button>
              </div>
              <el-button type="success" @click="openCreateProduct">新增商品</el-button>
            </div>
          </template>

          <el-table v-loading="productLoading" :data="products" empty-text="暂无商品">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="商品名称" min-width="200" />
            <el-table-column prop="categoryName" label="类目" width="150" />
            <el-table-column label="价格" width="110">
              <template #default="{ row }">
                <span class="price">￥{{ row.price }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="80" />
            <el-table-column prop="sales" label="销量" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">
                  {{ row.status === 1 ? '上架中' : '已下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditProduct(row)">编辑</el-button>
                <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="handleToggleProduct(row)">
                  {{ row.status === 1 ? '下架' : '上架' }}
                </el-button>
                <el-button link type="danger" @click="handleDeleteProduct(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              layout="total, prev, pager, next"
              :total="productTotal"
              :current-page="productPage"
              :page-size="productPageSize"
              @current-change="handleProductPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ============ 类目管理 ============ -->
      <el-tab-pane label="类目管理" name="categories">
        <el-card shadow="never">
          <template #header>
            <div class="header-row">
              <span>三级类目（一级 → 二级 → 三级）</span>
              <el-button type="success" @click="openCreateCategory(0)">新增一级类目</el-button>
            </div>
          </template>

          <el-table
            :data="categories"
            row-key="id"
            :tree-props="{ children: 'children' }"
            default-expand-all
            empty-text="暂无类目"
          >
            <el-table-column prop="name" label="类目名称" min-width="220" />
            <el-table-column label="层级" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="levelOf(row) === 1 ? 'danger' : levelOf(row) === 2 ? 'warning' : 'info'" size="small">
                  {{ levelName(levelOf(row)) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button
                  v-if="levelOf(row) < MAX_CATEGORY_LEVEL"
                  link
                  type="primary"
                  @click="openCreateCategory(row.id)"
                >
                  新增子类目
                </el-button>
                <span v-else class="muted">已达末级</span>
                <el-button link type="danger" @click="handleDeleteCategory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ 用户管理 ============ -->
      <el-tab-pane label="用户管理" name="users">
        <el-card shadow="never">
          <template #header>
            <div class="header-row">
              <div class="filters">
                <el-input
                  v-model="userKeyword"
                  placeholder="用户名 / 昵称"
                  clearable
                  style="width: 200px"
                  @keyup.enter="handleUserSearch"
                />
                <el-button type="primary" @click="handleUserSearch">查询</el-button>
              </div>
              <span class="muted">共 {{ userTotal }} 个用户</span>
            </div>
          </template>

          <el-table v-loading="userLoading" :data="users" empty-text="暂无用户">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="nickname" label="昵称" width="140" />
            <el-table-column prop="email" label="邮箱" min-width="200" />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag :type="row.role === 2 ? 'danger' : 'info'" size="small">
                  {{ row.role === 2 ? '管理员' : '普通用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">
                  {{ row.status === 1 ? '正常' : '已禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  link
                  :type="row.status === 1 ? 'danger' : 'success'"
                  :disabled="row.role === 2"
                  @click="handleToggleUser(row)"
                >
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              layout="total, prev, pager, next"
              :total="userTotal"
              :current-page="userPage"
              :page-size="userPageSize"
              @current-change="handleUserPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ============ 订单管理 ============ -->
      <el-tab-pane label="订单管理" name="orders">
        <el-card shadow="never">
          <template #header>
            <div class="header-row">
              <div class="filters">
                <el-select v-model="orderStatus" style="width: 150px">
                  <el-option label="全部状态" value="" />
                  <el-option v-for="item in ORDER_STATUS" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <el-button type="primary" @click="handleOrderSearch">查询</el-button>
              </div>
              <span class="muted">共 {{ orderTotal }} 笔订单</span>
            </div>
          </template>

          <el-table v-loading="orderLoading" :data="adminOrders" empty-text="暂无订单">
            <el-table-column prop="orderNo" label="订单号" width="180" />
            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">{{ itemsSummary(row) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="110">
              <template #default="{ row }">
                <span class="price">￥{{ row.totalAmount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="当前状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="支付方式" width="110">
              <template #default="{ row }">
                <span class="muted">{{ row.payTypeName || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" link type="primary" @click="handleOrderAction(row, 2, '发货')">
                  发货
                </el-button>
                <el-button v-if="row.status === 2" link type="success" @click="handleOrderAction(row, 3, '完成')">
                  完成
                </el-button>
                <el-button
                  v-if="row.status === 0 || row.status === 1"
                  link
                  type="danger"
                  @click="handleOrderAction(row, 4, '取消')"
                >
                  取消
                </el-button>
                <span v-if="row.status >= 3" class="muted">已结束</span>
              </template>
            </el-table-column>
            <el-table-column label="下单时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              layout="total, prev, pager, next"
              :total="orderTotal"
              :current-page="orderPage"
              :page-size="orderPageSize"
              @current-change="handleOrderPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="轮播图管理" name="carousels">
        <el-card shadow="never">
          <div class="toolbar">
            <el-button type="primary" @click="openCarouselDialog()">新增轮播图</el-button>
            <span class="muted">
              共 {{ carousels.length }} 条 · 排序值越小越靠前 · 未填背景图时使用渐变色
            </span>
          </div>

          <el-table v-loading="carouselLoading" :data="carousels" border>
            <el-table-column label="预览" width="190">
              <template #default="{ row }">
                <div
                  class="carousel-preview"
                  :style="row.imageUrl
                    ? { backgroundImage: `url(${row.imageUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' }
                    : { background: row.bgColor }"
                >
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="120" />
            <el-table-column prop="subtitle" label="副标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="linkUrl" label="跳转路径" min-width="160" show-overflow-tooltip />
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-switch
                  v-model="row.status"
                  :active-value="1"
                  :inactive-value="0"
                  @change="handleCarouselStatusChange(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCarouselDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteCarousel(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 轮播图编辑弹窗 ============ -->
    <el-dialog
      v-model="carouselDialog"
      :title="carouselForm.id ? '编辑轮播图' : '新增轮播图'"
      width="580px"
    >
      <el-form label-width="96px" @submit.prevent>
        <el-form-item label="标题" required>
          <el-input v-model="carouselForm.title" maxlength="100" show-word-limit placeholder="如：个性化推荐引擎" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="carouselForm.subtitle" maxlength="255" placeholder="一句话说明，可留空" />
        </el-form-item>
        <el-form-item label="背景渐变">
          <div class="gradient-picker">
            <div
              v-for="preset in GRADIENT_PRESETS"
              :key="preset.value"
              class="gradient-swatch"
              :class="{ 'is-active': carouselForm.bgColor === preset.value }"
              :style="{ background: preset.value }"
              :title="preset.label"
              @click="carouselForm.bgColor = preset.value"
            ></div>
          </div>
        </el-form-item>
        <el-form-item label="背景图URL">
          <el-input v-model="carouselForm.imageUrl" placeholder="填写后覆盖渐变，可留空" />
        </el-form-item>
        <el-form-item label="跳转路径">
          <el-input v-model="carouselForm.linkUrl" placeholder="如 /products 或 /products?categoryId=2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="carouselForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="carouselForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="carouselDialog = false">取消</el-button>
        <el-button type="primary" :loading="carouselSaving" @click="submitCarousel">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 商品编辑弹窗 ============ -->
    <el-dialog v-model="productDialog" :title="productForm.id ? '编辑商品' : '新增商品'" width="640px">
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="商品名称" required>
          <el-input v-model="productForm.name" maxlength="200" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="所属类目" required>
          <el-select v-model="productForm.categoryId" placeholder="请选择类目" style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="productForm.subtitle" maxlength="255" placeholder="选填" />
        </el-form-item>
        <el-form-item label="主图 URL">
          <el-input v-model="productForm.mainImage" maxlength="255" placeholder="选填" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="售价">
              <el-input-number v-model="productForm.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原价">
              <el-input-number v-model="productForm.originalPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="库存">
              <el-input-number v-model="productForm.stock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="productForm.status" style="width: 100%">
                <el-option label="上架" :value="1" />
                <el-option label="下架" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商品详情">
          <el-input v-model="productForm.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productDialog = false">取消</el-button>
        <el-button type="primary" :loading="productSaving" @click="handleSaveProduct">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 类目编辑弹窗 ============ -->
    <el-dialog v-model="categoryDialog" title="新增类目" width="440px">
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="类目名称" required>
          <el-input v-model="categoryForm.name" maxlength="64" placeholder="请输入类目名称" />
        </el-form-item>
        <el-form-item label="父级类目">
          <el-select v-model="categoryForm.parentId" style="width: 100%">
            <el-option label="无（一级类目）" :value="0" />
            <el-option
              v-for="option in categoryOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
              :disabled="option.depth >= MAX_CATEGORY_LEVEL"
            />
          </el-select>
          <div class="form-hint">
            将创建为「{{ pendingLevelName }}」类目
            <template v-if="categoryForm.parentId && (categoryLevels[categoryForm.parentId] || 1) >= MAX_CATEGORY_LEVEL">
              —— 该父级已是末级，不能再往下加
            </template>
          </div>
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialog = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="handleSaveCategory">保存</el-button>
      </template>
    </el-dialog>
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
  gap: 12px;
}

.filters {
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

/* ================= 轮播图管理 ================= */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.muted {
  font-size: 12px;
  color: #909399;
}

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.carousel-preview {
  height: 58px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  padding: 0 8px;
  text-align: center;
}

.gradient-picker {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.gradient-swatch {
  width: 54px;
  height: 30px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.15s, transform 0.15s;
}

.gradient-swatch:hover {
  transform: translateY(-1px);
}

.gradient-swatch.is-active {
  border-color: #303133;
}
</style>
