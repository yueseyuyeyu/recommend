<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminConfirmReceipt,
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
  shipOrder,
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

/**
 * 评测指标说明。
 *
 * 每个指标都配三层信息：显示名、一句话白话解释、悬停时的计算口径。
 * 目的是让没接触过推荐系统的人也能看懂这张表，而不是只看一堆百分比。
 */
const metricCards = computed(() => {
  const data = evaluation.value
  if (!data) return []

  return [
    {
      key: 'precision',
      label: 'Precision',
      alias: '命中率',
      value: percent(data.precision),
      desc: `推荐 ${data.topN} 个，大约有 ${percent(data.precision)} 是用户真会点的`,
      hint: '分子：推荐的 TopN 中确实出现在用户测试集里的商品数；分母：TopN × 参评用户数。衡量"推得准不准"。'
    },
    {
      key: 'recall',
      label: 'Recall',
      alias: '召回率',
      value: percent(data.recall),
      desc: '用户会点的商品里，被推荐覆盖到的比例',
      hint: '分子同上；分母：该用户在测试集里的商品总数。衡量"漏得严不严重"。'
    },
    {
      key: 'f1',
      label: 'F1',
      alias: '综合分',
      value: percent(data.f1),
      desc: '命中率与召回率的调和平均，防止只优化一头',
      hint: '2PR/(P+R)。只要命中率或召回率有一个很低，F1 就会被明显拉下来。'
    },
    {
      key: 'coverage',
      label: 'Coverage',
      alias: '覆盖率',
      value: percent(data.coverage),
      desc: `全站 ${data.totalItemCount} 个商品里，被推荐过 ${data.recommendedItemCount} 个`,
      hint: '覆盖率过低说明算法在反复推同样的爆款，个性化没有真正起作用。这一项要和平均流行度一起看。'
    },
    {
      key: 'evaluatedUserCount',
      label: '参评用户数',
      alias: '样本量',
      value: data.evaluatedUserCount ?? '-',
      desc: '测试集里有行为、可以参与评估的用户数',
      hint: '没有行为记录的用户无法评估，会被排除。样本量太小的情况下，指标波动会很大，参考价值有限。'
    },
    {
      key: 'avgPopularity',
      label: 'AvgPopularity',
      alias: '平均流行度',
      value: Number(data.avgPopularity || 0).toFixed(3),
      desc: '推荐商品热度的对数均值，越低说明越不扎堆热门',
      hint: '取的是 log1p(商品销量) 的平均值，不是平均销量本身。接近全站平均水平说明推荐接近热门榜，明显偏低说明更偏长尾。'
    }
  ]
})

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

/* ---------- 发货弹窗（选配送方 + 自动生成单号） ---------- */
/** 与后端 Carrier 枚举保持一致 */
const CARRIERS = [
  { code: 'SF', name: '顺丰速运', sample: 'SF + 13 位数字' },
  { code: 'EMS', name: '中国邮政', sample: 'E + 9 位数字 + CN' },
  { code: 'ZTO', name: '中通快递', sample: '75 开头共 12 位' },
  { code: 'YTO', name: '圆通速递', sample: 'YT + 13 位数字' },
  { code: 'YUNDA', name: '韵达快递', sample: '43 开头共 12 位' },
  { code: 'JD', name: '京东物流', sample: 'JD + 12 位数字' }
]

const shipDialog = ref(false)
const shipSaving = ref(false)
const shipForm = reactive({ orderNo: '', carrier: 'SF', trackingNo: '' })

/**
 * 本地生成一个格式正确的单号，仅用于弹窗里的即时预览。
 * 真正落库的单号以后端生成为准——管理员清空后提交，后端会重新生成。
 */
function generateTrackingNo(code) {
  const rule = {
    SF: () => 'SF' + randomDigits(13),
    EMS: () => 'E' + randomDigits(9) + 'CN',
    ZTO: () => '75' + randomDigits(10),
    YTO: () => 'YT' + randomDigits(13),
    YUNDA: () => '43' + randomDigits(10),
    JD: () => 'JD' + randomDigits(12)
  }[code]
  return rule ? rule() : randomDigits(12)
}

function randomDigits(count) {
  let out = ''
  for (let i = 0; i < count; i += 1) {
    out += Math.floor(Math.random() * 10)
  }
  return out
}

function openShipDialog(row) {
  shipForm.orderNo = row.orderNo
  shipForm.carrier = 'SF'
  shipForm.trackingNo = generateTrackingNo('SF')
  shipDialog.value = true
}

/** 换配送方时同步换一个符合该快递规则的单号 */
function handleCarrierChange(code) {
  shipForm.trackingNo = generateTrackingNo(code)
}

async function submitShip() {
  if (!shipForm.carrier) {
    ElMessage.warning('请选择配送方')
    return
  }
  shipSaving.value = true
  try {
    const finalNo = await shipOrder(shipForm.orderNo, shipForm.carrier, shipForm.trackingNo.trim())
    ElMessage.success(`已发货，物流单号 ${finalNo}`)
    shipDialog.value = false
    await Promise.all([loadAdminOrders(), loadStats()])
  } catch {
    // 错误提示由拦截器处理
  } finally {
    shipSaving.value = false
  }
}

/* ---------- 管理员确认收货（需发货满 7 天） ---------- */
const AUTO_RECEIPT_DAYS = 7

/** 发货是否已满 7 天，未满时不给管理员确认收货的入口 */
function receiptReady(row) {
  if (!row.shipTime) return false
  const shippedAt = new Date(row.shipTime).getTime()
  if (Number.isNaN(shippedAt)) return false
  return Date.now() - shippedAt >= AUTO_RECEIPT_DAYS * 24 * 3600 * 1000
}

function receiptRemainDays(row) {
  if (!row.shipTime) return AUTO_RECEIPT_DAYS
  const shippedAt = new Date(row.shipTime).getTime()
  if (Number.isNaN(shippedAt)) return AUTO_RECEIPT_DAYS
  const left = AUTO_RECEIPT_DAYS - Math.floor((Date.now() - shippedAt) / (24 * 3600 * 1000))
  return Math.max(left, 0)
}

async function handleAdminReceipt(row) {
  try {
    await ElMessageBox.confirm(
      `确认订单 ${row.orderNo} 已妥投并完成交易吗？确认后不可撤销。`,
      '确认收货',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await adminConfirmReceipt(row.orderNo)
    ElMessage.success('已确认收货，订单完成')
    await Promise.all([loadAdminOrders(), loadStats()])
  } catch {
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
            <el-descriptions-item v-for="item in metricCards" :key="item.key">
              <template #label>
                <span class="metric-label">
                  {{ item.label }}
                  <el-tooltip :content="item.hint" placement="top" :show-after="150">
                    <span class="metric-help">?</span>
                  </el-tooltip>
                </span>
              </template>
              <div class="metric-value">
                {{ item.value }}
                <span class="metric-alias">{{ item.alias }}</span>
              </div>
              <div class="metric-desc">{{ item.desc }}</div>
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="evaluation" class="metric-note">
            <div class="metric-note-line">
              <strong>怎么读这张表：</strong>
              当前 TopN={{ evaluation.topN }}，留出法（80% 行为训练、20% 行为测试）。
              Precision 要配 Coverage 一起看——只追命中率通常会牺牲推荐多样性。
            </div>
            <div class="metric-note-line">
              <strong>数据说明：</strong>
              本项目为单机本地环境，行为数据由种子脚本模拟生成（{{ evaluation.totalItemCount }} 个在售商品、{{ evaluation.evaluatedUserCount }} 位参评用户），
              <strong>指标绝对值不代表线上效果</strong>，只有横向比较不同参数时才有参考意义。
            </div>
          </div>
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
            <el-table-column label="物流" width="200">
              <template #default="{ row }">
                <template v-if="row.trackingNo">
                  <div class="muted">{{ row.carrierName || '—' }}</div>
                  <div class="logistics-no">{{ row.trackingNo }}</div>
                </template>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" link type="primary" @click="openShipDialog(row)">
                  发货
                </el-button>
                <template v-if="row.status === 2">
                  <el-button
                    v-if="receiptReady(row)"
                    link
                    type="success"
                    @click="handleAdminReceipt(row)"
                  >
                    确认收货
                  </el-button>
                  <el-tooltip
                    v-else
                    :content="`发货满 ${AUTO_RECEIPT_DAYS} 天后可确认收货，还剩 ${receiptRemainDays(row)} 天`"
                    placement="top"
                  >
                    <span class="muted-disabled">确认收货</span>
                  </el-tooltip>
                </template>
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

    <!-- ============ 发货弹窗 ============ -->
    <el-dialog v-model="shipDialog" title="订单发货" width="480px">
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="订单号">
          <span class="muted">{{ shipForm.orderNo }}</span>
        </el-form-item>
        <el-form-item label="配送方" required>
          <el-select
            v-model="shipForm.carrier"
            style="width: 100%"
            @change="handleCarrierChange"
          >
            <el-option
              v-for="item in CARRIERS"
              :key="item.code"
              :label="`${item.name}（${item.sample}）`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号">
          <div class="tracking-row">
            <el-input
              v-model="shipForm.trackingNo"
              maxlength="64"
              placeholder="留空则由后端自动生成"
              @keyup.enter="submitShip"
            />
            <el-button @click="shipForm.trackingNo = generateTrackingNo(shipForm.carrier)">
              重新生成
            </el-button>
          </div>
          <div class="form-hint">单号按所选配送方的规则自动生成，也可以手动改写。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" :loading="shipSaving" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>

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

/* ---------- 评测指标 ---------- */
.metric-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 小问号：悬停显示计算口径 */
.metric-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid #dcdfe6;
  color: #909399;
  font-size: 10px;
  line-height: 1;
  cursor: help;
  transition: border-color 0.15s, color 0.15s;
}

.metric-help:hover {
  border-color: #ff5000;
  color: #ff5000;
}

.metric-value {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.metric-alias {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.metric-desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.metric-note {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #fafafa;
  border-left: 3px solid #ff5000;
}

.metric-note-line {
  font-size: 12.5px;
  line-height: 1.8;
  color: #606266;
}

.metric-note-line + .metric-note-line {
  margin-top: 2px;
}

.metric-note-line strong {
  color: #303133;
  font-weight: 600;
}

/* ---------- 发货弹窗 ---------- */
.tracking-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.logistics-no {
  font-size: 12px;
  color: #303133;
}

/* 未到期的「确认收货」：看起来像按钮但不可点 */
.muted-disabled {
  font-size: 14px;
  color: #c0c4cc;
  cursor: not-allowed;
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
