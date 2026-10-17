<template>
  <div class="skeleton" :aria-busy="true" aria-live="polite">
    <div v-for="i in rows" :key="i" class="skeleton__row" :style="{ animationDelay: `${i * 60}ms` }">
      <div class="skeleton__bar" :style="{ width: barWidth(i) }" />
    </div>
  </div>
</template>

<script setup>
/**
 * 骨架屏（动效清单第 7 条）。
 *
 * 替代「全屏 loading 遮罩」：遮罩会让页面短暂完全空白，
 * 骨架屏则提前把布局画出来，用户感知上「内容已经在路上了」。
 * 错落的宽度与延迟让扫光有节奏，而不是整齐划一的假图案。
 */
const props = defineProps({
  rows: { type: Number, default: 5 }
})

/** 让每行宽度略有差异，看起来更像真实内容 */
function barWidth(index) {
  const widths = ['100%', '88%', '94%', '76%', '92%', '84%']
  return widths[index % widths.length]
}
</script>

<style scoped>
.skeleton {
  padding: var(--zk-space-2) 0;
}

.skeleton__row {
  margin-bottom: var(--zk-space-3);
  animation: zk-fade-in var(--zk-duration-base) var(--zk-ease) both;
}

.skeleton__bar {
  height: 14px;
  border-radius: var(--zk-radius-sm);
  background: linear-gradient(
    90deg,
    var(--zk-border-light) 25%,
    var(--zk-border-strong) 37%,
    var(--zk-border-light) 63%
  );
  background-size: 200% 100%;
  animation: zk-shimmer 1.4s ease-in-out infinite;
}
</style>
