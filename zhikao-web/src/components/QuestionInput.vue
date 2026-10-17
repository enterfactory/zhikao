<template>
  <div class="question-input">
    <fieldset v-if="type === 1 || type === 2 || type === 3" :disabled="disabled">
      <legend class="sr-only">{{ type === 2 ? '请选择所有正确选项' : '请选择一个答案' }}</legend>
      <label
        v-for="(text, key) in choices"
        :key="key"
        class="answer-option"
        :class="{ selected: selected(key) }"
      >
        <input
          :type="type === 2 ? 'checkbox' : 'radio'"
          :name="groupId"
          :value="key"
          :checked="selected(key)"
          @change="choose(key)"
        />
        <span class="option-key">{{ key }}</span>
        <RichContent :html="text" inline />
      </label>
    </fieldset>
    <label v-else>
      <span class="sr-only">填写答案</span>
      <textarea
        :rows="type === 5 ? 8 : 3"
        :value="modelValue"
        :disabled="disabled"
        maxlength="20000"
        :placeholder="type === 4 ? '多个空请用 ; 分隔' : '在此填写你的解答过程…'"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    </label>
  </div>
</template>
<script setup>
import { computed, useId } from 'vue'
import RichContent from '@/components/RichContent.vue'
const props = defineProps({
  modelValue: { type: String, default: '' },
  type: Number,
  options: { type: Object, default: () => ({}) },
  disabled: Boolean,
})
const emit = defineEmits(['update:modelValue'])
const groupId = useId()
const choices = computed(() => (props.type === 3 ? { 对: '正确', 错: '错误' } : props.options))
function selected(key) {
  return props.type === 2 ? props.modelValue.split(',').includes(key) : props.modelValue === key
}
function choose(key) {
  if (props.type !== 2) return emit('update:modelValue', key)
  const set = new Set(props.modelValue.split(',').filter(Boolean))
  set.has(key) ? set.delete(key) : set.add(key)
  emit('update:modelValue', [...set].sort().join(','))
}
</script>
