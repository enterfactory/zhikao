<template>
  <div class="zk-field">
    <label class="zk-field__label">{{ label }}</label>
    <div class="zk-field__control">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 筛选区里的一个字段：标签在上、控件在下，控件宽度由 FilterBar 的栅格决定。
 *
 * 为什么标签不用 Element 的 inline label：inline 表单的 label 宽度按内容自适应，
 * 「模块 / 操作人 / 结果」三种长度会把同一排的控件推成三种起点，这正是现在
 * 各页筛选区看起来歪的根本原因。标签固定占一行，控件就永远对齐。
 */
defineProps({
  label: { type: String, default: '' }
})
</script>

<style>
/* 非 scoped：插槽内容带着页面自己的 data-v 属性，scoped 选择器匹配不到，
   写在这里才能让「控件自动占满栅格」对所有页面生效。 */
.zk-field {
  display: flex;
  flex-direction: column;
  gap: var(--zk-space-1);
  min-width: 0;
}

.zk-field__label {
  font-size: var(--zk-font-xs);
  line-height: 1.4;
  color: var(--zk-text-secondary);
}

.zk-field__control {
  min-width: 0;
}

.zk-field__control .el-input,
.zk-field__control .el-select,
.zk-field__control .el-date-editor,
.zk-field__control .el-input-number,
.zk-field__control .el-cascader {
  width: 100%;
}

/* 日期区间默认很宽，栅格里会被挤成两行，这里让它按内容收缩 */
.zk-field__control .el-date-editor.el-input__wrapper {
  padding-left: var(--zk-space-2);
}
</style>
