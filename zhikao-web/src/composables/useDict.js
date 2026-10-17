import { computed, ref } from 'vue'
import { useDictStore } from '@/store/dict'

/**
 * 字典组合式函数。
 *
 * 用法：
 * ```js
 * const { dict, labelOf, colorOf } = useDict('question_type', 'difficulty')
 * // 模板里：<el-option v-for="it in dict.question_type" :label="it.label" :value="it.value" />
 * //        labelOf('question_type', row.type)
 * ```
 *
 * 设计取舍：返回的是 computed，因此字典异步到达后组件会自动重渲染，
 * 调用方不需要在 onMounted 里等 Promise，也不必写 v-if 判空。
 */
export function useDict(...types) {
  const list = types.flat().filter(Boolean)
  const store = useDictStore()
  const ready = ref(false)

  store
    .ensure(list)
    .then(() => {
      ready.value = true
    })
    .catch(() => {
      // 字典拉取失败不阻塞页面：labelOf 会回退为原始取值
      ready.value = false
    })

  return {
    ready,
    /** 响应式字典内容：dict.value.question_type */
    dict: computed(() => store.pick(list)),
    /** 取值 -> 文案 */
    labelOf: (type, value) => store.labelOf(type, value),
    /** 取值 -> 语义色 */
    colorOf: (type, value) => store.colorOf(type, value),
    /** 手工刷新（管理页保存后） */
    refresh: () => store.refresh(list)
  }
}

/**
 * 仅取一个字典的常用简写。
 */
export function useDictOne(type) {
  const store = useDictStore()
  const ready = ref(false)
  store
    .ensure([type])
    .then(() => {
      ready.value = true
    })
    .catch(() => {
      ready.value = false
    })

  return {
    ready,
    options: computed(() => store.get(type)),
    labelOf: (value) => store.labelOf(type, value),
    colorOf: (value) => store.colorOf(type, value)
  }
}
