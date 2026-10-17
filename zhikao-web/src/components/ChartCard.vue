<template>
  <section class="chart-card">
    <header class="chart-card__header">
      <div>
        <h3 class="chart-card__title">{{ title }}</h3>
        <p v-if="description" class="chart-card__desc">{{ description }}</p>
      </div>
      <div class="chart-card__extra">
        <slot name="extra" />
      </div>
    </header>

    <div class="chart-card__body">
      <SkeletonBlock v-if="loading" :rows="4" />
      <EmptyState
        v-else-if="isEmpty"
        :title="emptyTitle"
        :description="emptyDescription"
      />
      <div v-else ref="chartRef" class="chart-card__canvas" :style="{ height: `${height}px` }" />
    </div>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import EmptyState from './EmptyState.vue'
import SkeletonBlock from './SkeletonBlock.vue'

/**
 * ECharts 封装。
 *
 * 重复踩过的三个坑都在这里一次性解决：
 * 1. **自适应**：容器尺寸变化（侧边栏折叠、窗口缩放）必须调 resize，
 *    否则图表宽度停留在初始值。用 ResizeObserver 而不是 window.resize——
 *    因为容器变化不一定伴随窗口变化（侧边栏折叠就是典型）。
 * 2. **实例复用**：option 变化时用 setOption 而不是重建实例，避免内存泄漏。
 * 3. **空数据占位**：全 0 的图看起来像 bug，直接用空态替代。
 *
 * 二期的关键优化：**ECharts 改为动态加载**。
 * 首页是最先渲染的页面，而首页就用到本组件；如果静态 import echarts，
 * 那 500KB+ 的图表库会进入首屏的依赖图，把首次渲染拖慢。
 * 改成在 onMounted 里 import() 之后，图表库变成独立的异步 chunk：
 * 页面骨架先出来，图表随后补上（配合骨架屏观感自然），首屏 JS 显著变轻。
 */
const props = defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  option: { type: Object, default: () => ({}) },
  height: { type: Number, default: 300 },
  loading: { type: Boolean, default: false },
  /** 数据为空时显示占位而非空图 */
  isEmpty: { type: Boolean, default: false },
  emptyTitle: { type: String, default: '暂无数据' },
  emptyDescription: { type: String, default: '' }
})

const chartRef = ref(null)
let chart = null
let observer = null
let echartsLib = null
let base = {}

async function ensureLib() {
  if (!echartsLib) {
    const mod = await import('@/utils/echarts')
    echartsLib = mod.default
    base = mod.baseOption()
  }
  return echartsLib
}

async function render() {
  if (!chartRef.value || props.isEmpty || props.loading) return
  const lib = await ensureLib()
  if (!chartRef.value) return // 等待期间组件可能已卸载
  if (!chart) {
    chart = lib.init(chartRef.value)
  }
  chart.setOption({ ...base, ...props.option }, true)
}

function resize() {
  if (chart) chart.resize()
}

onMounted(async () => {
  await nextTick()
  await render()
  if (typeof ResizeObserver !== 'undefined' && chartRef.value) {
    observer = new ResizeObserver(() => resize())
    observer.observe(chartRef.value)
  } else {
    window.addEventListener('resize', resize)
  }
})

watch(
  () => props.option,
  async () => {
    await nextTick()
    await render()
  },
  { deep: true }
)

watch(
  () => [props.loading, props.isEmpty],
  async () => {
    await nextTick()
    await render()
  }
)

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  else window.removeEventListener('resize', resize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.chart-card {
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-sm);
  padding: var(--zk-space-4) var(--zk-space-5);
}

.chart-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zk-space-3);
  margin-bottom: var(--zk-space-3);
}

.chart-card__title {
  font-size: var(--zk-font-md);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.chart-card__desc {
  margin-top: 2px;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.chart-card__body {
  position: relative;
}

.chart-card__canvas {
  width: 100%;
}
</style>
