<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  clearCart,
  createOrder,
  getAddresses,
  getCart,
  getCartTotal,
  removeCart,
  updateCart
} from '../api'

const router = useRouter()

const items = ref([])
const total = ref(0)
const loading = ref(false)

/* ================= 结算弹窗 ================= */
const checkoutVisible = ref(false)
const submitting = ref(false)
const addresses = ref([])
const addressLoading = ref(false)

const checkoutForm = reactive({
  addressId: null,
  payType: 1
})

/** 支付方式，与后端 PayType 枚举保持一致 */
const PAY_TYPES = [
  { value: 1, label: '支付宝', desc: '推荐有支付宝账号的用户', color: '#1677ff' },
  { value: 2, label: '微信支付', desc: '推荐已开通微信支付的用户', color: '#07c160' },
  { value: 3, label: '银行卡', desc: '支持储蓄卡与信用卡', color: '#e6531f' }
]

/** 只结算已勾选的条目 */
const checkedItems = computed(() => items.value.filter((row) => row.checked === 1))

const checkoutTotal = computed(() =>
  checkedItems.value.reduce((sum, row) => {
    const subtotal = Number(row.subtotal)
    if (!Number.isNaN(subtotal)) return sum + subtotal
    return sum + Number(row.price) * Number(row.quantity)
  }, 0)
)

const checkoutCount = computed(() => checkedItems.value.length)

async function loadTotal() {
  try {
    total.value = Number(await getCartTotal()) || 0
  } catch {
    total.value = 0
  }
}

async function loadCart() {
  loading.value = true
  try {
    items.value = (await getCart()) || []
    await loadTotal()
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function handleQuantityChange(row) {
  if (row.quantity < 1) row.quantity = 1
  try {
    await updateCart(row.id, { quantity: row.quantity, checked: row.checked })
    await loadCart()
  } catch {
    // 修改失败时重新拉取，回滚界面上的临时值
    await loadCart()
  }
}

async function handleCheckedChange(row, checked) {
  try {
    await updateCart(row.id, { quantity: row.quantity, checked: checked ? 1 : 0 })
    await loadCart()
  } catch {
    await loadCart()
  }
}

async function handleRemove(row) {
  try {
    await removeCart(row.id)
    ElMessage.success('已删除')
    await loadCart()
  } catch {
    // 错误提示由拦截器处理
  }
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearCart()
    ElMessage.success('购物车已清空')
    await loadCart()
  } catch {
    // 错误提示由拦截器处理
  }
}

/** 打开结算弹窗：拉地址簿并默认选中默认地址 */
async function handleCheckout() {
  if (total.value <= 0 || checkedItems.value.length === 0) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }

  checkoutForm.payType = 1
  checkoutVisible.value = true
  addressLoading.value = true
  try {
    addresses.value = (await getAddresses()) || []
    const preferred = addresses.value.find((row) => row.isDefault === 1) || addresses.value[0]
    checkoutForm.addressId = preferred ? preferred.id : null
  } catch {
    addresses.value = []
    checkoutForm.addressId = null
  } finally {
    addressLoading.value = false
  }
}

/**
 * 提交订单：此时只生成「待支付」订单，跳转到收银台完成支付，
 * 支付成功后才算走完整个下单流程。
 */
async function submitCheckout() {
  if (!checkoutForm.addressId) {
    ElMessage.warning('请选择收货地址，没有地址可先去个人中心新增')
    return
  }
  if (!checkoutForm.payType) {
    ElMessage.warning('请选择支付方式')
    return
  }

  submitting.value = true
  try {
    const orderNo = await createOrder({
      addressId: checkoutForm.addressId,
      payType: checkoutForm.payType
    })
    checkoutVisible.value = false
    ElMessage.success('订单已创建，请完成支付')
    await loadCart()
    router.push(`/pay/${orderNo}`)
  } catch {
    // 错误提示由拦截器处理
  } finally {
    submitting.value = false
  }
}

function goManageAddress() {
  checkoutVisible.value = false
  router.push('/profile')
}

onMounted(loadCart)
</script>

<template>
  <div class="page">
    <h2 class="page-title">购物车</h2>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="items" empty-text="购物车还是空的">
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="product-cell">
              <div class="thumb">
                <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" class="thumb-img">
                  <template #error>
                    <div class="image-placeholder">暂无图片</div>
                  </template>
                </el-image>
                <div v-else class="image-placeholder">暂无图片</div>
              </div>
              <div>
                <div>{{ row.productName }}</div>
                <div class="muted">库存 {{ row.stock }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }">
            <span class="price">￥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :max="row.stock || 1"
              size="small"
              @change="handleQuantityChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="120">
          <template #default="{ row }">
            <span class="price">￥{{ row.subtotal }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结算" width="90">
          <template #default="{ row }">
            <el-checkbox
              :model-value="row.checked === 1"
              @change="(val) => handleCheckedChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="footer">
        <el-button @click="handleClear">清空购物车</el-button>
        <div class="total-area">
          <span class="muted">已勾选合计</span>
          <span class="price total">￥{{ total }}</span>
          <el-button type="primary" @click="handleCheckout">去结算</el-button>
        </div>
      </div>
    </el-card>

    <!-- ============ 结算弹窗 ============ -->
    <el-dialog v-model="checkoutVisible" title="确认订单" width="720px" top="6vh">
      <div class="checkout-body">
        <!-- 收货地址 -->
        <div class="checkout-section">
          <div class="section-title">
            收货地址
            <el-button link type="primary" size="small" @click="goManageAddress">管理地址</el-button>
          </div>

          <div v-loading="addressLoading" class="address-area">
            <el-radio-group v-if="addresses.length" v-model="checkoutForm.addressId" class="address-radios">
              <el-radio v-for="row in addresses" :key="row.id" :value="row.id" class="address-radio">
                <span class="addr-line">
                  <b>{{ row.receiverName }}</b>
                  <span class="muted">{{ row.receiverPhone }}</span>
                  <el-tag v-if="row.isDefault === 1" size="small" type="danger">默认</el-tag>
                </span>
                <span class="addr-detail">{{ row.detailAddress }}</span>
              </el-radio>
            </el-radio-group>
            <el-empty v-else description="还没有收货地址" :image-size="60">
              <el-button type="primary" size="small" @click="goManageAddress">去新增地址</el-button>
            </el-empty>
          </div>
        </div>

        <!-- 商品清单 -->
        <div class="checkout-section">
          <div class="section-title">
            商品清单
            <span class="muted">共 {{ checkoutCount }} 项</span>
          </div>
          <div class="goods-list">
            <div v-for="row in checkedItems" :key="row.id" class="goods-item">
              <div class="thumb">
                <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" class="thumb-img">
                  <template #error>
                    <div class="image-placeholder">图</div>
                  </template>
                </el-image>
                <div v-else class="image-placeholder">图</div>
              </div>
              <div class="goods-main">
                <div class="goods-name">{{ row.productName }}</div>
                <div class="muted">￥{{ row.price }} × {{ row.quantity }}</div>
              </div>
              <div class="goods-subtotal">￥{{ row.subtotal ?? (Number(row.price) * Number(row.quantity)).toFixed(2) }}</div>
            </div>
          </div>
        </div>

        <!-- 支付方式 -->
        <div class="checkout-section">
          <div class="section-title">支付方式</div>
          <div class="pay-types">
            <div
              v-for="type in PAY_TYPES"
              :key="type.value"
              class="pay-type"
              :class="{ 'is-active': checkoutForm.payType === type.value }"
              @click="checkoutForm.payType = type.value"
            >
              <div class="pay-name" :style="{ color: type.color }">{{ type.label }}</div>
              <div class="pay-desc">{{ type.desc }}</div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="checkout-footer">
          <div class="footer-total">
            <span class="muted">应付金额</span>
            <span class="price total">￥{{ checkoutTotal.toFixed(2) }}</span>
          </div>
          <div>
            <el-button @click="checkoutVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submitCheckout">
              提交订单
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.product-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.thumb {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
}

.thumb-img {
  width: 64px;
  height: 64px;
  display: block;
}

.thumb .image-placeholder {
  min-height: 64px;
  font-size: 12px;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.total-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.total {
  font-size: 20px;
}

/* ================= 结算弹窗 ================= */
.checkout-body {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}

.checkout-section + .checkout-section {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #f5f5f5;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.address-area {
  min-height: 40px;
}

.address-radios {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

/* 单选项整行卡片化 */
.address-radio {
  height: auto;
  align-items: flex-start;
  margin-right: 0;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
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

/* 商品清单 */
.goods-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.goods-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.goods-main {
  flex: 1;
  min-width: 0;
}

.goods-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-subtotal {
  flex: 0 0 auto;
  font-size: 14px;
  font-weight: 600;
  color: #f56c6c;
}

/* 支付方式 */
.pay-types {
  display: flex;
  gap: 12px;
}

.pay-type {
  flex: 1;
  padding: 10px 12px;
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
  font-size: 14px;
  font-weight: 600;
}

.pay-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.checkout-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-total {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

@media (max-width: 760px) {
  .pay-types {
    flex-direction: column;
  }
}
</style>
