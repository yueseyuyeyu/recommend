<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHomeRecommend, getHotRecommend, reportBehavior } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const hotList = ref([])
const personalList = ref([])
const loadingHot = ref(false)
const loadingPersonal = ref(false)

async function loadHot() {
  loadingHot.value = true
  try {
    hotList.value = (await getHotRecommend(10)) || []
  } catch {
    hotList.value = []
  } finally {
    loadingHot.value = false
  }
}

async function loadPersonal() {
  loadingPersonal.value = true
  try {
    personalList.value = (await getHomeRecommend(10)) || []
  } catch {
    personalList.value = []
  } finally {
    loadingPersonal.value = false
  }
}

function openProduct(item) {
  // 埋点失败不阻断跳转
  if (userStore.isLoggedIn) {
    reportBehavior({ productId: item.id, behaviorType: 'CLICK' }).catch(() => {})
  }
  router.push(`/products/${item.id}`)
}

onMounted(() => {
  loadHot()
  if (userStore.isLoggedIn) loadPersonal()
})
</script>

<template>
  <div class="page">
    <h2 class="page-title">首页</h2>

    <el-alert
      v-if="!userStore.isLoggedIn"
      type="info"
      show-icon
      :closable="false"
      class="tip"
    >
      登录后可根据你的行为生成个性化推荐。
      <el-button link type="primary" @click="router.push('/login')">去登录</el-button>
    </el-alert>

    <template v-if="userStore.isLoggedIn">
      <h3 class="section-title">为你推荐</h3>
      <div v-loading="loadingPersonal" class="card-grid">
        <el-card
          v-for="item in personalList"
          :key="item.id"
          class="product-card"
          shadow="hover"
          @click="openProduct(item)"
        >
          <div class="cover">
            <el-image v-if="item.mainImage" :src="item.mainImage" fit="cover" class="cover-img">
              <template #error>
                <div class="image-placeholder">暂无图片</div>
              </template>
            </el-image>
            <div v-else class="image-placeholder">暂无图片</div>
          </div>
          <div class="name">{{ item.name }}</div>
          <div class="muted">{{ item.subtitle }}</div>
          <div class="row">
            <span class="price">￥{{ item.price }}</span>
            <span class="muted">销量 {{ item.sales }}</span>
          </div>
          <div v-if="item.reason" class="reason">{{ item.reason }}</div>
        </el-card>
      </div>
      <el-empty v-if="!loadingPersonal && personalList.length === 0" description="暂无个性化推荐" />
    </template>

    <h3 class="section-title">热门推荐</h3>
    <div v-loading="loadingHot" class="card-grid">
      <el-card
        v-for="item in hotList"
        :key="item.id"
        class="product-card"
        shadow="hover"
        @click="openProduct(item)"
      >
        <div class="cover">
          <el-image v-if="item.mainImage" :src="item.mainImage" fit="cover" class="cover-img">
            <template #error>
              <div class="image-placeholder">暂无图片</div>
            </template>
          </el-image>
          <div v-else class="image-placeholder">暂无图片</div>
        </div>
        <div class="name">{{ item.name }}</div>
        <div class="muted">{{ item.subtitle }}</div>
        <div class="row">
          <span class="price">￥{{ item.price }}</span>
          <span class="muted">销量 {{ item.sales }}</span>
        </div>
        <div v-if="item.reason" class="reason">{{ item.reason }}</div>
      </el-card>
    </div>
    <el-empty v-if="!loadingHot && hotList.length === 0" description="暂无热门商品" />
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 8px;
}

.product-card {
  cursor: pointer;
}

.cover {
  height: 160px;
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 160px;
  display: block;
}

.name {
  margin-top: 8px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.reason {
  margin-top: 8px;
  padding: 4px 8px;
  background: #f0f9eb;
  color: #67c23a;
  font-size: 12px;
  border-radius: 4px;
}
</style>
