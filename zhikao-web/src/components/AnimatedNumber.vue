<template>
  <div class="animated-number" :class="{ 'is-pulse': pulse }">
    {{ display }}
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

/**
 * 数字滚动（动效清单第 5 条）。
 *
 * 三个刻意的处理：
 * 1. 用 requestAnimationFrame 而非 setInterval —— 与浏览器刷新节奏对齐，不丢帧；
 * 2. **尊重 prefers-reduced-motion** —— 用户明确要求减弱动效时直接跳到终值，
 *    不做「假装在动」的妥协；
 * 3. 小数位跟随目标值推断 —— 整数不显示 .00，避免统计数字出现无意义的小数。
 */
const props = defineProps({
  value: { type: [Number, String], default: 0 },
  duration: { type: Number, default: 600 },
  /** 小数点后保留位数；不传则按目标值自动推断 */
  precision: { type: Number, default: null },
  suffix: { type: String, default: '' },
  prefix: { type: String, default: '' }
})

const current = ref(0)
const pulse = ref(false)
let rafId = null

const target = computed(() => {
  const num = Number(props.value)
  return Number.isFinite(num) ? num : 0
})

const decimals = computed(() => {
  if (props.precision !== null) return props.precision
  return Number.isInteger(target.value) ? 0 : 2
})

const display = computed(
  () => `${props.prefix}${current.value.toFixed(decimals.value)}${props.suffix}`
)

function prefersReducedMotion() {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

function animate(from, to) {
  if (rafId) cancelAnimationFrame(rafId)
  if (prefersReducedMotion() || props.duration <= 0 || from === to) {
    current.value = to
    return
  }
  const start = performance.now()
  const step = (now) => {
    const progress = Math.min((now - start) / props.duration, 1)
    // 缓出：起步快、收尾稳，比线性更符合「数字落定」的直觉
    const eased = 1 - Math.pow(1 - progress, 3)
    current.value = from + (to - from) * eased
    if (progress < 1) {
      rafId = requestAnimationFrame(step)
    } else {
      current.value = to
      // 落定后轻微脉冲一次，给「数值已更新」一个视觉确认
      pulse.value = true
      setTimeout(() => {
        pulse.value = false
      }, 220)
    }
  }
  rafId = requestAnimationFrame(step)
}

watch(
  target,
  (to, from) => animate(from ?? 0, to),
  { immediate: true }
)

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<style scoped>
.animated-number {
  font-variant-numeric: tabular-nums; /* 等宽数字：滚动时宽度不跳动 */
  transition: transform var(--zk-duration-fast) var(--zk-ease);
  transform-origin: left center;
}

.animated-number.is-pulse {
  transform: scale(1.06);
}
</style>
