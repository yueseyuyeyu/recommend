<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserHistory, getUserProfile, getUserStats, updateUserProfile } from '../api'
import { useUserStore } from '../stores/user'
import { useFavoriteStore } from '../stores/favorite'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()
const userStore = useUserStore()
const favoriteStore = useFavoriteStore()

const PAGE_SIZE = 12

const profile = ref(null)
const stats = ref({})
const loading = ref(false)
const saving = ref(false)

const form = reactive({
  nickname: '',
  phone: '',
  email: '',
  address: '',
  avatar: ''
})

/** 没有头像时用昵称首字兜底 */
const avatarText = computed(() => {
  const name = profile.value?.nickname || profile.value?.username || '?'
  return name.trim().charAt(0).toUpperCase()
})

/* ---------- 待办数量 ---------- */
const statItems = computed(() => [
  { key: 'cartCount', label: '购物车', value: stats.value.cartCount || 0, to: '/cart' },
  { key: 'pendingReceipt', label: '待收货', value: stats.value.pendingReceipt || 0, to: '/orders' },
  { key: 'pendingShipment', label: '待发货', value: stats.value.pendingShipment || 0, to: '/orders' },
  { key: 'pendingPayment', label: '待付款', value: stats.value.pendingPayment || 0, to: '/orders' },
  { key: 'pendingReview', label: '待评价', value: stats.value.pendingReview || 0, to: '/orders' }
])

/* ---------- 足迹 ---------- */
const activeTab = ref('VIEW')
const historyList = ref([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyLoading = ref(false)

const historyPages = computed(() => Math.max(1, Math.ceil(historyTotal.value / PAGE_SIZE)))

async function loadHistory() {
  historyLoading.value = true
  try {
    const page = await getUserHistory(activeTab.value, historyPage.value, PAGE_SIZE)
    historyList.value = page?.records || []
    historyTotal.value = page?.total || 0
  } catch {
    historyList.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

function switchTab() {
  // v-model 已更新 activeTab，这里只需要回到第一页重新拉取
  historyPage.value = 1
  loadHistory()
}

function handleHistoryPage(page) {
  historyPage.value = page
  loadHistory()
}

/* ---------- 资料 ---------- */
async function loadAll() {
  loading.value = true
  try {
    const [profileData, statsData] = await Promise.all([getUserProfile(), getUserStats()])
    profile.value = profileData
    stats.value = statsData || {}
    Object.assign(form, {
      nickname: profileData?.nickname || '',
      phone: profileData?.phone || '',
      email: profileData?.email || '',
      address: profileData?.address || '',
      avatar: profileData?.avatar || ''
    })
  } catch {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false
  }
  loadHistory()
}

async function handleSave() {
  if (!form.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    const updated = await updateUserProfile({
      nickname: form.nickname.trim(),
      phone: form.phone.trim(),
      email: form.email.trim(),
      address: form.address.trim(),
      avatar: form.avatar.trim()
    })
    profile.value = updated
    // 同步导航栏头像与昵称
    userStore.patchUserInfo({
      nickname: updated.nickname,
      avatar: updated.avatar
    })
    ElMessage.success('资料已保存')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function openProduct(item) {
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

onMounted(() => {
  if (userStore.isLoggedIn) favoriteStore.load()
  loadAll()
})
</script>

<template>
  <div v-loading="loading" class="page">
    <!-- ============ 资料卡 ============ -->
    <el-card shadow="never" class="profile-card">
      <div class="profile-head">
        <el-avatar :size="64" :src="profile?.avatar || undefined" class="avatar">
          {{ avatarText }}
        </el-avatar>
        <div class="identity">
          <div class="nickname">
            {{ profile?.nickname || profile?.username || '未登录' }}
            <el-tag v-if="profile?.role === 2" size="small" type="danger">管理员</el-tag>
          </div>
          <div class="muted">账号：{{ profile?.username }}</div>
          <div class="muted">
            {{ profile?.phone || '未填写手机号' }} · {{ profile?.email || '未填写邮箱' }}
          </div>
        </div>
      </div>

      <!-- 待办数量：对应淘宝个人中心那一排数字 -->
      <div class="stat-row">
        <div v-for="item in statItems" :key="item.key" class="stat-item" @click="router.push(item.to)">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </el-card>

    <!-- ============ 资料编辑 ============ -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <span class="card-title">基本资料</span>
      </template>
      <el-form label-width="88px" class="profile-form" @submit.prevent>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="64" placeholder="怎么称呼你" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号，可留空" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="128" placeholder="用于接收通知，可留空" />
        </el-form-item>
        <el-form-item label="收货地址">
          <el-input
            v-model="form.address"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="常用收货地址，下单时可自动填充"
          />
        </el-form-item>
        <el-form-item label="头像URL">
          <el-input v-model="form.avatar" maxlength="255" placeholder="图片地址，留空则用昵称首字" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存资料</el-button>
          <el-button @click="loadAll">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ============ 足迹 ============ -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="tabs-head">
          <span class="card-title">我的足迹</span>
          <el-radio-group v-model="activeTab" size="small" @change="switchTab">
            <el-radio-button value="VIEW">浏览过</el-radio-button>
            <el-radio-button value="PURCHASE">买过</el-radio-button>
          </el-radio-group>
          <span class="muted">共 {{ historyTotal }} 件</span>
        </div>
      </template>

      <div v-loading="historyLoading" class="card-grid">
        <ProductCard
          v-for="item in historyList"
          :key="item.id"
          :item="item"
          :favorited="favoriteStore.isFavorited(item.id)"
          :show-reason="false"
          @open="openProduct"
          @toggle-favorite="toggleFavorite"
        />
      </div>

      <el-empty
        v-if="!historyLoading && historyList.length === 0"
        :description="activeTab === 'VIEW' ? '还没有浏览过商品' : '还没有买过商品'"
      />

      <div v-if="historyTotal > PAGE_SIZE" class="pagination">
        <el-pagination
          layout="prev, pager, next"
          :current-page="historyPage"
          :page-size="PAGE_SIZE"
          :total="historyTotal"
          @current-change="handleHistoryPage"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.profile-card {
  margin-bottom: 16px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
}

.avatar {
  background: #ff5000;
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  flex: 0 0 auto;
}

.identity {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.nickname {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.muted {
  font-size: 12px;
  color: #909399;
}

/* 待办数字 */
.stat-row {
  display: flex;
  padding-top: 18px;
}

.stat-item {
  flex: 1;
  text-align: center;
  cursor: pointer;
  border-right: 1px solid #f5f5f5;
  transition: color 0.15s;
}

.stat-item:last-child {
  border-right: none;
}

.stat-item:hover {
  color: #ff5000;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: #303133;
}

.stat-item:hover .stat-value {
  color: #ff5000;
}

.stat-label {
  margin-top: 4px;
  font-size: 12.5px;
  color: #606266;
}

.stat-item:hover .stat-label {
  color: #ff5000;
}

/* 区块 */
.section-card {
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.profile-form {
  max-width: 560px;
}

.tabs-head {
  display: flex;
  align-items: center;
  gap: 16px;
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

  .stat-row {
    flex-wrap: wrap;
    gap: 12px 0;
  }

  .stat-item {
    flex: 0 0 33.33%;
  }
}
</style>
