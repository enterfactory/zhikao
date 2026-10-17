<template>
  <RouteProgress />
  <router-view />
</template>

<script setup>
import RouteProgress from '@/components/RouteProgress.vue'

/**
 * 应用根组件。
 * 路由进度条放在最外层：它是全局的，不应该随布局或页面切换被卸载重建。
 */
</script>
