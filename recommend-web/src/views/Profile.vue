<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAddress,
  deleteAddress,
  getAddresses,
  getUserHistory,
  getUserProfile,
  getUserStats,
  setDefaultAddress,
  updateAddress,
  updateUserProfile,
  uploadAvatar
} from '../api'
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
const uploading = ref(false)

const form = reactive({
  nickname: '',
  phone: '',
  email: ''
})

/** 无头像时用昵称首字兜底 */
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

/* ---------- 头像上传 ---------- */
async function handleAvatarUpload(option) {
  const file = option.file
  if (!file) return
  if (!/^image\//.test(file.type)) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 2MB')
    return
  }

  uploading.value = true
  try {
    const url = await uploadAvatar(file)
    // 上传成功即落库，省掉"再点一次保存"的步骤
    const updated = await updateUserProfile({ avatar: url })
    profile.value = updated
    userStore.patchUserInfo({ avatar: updated.avatar })
    ElMessage.success('头像已更新')
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

/* ---------- 收货地址簿 ---------- */
const addresses = ref([])
const addressLoading = ref(false)
const addressDialog = ref(false)
const addressSaving = ref(false)

const addressForm = reactive({
  id: null,
  receiverName: '',
  receiverPhone: '',
  detailAddress: '',
  isDefault: false
})

async function loadAddresses() {
  addressLoading.value = true
  try {
    addresses.value = (await getAddresses()) || []
  } catch {
    addresses.value = []
  } finally {
    addressLoading.value = false
  }
}

function resetAddressForm() {
  addressForm.id = null
  addressForm.receiverName = profile.value?.nickname || ''
  addressForm.receiverPhone = profile.value?.phone || ''
  addressForm.detailAddress = ''
  addressForm.isDefault = addresses.value.length === 0
}

function openAddressDialog(row) {
  if (row) {
    Object.assign(addressForm, {
      id: row.id,
      receiverName: row.receiverName,
      receiverPhone: row.receiverPhone,
      detailAddress: row.detailAddress,
      isDefault: row.isDefault === 1
    })
  } else {
    resetAddressForm()
  }
  addressDialog.value = true
}

async function submitAddress() {
  if (!addressForm.receiverName.trim()) {
    ElMessage.warning('请填写收货人')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(addressForm.receiverPhone.trim())) {
    ElMessage.warning('请填写正确的 11 位手机号')
    return
  }
  if (!addressForm.detailAddress.trim()) {
    ElMessage.warning('请填写详细地址')
    return
  }

  addressSaving.value = true
  try {
    const payload = {
      receiverName: addressForm.receiverName.trim(),
      receiverPhone: addressForm.receiverPhone.trim(),
      detailAddress: addressForm.detailAddress.trim(),
      isDefault: addressForm.isDefault
    }
    if (addressForm.id) {
      await updateAddress(addressForm.id, payload)
      ElMessage.success('地址已更新')
    } else {
      await createAddress(payload)
      ElMessage.success('地址已新增')
    }
    addressDialog.value = false
    loadAddresses()
  } catch {
    // 拦截器已提示
  } finally {
    addressSaving.value = false
  }
}

async function removeAddress(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.receiverName} ${row.detailAddress}」吗？`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteAddress(row.id)
    ElMessage.success('已删除')
    loadAddresses()
  } catch {
    // 拦截器已提示
  }
}

async function makeDefault(row) {
  if (row.isDefault === 1) return
  try {
    await setDefaultAddress(row.id)
    ElMessage.success('已设为默认地址')
    loadAddresses()
  } catch {
    // 拦截器已提示
  }
}

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
      email: profileData?.email || ''
    })
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
  loadAddresses()
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
      email: form.email.trim()
    })
    profile.value = updated
    userStore.patchUserInfo({ nickname: updated.nickname })
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
        <el-upload
          class="avatar-upload"
          :show-file-list="false"
          :http-request="handleAvatarUpload"
          accept="image/*"
        >
          <div v-loading="uploading" class="avatar-wrap">
            <el-avatar :size="80" :src="profile?.avatar || undefined" class="avatar">
              {{ avatarText }}
            </el-avatar>
            <div class="avatar-mask">更换</div>
          </div>
        </el-upload>

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

      <div class="stat-row">
        <div v-for="item in statItems" :key="item.key" class="stat-item" @click="router.push(item.to)">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </el-card>

    <!-- ============ 基本资料 ============ -->
    <el-card shadow="never" class="section-card">
      <template #header><span class="card-title">基本资料</span></template>
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
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存资料</el-button>
          <el-button @click="loadAll">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ============ 收货地址 ============ -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-head">
          <span class="card-title">收货地址</span>
          <span class="muted">共 {{ addresses.length }} 条，下单时可直接选择</span>
          <el-button type="primary" size="small" @click="openAddressDialog()">新增地址</el-button>
        </div>
      </template>

      <div v-loading="addressLoading" class="address-list">
        <div
          v-for="row in addresses"
          :key="row.id"
          class="address-item"
          :class="{ 'is-default': row.isDefault === 1 }"
        >
          <div class="address-main">
            <div class="address-line">
              <span class="receiver">{{ row.receiverName }}</span>
              <span class="muted">{{ row.receiverPhone }}</span>
              <el-tag v-if="row.isDefault === 1" size="small" type="danger">默认</el-tag>
            </div>
            <div class="address-detail">{{ row.detailAddress }}</div>
          </div>
          <div class="address-actions">
            <el-button v-if="row.isDefault !== 1" link type="primary" size="small" @click="makeDefault(row)">
              设为默认
            </el-button>
            <el-button link type="primary" size="small" @click="openAddressDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeAddress(row)">删除</el-button>
          </div>
        </div>

        <el-empty
          v-if="!addressLoading && addresses.length === 0"
          description="还没有收货地址，新增一条下单时会方便很多"
          :image-size="70"
        />
      </div>
    </el-card>

    <!-- ============ 足迹 ============ -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-head">
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

    <!-- ============ 地址编辑弹窗 ============ -->
    <el-dialog
      v-model="addressDialog"
      :title="addressForm.id ? '编辑地址' : '新增地址'"
      width="520px"
    >
      <el-form label-width="88px" @submit.prevent>
        <el-form-item label="收货人" required>
          <el-input v-model="addressForm.receiverName" maxlength="64" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="addressForm.receiverPhone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="详细地址" required>
          <el-input
            v-model="addressForm.detailAddress"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="省 / 市 / 区 / 街道门牌号"
          />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="addressForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addressDialog = false">取消</el-button>
        <el-button type="primary" :loading="addressSaving" @click="submitAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-card {
  margin-bottom: 16px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
}

/* 头像上传：悬停出现「更换」遮罩 */
.avatar-upload {
  flex: 0 0 auto;
}

.avatar-wrap {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
}

.avatar {
  background: #ff5000;
  color: #fff;
  font-size: 30px;
  font-weight: 600;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
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

.section-card {
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.section-head .el-button {
  margin-left: auto;
}

.profile-form {
  max-width: 560px;
}

/* 地址列表 */
.address-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 40px;
}

.address-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  transition: border-color 0.15s, background 0.15s;
}

.address-item:hover {
  border-color: #ffd0b8;
  background: #fffaf7;
}

.address-item.is-default {
  border-color: #ff5000;
  background: #fff8f4;
}

.address-main {
  flex: 1;
  min-width: 0;
}

.address-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.receiver {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.address-detail {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.address-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 4px;
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
