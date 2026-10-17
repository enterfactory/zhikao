<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    align-center
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :before-close="handleBeforeClose"
    :destroy-on-close="destroyOnClose"
    class="zk-dialog"
    @closed="emit('closed')"
  >
    <p v-if="description" class="zk-dialog__note">{{ description }}</p>

    <slot />

    <template v-if="showFooter" #footer>
      <div class="zk-dialog__foot">
        <div class="zk-dialog__foot-extra">
          <slot name="foot-extra" />
        </div>
        <div class="zk-dialog__buttons">
          <slot name="footer">
            <el-button :disabled="loading" @click="onCancel">{{ cancelText }}</el-button>
            <el-button type="primary" :loading="loading" @click="emit('confirm')">
              {{ confirmText }}
            </el-button>
          </slot>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { confirmAction } from '@/utils/feedback'

/**
 * 全站弹窗唯一入口。
 *
 * 收口的三件事：
 * 1. **宽度只有三档**。之前 440/460/480/520/560/620/640/680 十个魔数并存，
 *    同一系统里两个弹窗摆在一起就能看出「不是一套东西」。档位按内容密度分：
 *    sm 放确认与 2~3 个字段，md 放常规表单，lg 放多列或带预览的表单。
 *    再宽的内容应该走 ZkDrawer，而不是把弹窗继续加宽。
 * 2. **页脚顺序固定**：取消在前、主操作在后且靠最右，异步进行中两个按钮都不可用——
 *    只禁用主按钮时，用户仍能点取消把请求甩在后台，回来就看到状态错乱。
 * 3. **关闭要过一道闸**：提交中不允许关闭（否则请求回来后往已卸载的表单里写值），
 *    有未保存改动时给一次挽留，而不是静默丢弃。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  /** 标题下的一句话说明：写「保存后会怎样」，不要重复标题 */
  description: { type: String, default: '' },
  size: { type: String, default: 'md' },
  /** 提交中：禁用页脚、禁止关闭 */
  loading: { type: Boolean, default: false },
  /** 表单是否有未保存改动；为真时关闭前弹一次挽留 */
  dirty: { type: Boolean, default: false },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  showFooter: { type: Boolean, default: true },
  destroyOnClose: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel', 'closed'])

const SIZES = { sm: '420px', md: '560px', lg: '720px' }

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const width = computed(() => SIZES[props.size] || SIZES.md)

async function handleBeforeClose(next) {
  if (props.loading) return
  if (props.dirty) {
    const ok = await confirmAction({
      message: '当前有未保存的改动，确定放弃吗？',
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
/* 非 scoped：这里要覆写的是 Element 自己渲染的 .el-dialog__* 结构，
   加 scoped 选择器带不上 data-v 属性，规则会静默不生效。 */
.zk-dialog {
  --el-dialog-padding-primary: var(--zk-space-4);
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - var(--zk-space-6));
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-lg);
}

.zk-dialog .el-dialog__header {
  margin-right: 0;
  padding-bottom: var(--zk-space-3);
  border-bottom: 1px solid var(--zk-border-light);
}

.zk-dialog .el-dialog__title {
  font-size: var(--zk-font-base);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.zk-dialog .el-dialog__body {
  /* 内容长时只滚 body：标题与页脚固定，用户不会滚着滚着找不到提交按钮 */
  overflow-y: auto;
  max-height: min(62vh, 620px);
  padding-bottom: var(--zk-space-4);
  font-size: var(--zk-font-base);
  color: var(--zk-text-regular);
}

.zk-dialog .el-dialog__footer {
  padding-top: var(--zk-space-3);
  border-top: 1px solid var(--zk-border-light);
}

.zk-dialog__note {
  margin: 0 0 var(--zk-space-4);
  font-size: var(--zk-font-sm);
  line-height: 1.7;
  color: var(--zk-text-secondary);
}

.zk-dialog__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-3);
}

.zk-dialog__foot-extra {
  min-width: 0;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.zk-dialog__buttons {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  margin-left: auto;
}
</style>
