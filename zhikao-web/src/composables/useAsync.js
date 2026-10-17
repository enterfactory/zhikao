import { reactive, ref } from 'vue'
import { errorMessage, notifyError, notifySuccess } from '@/utils/feedback'

/**
 * 异步三态收口。
 *
 * 这一层要解决的是**假空态**：以前列表页一律写
 * ```
 * loading = true; try { rows = await api() } finally { loading = false }
 * ```
 * 接口挂了 rows 还是空数组，于是表格显示「暂无数据」——用户以为真的没有数据，
 * 管理员以为系统里没东西可查。DataTable 早就支持 error 分支，只是没有一个
 * 顺手的东西把 error 填进去，所以没人用。把 catch 写进 composable，
 * 页面想漏都漏不掉。
 */

/** 单次加载：适合「一段数据、一个刷新按钮」的场景（下拉选项、统计卡片） */
export function useAsync(loader, options = {}) {
  const { immediate = true, errorText = '数据加载失败' } = options

  const data = ref(null)
  const loading = ref(false)
  const error = ref('')

  async function run(...args) {
    loading.value = true
    try {
      const result = await loader(...args)
      data.value = result ?? null
      error.value = ''
      return result
    } catch (e) {
      error.value = errorMessage(e, errorText)
      return null
    } finally {
      loading.value = false
    }
  }

  if (immediate) run()

  return { data, loading, error, run }
}

/**
 * 分页列表：query + 分页参数 + 加载/错误态一起给。
 *
 * loader 收到 `{ ...query, pageNum, pageSize }`，返回 `{ records, total }`。
 * search 会把页码退回第 1 页——改完筛选条件还停在第 8 页，是「查不到数据」这个
 * 假 bug 最常见的来源。
 */
export function usePagedQuery(loader, options = {}) {
  const {
    defaultQuery = () => ({}),
    pageSize: initialPageSize = 10,
    immediate = true,
    errorText = '数据加载失败'
  } = options

  const rows = ref([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref('')
  const pageNum = ref(1)
  const pageSize = ref(initialPageSize)
  const query = reactive(defaultQuery())

  async function load() {
    loading.value = true
    try {
      const data = await loader({ ...query, pageNum: pageNum.value, pageSize: pageSize.value })
      rows.value = data?.records || []
      total.value = data?.total || 0
      error.value = ''
    } catch (e) {
      rows.value = []
      total.value = 0
      error.value = errorMessage(e, errorText)
    } finally {
      loading.value = false
    }
  }

  function search() {
    pageNum.value = 1
    return load()
  }

  function reset() {
    Object.assign(query, defaultQuery())
    pageNum.value = 1
    return load()
  }

  function onPageChange(page) {
    pageNum.value = page
    return load()
  }

  function onSizeChange(size) {
    pageSize.value = size
    pageNum.value = 1
    return load()
  }

  if (immediate) load()

  return {
    rows,
    total,
    loading,
    error,
    query,
    pageNum,
    pageSize,
    load,
    search,
    reset,
    onPageChange,
    onSizeChange
  }
}

/**
 * 提交按钮：pending 期间直接拒绝二次进入。
 *
 * 双点「保存」会打出两个相同请求，后端要么重复插入要么后者覆盖前者，
 * 而用户只看到「保存成功」弹了两次。这里在源头掐掉，而不是靠按钮的 loading 样式。
 */
export function useSubmit(action, options = {}) {
  const { successText = '', errorText = '保存失败' } = options

  const pending = ref(false)

  async function run(...args) {
    if (pending.value) return false
    pending.value = true
    try {
      const result = await action(...args)
      if (successText) notifySuccess(successText)
      return result ?? true
    } catch (e) {
      notifyError(e, errorText)
      return false
    } finally {
      pending.value = false
    }
  }

  return { pending, run }
}
