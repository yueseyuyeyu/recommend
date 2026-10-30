<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const props = defineProps({
  /** 商品对象，推荐接口返回的 RecommendVO 与商品接口返回的 ProductVO 都兼容 */
  item: { type: Object, required: true },
  /** 当前用户是否已收藏，由父组件统一维护，避免每张卡片各查一次 */
  favorited: { type: Boolean, default: false },
  /** 是否展示推荐理由 */
  showReason: { type: Boolean, default: true }
})

const emit = defineEmits(['open', 'toggle-favorite'])

const userStore = useUserStore()

/* ---------- 悬停 1 秒显示商品信息 ----------
 * 用定时器而不是 CSS transition-delay：
 * 后者一旦触发就无法在鼠标离开时可靠取消，快速划过一排卡片会闪成一片。
 */
const infoVisible = ref(false)
let hoverTimer = null

function handleMouseEnter() {
  clearTimeout(hoverTimer)
  hoverTimer = setTimeout(() => {
    infoVisible.value = true
  }, 1000)
}

function handleMouseLeave() {
  clearTimeout(hoverTimer)
  infoVisible.value = false
}

onBeforeUnmount(() => clearTimeout(hoverTimer))

/* ---------- 收藏 ---------- */
function handleHeartClick(event) {
  // 阻止冒泡，否则会同时触发卡片跳转
  event.stopPropagation()
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能收藏商品')
    return
  }
  emit('toggle-favorite', props.item)
}

function handleOpen() {
  emit('open', props.item)
}
</script>

<template>
  <el-popover
    v-model:visible="infoVisible"
    trigger="manual"
    placement="bottom"
    :width="248"
    :show-arrow="false"
    popper-class="product-info-popper"
  >
    <template #reference>
      <div class="card" @mouseenter="handleMouseEnter" @mouseleave="handleMouseLeave" @click="handleOpen">
        <div class="cover">
          <el-image v-if="item.mainImage" :src="item.mainImage" fit="cover" class="cover-img" lazy>
            <template #error>
              <div class="image-placeholder">暂无图片</div>
            </template>
          </el-image>
          <div v-else class="image-placeholder">{{ (item.name || '商品').slice(0, 4) }}</div>

          <!-- 爱心：右下角，点击变红表示已收藏 -->
          <button
            class="heart"
            :class="{ 'is-on': favorited }"
            :title="favorited ? '取消收藏' : '收藏'"
            type="button"
            @click="handleHeartClick"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M12 20.7l-1.45-1.32C5.4 14.6 2 11.5 2 8.2 2 5.6 4.1 3.5 6.7 3.5c1.5 0 2.9.7 3.8 1.8.9-1.1 2.3-1.8 3.8-1.8 2.6 0 4.7 2.1 4.7 4.7 0 3.3-3.4 6.4-8.55 11.18L12 20.7z"
              />
            </svg>
          </button>

          <div v-if="showReason && item.reason" class="reason-badge">{{ item.reason }}</div>
        </div>

        <div class="body">
          <div class="name">{{ item.name }}</div>
          <div class="subtitle">{{ item.subtitle }}</div>
          <div class="row">
            <span class="price">￥{{ item.price }}</span>
            <span class="sales">销量 {{ item.sales ?? 0 }}</span>
          </div>
        </div>
      </div>
    </template>

    <!-- 悬停 1 秒后浮出的商品信息卡 -->
    <div class="info">
      <div class="info-name">{{ item.name }}</div>
      <div class="info-subtitle">{{ item.subtitle || '—' }}</div>
      <div class="info-row"><span>价格</span><b class="info-price">￥{{ item.price }}</b></div>
      <div class="info-row"><span>销量</span><b>{{ item.sales ?? 0 }}</b></div>
      <div v-if="item.reason" class="info-reason">{{ item.reason }}</div>
    </div>
  </el-popover>
</template>

<style scoped>
.card {
  cursor: pointer;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
  transition: box-shadow 0.2s, transform 0.2s;
}

.card:hover {
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.cover {
  position: relative;
  height: 220px;
  overflow: hidden;
  background: #f5f7fa;
}

.cover-img {
  width: 100%;
  height: 220px;
  display: block;
}

.image-placeholder {
  width: 100%;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
  background: #f5f7fa;
}

/* ---------- 爱心 ---------- */
.heart {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s, transform 0.15s;
}

.heart:hover {
  background: rgba(0, 0, 0, 0.55);
  transform: scale(1.1);
}

.heart svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: #fff;
  stroke-width: 2;
  stroke-linejoin: round;
  transition: fill 0.2s, stroke 0.2s;
}

/* 已收藏：变红 */
.heart.is-on svg {
  fill: #f56c6c;
  stroke: #f56c6c;
}

.reason-badge {
  position: absolute;
  left: 8px;
  top: 8px;
  max-width: 70%;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
  color: #e6531f;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.body {
  padding: 10px 12px 14px;
}

.name {
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 8px;
}

.price {
  color: #f56c6c;
  font-weight: 700;
  font-size: 17px;
}

.sales {
  font-size: 12px;
  color: #909399;
}

/* ---------- 悬停信息卡 ---------- */
.info-name {
  font-weight: 600;
  font-size: 14px;
  line-height: 1.4;
}

.info-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.info-row {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}

.info-price {
  color: #f56c6c;
}

.info-reason {
  margin-top: 10px;
  padding: 4px 8px;
  background: #f0f9eb;
  color: #67c23a;
  font-size: 12px;
  border-radius: 4px;
}
</style>
