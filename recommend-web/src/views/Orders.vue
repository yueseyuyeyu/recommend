<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, confirmReceipt, getOrderDetail, getOrders } from '../api'

const router = useRouter()
const loading = ref(false)
const orders = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

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

/** 去收银台付款；本页只做订单查看与状态操作，不承担下单职责 */
function handlePay(row) {
  router.push(`/pay/${row.orderNo}`)
}

/** 确认收货：只有已发货的订单能点 */
async function handleReceipt(row) {
  try {
    await ElMessageBox.confirm(
      `确认已经收到订单 ${row.orderNo} 的商品了吗？确认后订单将标记为已完成。`,
      '确认收货',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await confirmReceipt(row.orderNo)
    ElMessage.success('已确认收货')
    await loadOrders()
  } catch {
    await loadOrders()
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

onMounted(loadOrders)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2 class="page-title">我的订单</h2>
      <span class="muted">下单请前往购物车结算</span>
    </div>

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
        <el-table-column label="支付方式" width="110">
          <template #default="{ row }">
            <span class="muted">{{ row.payTypeName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="warning" @click="handlePay(row)">
              去支付
            </el-button>
            <el-button v-if="row.status === 2" link type="success" @click="handleReceipt(row)">
              确认收货
            </el-button>
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
            <el-descriptions-item label="支付方式">
              {{ detail.payTypeName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="支付时间">
              {{ detail.paidTime ? formatTime(detail.paidTime) : '未支付' }}
            </el-descriptions-item>
            <el-descriptions-item label="物流单号">
              {{ detail.trackingNo || '未发货' }}
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
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.detail-items {
  margin-top: 16px;
}

.muted {
  font-size: 12px;
  color: #909399;
}
</style>
