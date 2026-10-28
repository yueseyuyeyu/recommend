<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearCart, getCart, getCartTotal, removeCart, updateCart } from '../api'

const router = useRouter()

const items = ref([])
const total = ref(0)
const loading = ref(false)

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

function handleCheckout() {
  if (total.value <= 0) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  router.push('/orders')
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
</style>
