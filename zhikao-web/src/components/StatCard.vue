<template>
  <!--
    这里刻意不加 zk-hover-lift：指标卡是「看」的，不是「点」的。
    整排卡片在鼠标划过时一起抬升，是典型的动效滥用——
    悬浮抬升只应该出现在真正可点击的元素上（如首页待办条目）。
  -->
  <div class="stat-card" :style="{ '--card-accent': accent }">
    <div class="stat-card__icon">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-card__body">
      <div class="stat-card__label">{{ label }}</div>
      <div class="stat-card__value">
        <AnimatedNumber :value="value" :suffix="unit" />
      </div>
      <div v-if="hint" class="stat-card__hint zk-ellipsis" :title="hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AnimatedNumber from './AnimatedNumber.vue'

/**
 * 指标卡片：图标 + 标题 + 滚动数值 + 说明。
 *
 * accent 由后端下发的语义色（primary/success/warning/danger）映射到 Token，
 * 这样「什么数字该用什么颜色」由数据决定，组件本身不硬编码业务语义。
 */
const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  unit: { type: String, default: '' },
  hint: { type: String, default: '' },
  color: { type: String, default: 'primary' },
  icon: { type: String, default: 'DataLine' }
})

const TOKEN_MAP = {
  primary: 'var(--zk-primary)',
  success: 'var(--zk-success)',
  warning: 'var(--zk-warning)',
  danger: 'var(--zk-danger)',
  info: 'var(--zk-info)'
}

const accent = computed(() => TOKEN_MAP[props.color] || TOKEN_MAP.primary)
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: var(--zk-space-4);
  padding: var(--zk-space-4) var(--zk-space-5);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-sm);
  overflow: hidden;
  position: relative;
}

/* 左侧色条：用 transform 而非 width 做入场，不触发重排 */
.stat-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--card-accent);
  transform-origin: top;
  animation: zk-bar-grow var(--zk-duration-slow) var(--zk-ease) both;
}

/* 图标底色的浓度从 12% 降到 8%：一排四个高饱和色块是廉价感的主要来源，
   降到能看出语义色、又不喧宾夺主的程度即可 */
.stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: var(--zk-radius-md);
  color: var(--card-accent);
  background: color-mix(in srgb, var(--card-accent) 8%, transparent);
}

.stat-card__body {
  min-width: 0;
  flex: 1;
}

.stat-card__label {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-secondary);
}

.stat-card__value {
  font-size: var(--zk-font-xl);
  font-weight: 600;
  line-height: 1.3;
  color: var(--zk-text-primary);
}

.stat-card__hint {
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}
</style>
