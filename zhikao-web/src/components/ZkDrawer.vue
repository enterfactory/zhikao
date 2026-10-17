<template>
  <el-drawer
    v-model="visible"
    :title="title"
    :size="sizePx"
    direction="rtl"
    :close-on-click-modal="!loading"
    :before-close="handleBeforeClose"
    :destroy-on-close="destroyOnClose"
    class="zk-drawer"
    @closed="emit('closed')"
  >
    <div class="zk-drawer__inner">
      <header v-if="description || $slots.meta" class="zk-drawer__meta">
        <p v-if="description" class="zk-drawer__note">{{ description }}</p>
        <slot name="meta" />
      </header>

      <slot />
    </div>

    <template v-if="showFooter" #footer>
      <div class="zk-drawer__foot">
        <div class="zk-drawer__foot-extra">
          <slot name="foot-extra" />
        </div>
        <div class="zk-drawer__buttons">
          <slot name="footer">
            <el-button :disabled="loading" @click="onCancel">{{ cancelText }}</el-button>
            <el-button type="primary" :loading="loading" @click="emit('confirm')">
              {{ confirmText }}
            </el-button>
          </slot>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { confirmAction } from '@/utils/feedback'

/**
 * 详情与长内容的承载层。
 *
 * 为什么要有它而不是把弹窗加宽：详情类内容的信息量是「一行一个字段 + 一段富文本」，
 * 居中弹窗要加宽到 900px 才不挤，而那时它已经变成一个「没有上下文的独立页面」，
 * 用户却仍然以为这是个可以随手点掉的对话框。右侧抽屉从屏幕边缘推出，
 * 宽度天然受视口约束，左边还能保留列表可见——看完关掉还在原来那一行，不用重新找。
 *
 * 用在：整卷预览、文档解析详情、题目详情、字典项管理这类「看」为主的场景。
 * 短表单（≤4 个字段）仍然用 ZkDialog，别为了统一而把小表单也做成抽屉。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  /** md 常规详情 / lg 富文本与表格 / xl 整卷预览 */
  size: { type: String, default: 'lg' },
  loading: { type: Boolean, default: false },
  dirty: { type: Boolean, default: false },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '关闭' },
  showFooter: { type: Boolean, default: false },
  destroyOnClose: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel', 'closed'])

const SIZES = { md: '560px', lg: '760px', xl: '960px' }

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const sizePx = computed(() => {
  const base = SIZES[props.size] || SIZES.lg
  // 窄屏上固定宽度会直接顶满视口，留 24px 边距让关闭按钮够得着
  return `min(${base}, calc(100vw - var(--zk-space-6)))`
})

async function handleBeforeClose(next) {
  if (props.loading) return
  if (props.dirty) {
    const ok = await confirmAction({
      message: '当前有未保存的改动，确定关闭吗？',
      title: '放弃改动',
      confirmText: '放弃',
      danger: true,
    })
    if (!ok) return
  }
  next()
}

async function onCancel() {
  await handleBeforeClose(() => {
    emit('cancel')
    visible.value = false
  })
}
</script>

<style>
.zk-drawer {
  --el-drawer-padding-primary: var(--zk-space-4);
  box-shadow: var(--zk-shadow-lg);
}

.zk-drawer .el-drawer__header {
  margin-bottom: 0;
  padding-bottom: var(--zk-space-3);
  border-bottom: 1px solid var(--zk-border-light);
}

.zk-drawer .el-drawer__title {
  font-size: var(--zk-font-base);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.zk-drawer .el-drawer__body {
  padding-top: var(--zk-space-4);
}

.zk-drawer .el-drawer__footer {
  padding-top: var(--zk-space-3);
  border-top: 1px solid var(--zk-border-light);
}

.zk-drawer__meta {
  margin-bottom: var(--zk-space-4);
  padding-bottom: var(--zk-space-3);
  border-bottom: 1px dashed var(--zk-border-light);
}

.zk-drawer__note {
  margin: 0;
  font-size: var(--zk-font-sm);
  line-height: 1.7;
  color: var(--zk-text-secondary);
}

.zk-drawer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-3);
}

.zk-drawer__foot-extra {
  min-width: 0;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.zk-drawer__buttons {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  margin-left: auto;
}
</style>
