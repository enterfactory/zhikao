<template>
  <div v-show="visible" class="route-progress" :class="{ 'is-done': done }" aria-hidden="true" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 顶部路由进度条（动效清单第 13 条）。
 *
 * 自研而非引入 nprogress：需求只有「一条 2px 的进度条」，
 * 引一个库进来只为了这个，是典型的依赖膨胀。
 *
 * 实现要点：
 * - 只动 transform: scaleX，不动 width —— 不触发重排；
 * - 用 router 的 beforeEach/afterEach 钩子驱动，与已有守卫共存（Vue Router 支持多个守卫）；
 * - 离开路由时先推到 100% 再淡出，避免「进度条跳到一半就消失」的突兀感。
 */
const visible = ref(false)
const done = ref(false)
const router = useRouter()

let finishTimer = null
let hideTimer = null

function start() {
  clearTimeout(finishTimer)
  clearTimeout(hideTimer)
  done.value = false
  visible.value = true
}

function finish() {
  if (!visible.value) return
  done.value = true
  // 推满后再淡出：让「已完成」这件事被看见
  finishTimer = setTimeout(() => {
    visible.value = false
    done.value = false
  }, 260)
}

// 用返回值注销守卫，避免组件卸载后残留监听
const removeBefore = router.beforeEach((to, from, next) => {
  if (to.path !== from.path) start()
  next()
})

const removeAfter = router.afterEach(() => finish())
const removeError = router.onError(() => finish())

onMounted(() => {
  // 首次进入也走一次，保证刷新页面时有反馈
  start()
  finish()
})

onBeforeUnmount(() => {
  removeBefore()
  removeAfter()
  removeError()
  clearTimeout(finishTimer)
  clearTimeout(hideTimer)
})
</script>

<style scoped>
.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 3000;
  background: var(--zk-primary);
  transform-origin: left center;
  transform: scaleX(0);
  animation: zk-progress-grow 1.4s var(--zk-ease) forwards;
  transition: opacity var(--zk-duration-base) var(--zk-ease);
  pointer-events: none;
}

.route-progress.is-done {
  transform: scaleX(1);
  animation: none;
  opacity: 0;
}
</style>
