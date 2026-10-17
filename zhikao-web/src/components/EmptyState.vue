<template>
  <div class="empty-state" role="status" :aria-label="title">
    <!-- 插画按语义分两套：error 用告警三角，用户一眼能区分「没有数据」与「出了问题」；
         色彩只作辅助，形状本身也承载语义（无障碍约定：颜色不能是唯一指示） -->
    <svg v-if="variant === 'error'" class="empty-state__art" viewBox="0 0 160 120" fill="none" aria-hidden="true">
      <path d="M80 26 L126 94 H34 Z" fill="var(--zk-danger-light)" stroke="var(--zk-danger)" stroke-opacity="0.45" stroke-width="2" stroke-linejoin="round" />
      <rect x="77" y="50" width="6" height="24" rx="3" fill="var(--zk-danger)" />
      <circle cx="80" cy="82" r="3.5" fill="var(--zk-danger)" />
    </svg>
    <svg v-else class="empty-state__art" viewBox="0 0 160 120" fill="none" aria-hidden="true">
      <!-- 内联 SVG 插画：不引图片资源，随主题色变化，且体积为 0 -->
      <rect x="24" y="30" width="112" height="72" rx="8" fill="var(--zk-primary-lighter)" />
      <rect x="24" y="30" width="112" height="72" rx="8" stroke="var(--zk-primary)" stroke-opacity="0.25" />
      <rect x="38" y="46" width="60" height="6" rx="3" fill="var(--zk-primary)" fill-opacity="0.28" />
      <rect x="38" y="60" width="84" height="6" rx="3" fill="var(--zk-primary)" fill-opacity="0.18" />
      <rect x="38" y="74" width="46" height="6" rx="3" fill="var(--zk-primary)" fill-opacity="0.18" />
      <circle cx="124" cy="84" r="16" fill="var(--zk-bg-card)" stroke="var(--zk-primary)" stroke-opacity="0.4" />
      <path d="M118 84l4 4 8-8" stroke="var(--zk-primary)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
    </svg>

    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>

    <slot>
      <el-button v-if="actionText" :type="variant === 'error' ? 'primary' : 'primary'" :plain="variant !== 'error'" @click="$emit('action')">
        {{ actionText }}
      </el-button>
    </slot>
  </div>
</template>

<script setup>
/**
 * 空状态 / 错误状态。
 * 一期「空白表格」是最强的「学生作业感」来源——空态 + 引导按钮
 * 是观感提升里投入产出比最高的一处。
 * 四期扩展 error 变体：接口失败不再是「一条 toast + 一张空表」，
 * 数据区要能自我说明「出问题了，可以重试」。
 */
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' },
  /** empty | error —— error 时插画与按钮样式随语义切换 */
  variant: { type: String, default: 'empty' }
})

defineEmits(['action'])
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--zk-space-6) var(--zk-space-4);
  text-align: center;
  animation: zk-fade-in var(--zk-duration-base) var(--zk-ease) both;
}

.empty-state__art {
  width: 160px;
  height: 120px;
  margin-bottom: var(--zk-space-3);
}

.empty-state__title {
  font-size: var(--zk-font-md);
  font-weight: 500;
  color: var(--zk-text-regular);
}

.empty-state__desc {
  margin-top: var(--zk-space-1);
  margin-bottom: var(--zk-space-4);
  font-size: var(--zk-font-sm);
  color: var(--zk-text-placeholder);
  max-width: 420px;
}
</style>
