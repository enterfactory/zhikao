<template>
  <component :is="shell" />
</template>

<script setup>
/**
 * 壳层调度器（四期新增）。
 *
 * 双壳不是两个应用：登录态、请求层、组件、Token 全部共享，
 * 只是在应用入口按「粗粒度身份」选择一套信息架构——
 * 学生走门户（顶导航、卡片流、答题沉浸页），教师/管理走控制台（侧栏、页签、表格密度）。
 *
 * 路由子树挂在同一个父级下，壳选择在这里发生；
 * 这样新增页面只需声明 meta.roles/perms/bare，不必关心自己出现在哪一壳。
 */
import { computed, defineAsyncComponent } from 'vue'
import { useUserStore } from '@/store/user'

const ConsoleLayout = defineAsyncComponent(() => import('@/layouts/ConsoleLayout.vue'))
const PortalLayout = defineAsyncComponent(() => import('@/layouts/PortalLayout.vue'))

const userStore = useUserStore()

const shell = computed(() => (userStore.role === 'student' ? PortalLayout : ConsoleLayout))
</script>
