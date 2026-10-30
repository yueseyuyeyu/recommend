<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, createOrder, getAddresses, getOrderDetail, getOrders } from '../api'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const loading = ref(false)
const orders = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

/** 收货地址簿；选择 0 表示手动填写新地址 */
const addresses = ref([])
const selectedAddressId = ref(0)

const form = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddr: ''
})

const rules = {
  receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入 11 位手机号', trigger: 'blur' }
  ],
  receiverAddr: [{ required: true, message: '请输入收货地址', trigger: 'blur' }]
}

/** 加载地址簿，默认选中默认地址 */
async function loadAddresses() {
  try {
    addresses.value = (await getAddresses()) || []
    const preferred = addresses.value.find((item) => item.isDefault === 1) || addresses.value[0]
    selectedAddressId.value = preferred ? preferred.id : 0
  } catch {
    addresses.value = []
    selectedAddressId.value = 0
  }
}

async function loadOrders() {
  loading.value = true
  try {
    const data = await getOrders({ pageNum: pageNum.value, pageSize: pageSize.value })
    orders.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  let payload

  if (selectedAddressId.value) {
    // 选了地址簿里的地址：只传 ID，收货信息由服务端回查，前端改不动
    payload = { addressId: selectedAddressId.value }
  } else {
    try {
      await formRef.value.validate()
    } catch {
      return
    }
    payload = { ...form }
  }

  submitting.value = true
  try {
    const orderNo = await createOrder(payload)
    ElMessage.success(`下单成功，订单号：${orderNo}`)
    formRef.value?.resetFields()
    pageNum.value = 1
    await loadOrders()
  } catch {
    // 错误提示由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(`确定取消订单 ${row.orderNo} 吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelOrder(row.orderNo)
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch {
    // 错误提示由拦截器处理
  }
}

function handlePageChange(page) {
  pageNum.value = page
  loadOrders()
}

async function handleDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getOrderDetail(row.orderNo)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function itemsSummary(row) {
  return (row.items || []).map((item) => `${item.productName}×${item.quantity}`).join('、')
}

function statusTagType(status) {
  return { 0: 'warning', 1: 'success', 2: 'primary', 3: 'info', 4: 'info' }[status] || 'info'
}

function formatTime(time) {
  return (time || '').replace('T', ' ')
}

onMounted(() => {
  loadOrders()
  loadAddresses()
})
</script>

<template>
  <div class="page">
    <h2 class="page-title">我的订单</h2>

    <el-card shadow="never" class="create-card">
      <template #header>
        <div class="create-head">
          <span>提交订单</span>
          <el-button link type="primary" size="small" @click="router.push('/profile')">
            管理收货地址
          </el-button>
        </div>
      </template>

      <!-- 地址簿存在时优先选地址 -->
      <el-radio-group v-if="addresses.length" v-model="selectedAddressId" class="address-radios">
        <el-radio v-for="row in addresses" :key="row.id" :value="row.id" class="address-radio">
          <span class="addr-line">
            <b>{{ row.receiverName }}</b>
            <span class="muted">{{ row.receiverPhone }}</span>
            <el-tag v-if="row.isDefault === 1" size="small" type="danger">默认</el-tag>
          </span>
          <span class="addr-detail">{{ row.detailAddress }}</span>
        </el-radio>
        <el-radio :value="0" class="address-radio">
          <span class="addr-line"><b>手动填写新地址</b></span>
        </el-radio>
      </el-radio-group>

      <!-- 未选地址（或无地址）时手填 -->
      <el-form
        v-if="!selectedAddressId"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="88px"
        @submit.prevent
      >
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="收货人" prop="receiverName">
              <el-input v-model="form.receiverName" placeholder="请输入收货人" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话" prop="receiverPhone">
              <el-input v-model="form.receiverPhone" placeholder="请输入手机号" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货地址" prop="receiverAddr">
              <el-input v-model="form.receiverAddr" placeholder="请输入收货地址" clearable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="submit-row">
        <el-button type="primary" :loading="submitting" @click="handleCreate">提交订单</el-button>
        <span class="muted">下单将结算购物车中已勾选的商品</span>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="orders" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">{{ itemsSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span class="price">￥{{ row.totalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 0 || row.status === 1"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              取消
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

    <el-dialog v-model="detailVisible" title="订单详情" width="640px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(detail.status)">{{ detail.statusName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="金额">
              <span class="price">￥{{ detail.totalAmount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="收货人">{{ detail.receiverName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.receiverPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">
              {{ detail.receiverAddr || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="下单时间" :span="2">
              {{ formatTime(detail.createTime) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-table :data="detail.items || []" size="small" class="detail-items" empty-text="无明细">
            <el-table-column prop="productName" label="商品" min-width="200" />
            <el-table-column label="单价" width="100">
              <template #default="{ row }">￥{{ row.price }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="小计" width="110">
              <template #default="{ row }"><span class="price">￥{{ row.subtotal }}</span></template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.create-card {
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.detail-items {
  margin-top: 16px;
}

/* 收货地址选择 */
.create-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.address-radios {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

/* 让单选项变成整行卡片，纵向排布收货信息 */
.address-radio {
  height: auto;
  align-items: flex-start;
  margin-right: 0;
  padding: 10px 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  width: 100%;
  transition: border-color 0.15s, background 0.15s;
}

.address-radio:hover {
  border-color: #ffd0b8;
  background: #fffaf7;
}

.address-radio :deep(.el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 3px;
  white-space: normal;
  line-height: 1.5;
}

.addr-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.addr-detail {
  font-size: 13px;
  color: #606266;
}

.submit-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.muted {
  font-size: 12px;
  color: #909399;
}
</style>
