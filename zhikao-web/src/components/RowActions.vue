<template>
  <div class="zk-actions">
    <el-button
      v-for="action in flat"
      :key="action.key"
      text
      :type="action.danger ? 'danger' : 'primary'"
      size="small"
      :disabled="isDisabled(action)"
      @click="run(action)"
    >
      {{ action.label }}
    </el-button>

    <el-dropdown
      v-if="folded.length"
      trigger="click"
      popper-class="zk-actions__popper"
      @command="runByKey"
    >
      <el-button text type="primary" size="small" class="zk-actions__more">
        {{ moreLabel }}
        <el-icon :size="12"><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="action in folded"
            :key="action.key"
            :command="action.key"
            :disabled="isDisabled(action)"
            :class="{ 'is-danger': action.danger }"
          >
            {{ action.label }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <span v-if="!visibleActions.length" class="zk-actions__none">—</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

/**
 * 表格行内操作列。
 *
 * 解决的问题很具体：所有页面的操作列都写死 `width="220"`，但里面塞的按钮从 1 到 5 个都有。
 * 1 个按钮的列右边空一半，5 个按钮的列必然换行错位——同一张表里两列宽窄全靠猜。
 *
 * 规则：**前 flat 个高频动作平铺，其余收进「更多」**。
 * 平铺的两个位置留给「真正每天点的」（通常是查看 / 编辑），删除一类的破坏性动作
 * 一律收进菜单并标红——放在外面并不会让删除更快，只会让它更容易被误点。
 *
 * actions 每项：{ key, label, run, danger?, disabled?, show? }
 * disabled / show 可以是布尔，也可以是接受 row 的函数。
 */
const props = defineProps({
  actions: { type: Array, default: () => [] },
  row: { type: Object, default: () => ({}) },
  flat: { type: Number, default: 2 },
  moreLabel: { type: String, default: '更多' }
})

function shown(action) {
  return typeof action.show === 'function' ? action.show(props.row) : action.show !== false
}

const visibleActions = computed(() => props.actions.filter(shown))
const flat = computed(() => visibleActions.value.slice(0, props.flat))
const folded = computed(() => visibleActions.value.slice(props.flat))

function isDisabled(action) {
  return typeof action.disabled === 'function' ? Boolean(action.disabled(props.row)) : Boolean(action.disabled)
}

function run(action) {
  if (isDisabled(action)) return
  action.run?.(props.row)
}

function runByKey(key) {
  const action = folded.value.find((item) => item.key === key)
  if (action) run(action)
}
</script>

<style scoped>
.zk-actions {
  display: flex;
  align-items: center;
  gap: var(--zk-space-1);
  /* 操作列里的按钮挨得很近，默认 12px 的相邻间距会让「两个动作」看起来像一个词 */
  white-space: nowrap;
}

.zk-actions__more :deep(.el-icon) {
  margin-left: 2px;
}

.zk-actions__none {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-placeholder);
}
</style>

<style>
/* 下拉面板被 teleport 到 body 上，页面级 scoped 样式够不到，所以单独开一块 */
.zk-actions__popper .el-dropdown-menu__item.is-danger {
  color: var(--zk-danger);
}

.zk-actions__popper .el-dropdown-menu__item.is-danger:not(.is-disabled):hover {
  background: var(--zk-danger-light);
  color: var(--zk-danger);
}
</style>
