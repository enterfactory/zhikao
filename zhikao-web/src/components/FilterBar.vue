<template>
  <div class="filter-bar" @keyup.enter="onEnter">
    <div class="filter-bar__fields">
      <slot />
    </div>

    <div class="filter-bar__actions">
      <slot name="extra" />
      <el-button v-if="showReset" :icon="RefreshRight" @click="onReset">重置</el-button>
      <el-button type="primary" :icon="Search" :loading="loading" @click="emit('search')">
        查询
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { RefreshRight, Search } from '@element-plus/icons-vue'

/**
 * 列表页筛选区。
 *
 * 之前每个页面各自复制一份 `.xxx-form :deep(.el-input){width:220px}`，
 * 一共复制了 4 份，另有 9 处行内 `style="width:160px"`。后果不是「不优雅」，
 * 而是实打实的错位：同一排控件宽度不齐、标签换行位置各不相同、
 * 有的页面回车能查、有的必须去点按钮。
 *
 * 这里定下三条就够用：
 * 1. 栅格 `auto-fill minmax(220px,1fr)`——控件永远等宽，窄屏自动折行，不需要任何行内宽度；
 * 2. 回车即查（日期选择器除外，它自己要吃掉回车）；
 * 3. 操作按钮永远在最右，顺序固定「重置 → 查询」。
 */
const props = defineProps({
  loading: { type: Boolean, default: false },
  showReset: { type: Boolean, default: true }
})

const emit = defineEmits(['search', 'reset'])

function onEnter(event) {
  // 日期区间面板的回车是「确认所选」，再触发一次查询就是重复请求
  if (event.target?.closest?.('.el-date-editor')) return
  emit('search')
}

/**
 * 重置只发事件、不顺手查询：调用方的 reset 通常自己就会重新取数
 * （usePagedQuery 的 reset 就是这样），这里再补一次 search 就是同一个接口打两遍。
 */
function onReset() {
  emit('reset')
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--zk-space-3) var(--zk-space-4);
  flex-wrap: wrap;
}

.filter-bar__fields {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--zk-space-3) var(--zk-space-4);
  flex: 1 1 480px;
  min-width: 0;
}

.filter-bar__actions {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  margin-left: auto;
}
</style>
