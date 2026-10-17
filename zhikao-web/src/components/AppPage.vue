<template>
  <div class="app-page">
    <!-- 页头：标题 + 描述 + 操作区。面包屑由布局层的顶部工具栏统一承载，避免两处重复 -->
    <header class="app-page__header">
      <div class="app-page__heading">
        <h2 class="app-page__title">{{ title }}</h2>
        <p v-if="description" class="app-page__desc">{{ description }}</p>
      </div>
      <div class="app-page__actions">
        <slot name="actions" />
      </div>
    </header>

    <!-- 筛选区：与内容区分离，视觉上更清楚「这是查询条件」 -->
    <div v-if="$slots.filter" class="app-page__filter">
      <slot name="filter" />
    </div>

    <div class="app-page__body">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 页面骨架：统一页头 / 筛选区 / 内容区的间距与层次。
 *
 * 一期每个页面各写一遍 `page-header` 的 DOM 与样式，导致字号、间距、按钮位置
 * 各不相同。抽成组件后「页头长什么样」只有一处定义，新页面只要填插槽。
 */
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.app-page {
  display: flex;
  flex-direction: column;
  gap: var(--zk-space-4);
}

.app-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zk-space-4);
  flex-wrap: wrap;
}

.app-page__title {
  font-size: var(--zk-font-lg);
  font-weight: 600;
  color: var(--zk-text-primary);
  line-height: 1.4;
}

.app-page__desc {
  margin-top: var(--zk-space-1);
  font-size: var(--zk-font-sm);
  color: var(--zk-text-secondary);
  max-width: 720px;
}

.app-page__actions {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  flex-wrap: wrap;
}

.app-page__filter {
  padding: var(--zk-space-4);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-sm);
}

/* 内容区不做入场动画（三期修正）。
   原来这里挂了 zk-slide-up，和路由过渡叠加后每次切页都有「整页重新加载」的观感。
   路由切换本身已有极短的淡入，页面内部再动一次是重复表达。 */
.app-page__body {
  min-width: 0;
}
</style>
