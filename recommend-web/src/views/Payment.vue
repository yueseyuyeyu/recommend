<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrderDetail, payOrder } from '../api'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => route.params.orderNo)

const order = ref(null)
const loading = ref(false)
const paying = ref(false)
const paid = ref(false)

/** 支付方式，与后端 PayType 枚举一致 */
const PAY_TYPES = [
  { value: 1, label: '支付宝', desc: '推荐有支付宝账号的用户', color: '#1677ff' },
  { value: 2, label: '微信支付', desc: '推荐已开通微信支付的用户', color: '#07c160' },
  { value: 3, label: '银行卡', desc: '支持储蓄卡与信用卡', color: '#e6531f' }
]

const payType = ref(1)

/** 只有待支付订单能进收银台 */
const payable = computed(() => order.value?.status === 0)

const payTypeName = computed(
  () => PAY_TYPES.find((item) => item.value === payType.value)?.label || '未知方式'
)

const itemCount = computed(() =>
  (order.value?.items || []).reduce((sum, item) => sum + (item.quantity || 0), 0)
)

async function loadOrder() {
  loading.value = true
  try {
    const data = await getOrderDetail(orderNo.value)
    order.value = data
    // 沿用下单时选定的支付方式，用户可以改
    if (data?.payType) {
      payType.value = data.payType
    }
    paid.value = data?.status === 1
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
}

async function handlePay() {
  if (!payable.value) return
  paying.value = true
  try {
    await payOrder(orderNo.value, payType.value)
    paid.value = true
    ElMessage.success('支付成功')
    await loadOrder()
  } catch {
    // 错误提示由拦截器处理
  } finally {
    paying.value = false
  }
}

onMounted(loadOrder)
</script>

<template>
  <div v-loading="loading" class="page">
    <h2 class="page-title">收银台</h2>

    <el-empty v-if="!loading && !order" description="订单不存在或无权访问">
      <el-button type="primary" @click="router.push('/orders')">返回订单列表</el-button>
    </el-empty>

    <template v-else-if="order">
      <!-- 支付成功 -->
      <el-card v-if="paid" shadow="never" class="success-card">
        <el-result icon="success" title="支付成功" :sub-title="`订单号：${order.orderNo}`">
          <template #extra>
            <el-button type="primary" @click="router.push('/orders')">查看我的订单</el-button>
            <el-button @click="router.push('/')">继续逛逛</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 不可支付的订单（已取消等） -->
      <el-card v-else-if="!payable" shadow="never" class="success-card">
        <el-result
          icon="warning"
          :title="`该订单当前状态为「${order.statusName}」`"
          sub-title="只有待支付订单可以付款"
        >
          <template #extra>
            <el-button type="primary" @click="router.push('/orders')">查看我的订单</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 待支付：展示订单信息并选择支付方式 -->
      <template v-else>
        <el-card shadow="never" class="order-card">
          <div class="amount-row">
            <span class="muted">应付金额</span>
            <span class="amount">￥{{ order.totalAmount }}</span>
          </div>
          <div class="meta-grid">
            <div class="meta-item">
              <span class="meta-label">订单号</span>
              <span class="meta-value">{{ order.orderNo }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">商品件数</span>
              <span class="meta-value">{{ itemCount }} 件</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">收货人</span>
              <span class="meta-value">{{ order.receiverName }} {{ order.receiverPhone }}</span>
            </div>
            <div class="meta-item wide">
              <span class="meta-label">收货地址</span>
              <span class="meta-value">{{ order.receiverAddr }}</span>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="pay-card">
          <template #header><span class="card-title">选择支付方式</span></template>
          <div class="pay-types">
            <div
              v-for="type in PAY_TYPES"
              :key="type.value"
              class="pay-type"
              :class="{ 'is-active': payType === type.value }"
              @click="payType = type.value"
            >
              <div class="pay-name" :style="{ color: type.color }">{{ type.label }}</div>
              <div class="pay-desc">{{ type.desc }}</div>
            </div>
          </div>

          <div class="actions">
            <el-button type="primary" size="large" :loading="paying" @click="handlePay">
              确认支付 ￥{{ order.totalAmount }}
            </el-button>
            <el-button size="large" @click="router.push('/orders')">稍后支付</el-button>
          </div>
          <p class="hint">
            这是学习项目的模拟收银台，点击确认支付即把订单状态流转为「已支付」，不会产生真实扣款。
          </p>
        </el-card>

        <!-- 商品清单 -->
        <el-card shadow="never" class="pay-card">
          <template #header><span class="card-title">商品清单</span></template>
          <el-table :data="order.items || []" size="small">
            <el-table-column prop="productName" label="商品" min-width="240" />
            <el-table-column prop="price" label="单价" width="120" />
            <el-table-column prop="quantity" label="数量" width="90" />
            <el-table-column prop="subtotal" label="小计" width="120" />
          </el-table>
        </el-card>
      </template>
    </template>
  </div>
</template>

<style scoped>
.success-card,
.order-card,
.pay-card {
  margin-bottom: 16px;
}

.amount-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
}

.amount {
  font-size: 30px;
  font-weight: 700;
  color: #f56c6c;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin-top: 16px;
}

.meta-item {
  display: flex;
  gap: 10px;
  font-size: 13px;
}

.meta-item.wide {
  grid-column: 1 / -1;
}

.meta-label {
  flex: 0 0 64px;
  color: #909399;
}

.meta-value {
  color: #303133;
  word-break: break-all;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.pay-types {
  display: flex;
  gap: 12px;
}

.pay-type {
  flex: 1;
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.pay-type:hover {
  border-color: #ffd0b8;
}

.pay-type.is-active {
  border-color: #ff5000;
  background: #fff8f4;
  box-shadow: inset 0 0 0 1px #ff5000;
}

.pay-name {
  font-size: 15px;
  font-weight: 600;
}

.pay-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}

.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: #b1b3b8;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .meta-grid {
    grid-template-columns: 1fr;
  }

  .pay-types {
    flex-direction: column;
  }
}
</style>
