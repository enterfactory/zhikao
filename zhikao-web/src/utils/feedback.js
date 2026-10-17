import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 交互反馈的统一出口。
 *
 * 这三个函数看着小，但它们各自堵掉一个反复出现的 bug：
 *
 * 1. `validateForm` —— 全项目有 12 处 `await formRef.value.validate()` 不接 catch。
 *    Element 的 validate 在校验不过时是 **reject**，于是每次点保存都往控制台抛一条
 *    Unhandled Rejection，用户看到的是「点了没反应」。
 * 2. `confirmAction` —— `ElMessageBox.confirm` 在点「取消」时同样 reject，
 *    以前每页都要自己写一层 try/catch 才能挡住，于是有的页写了、有的没写。
 * 3. `errorMessage` —— 后端业务错的文案在 `R.msg` 里，而 axios 抛的是 Error，
 *    取 `e.message` 只能拿到「Request failed with status code 500」这种没用的话。
 */

/** 从各种异常形态里取出「能给人看的那句」；取不到就回落到兜底文案，绝不显示 undefined */
export function errorMessage(error, fallback = '操作失败，请稍后重试') {
  if (!error) return fallback
  if (typeof error === 'string') return error
  return error.msg || error?.response?.data?.msg || error.message || fallback
}

/** 表单校验：返回布尔而不是抛异常。调用方写 `if (!(await validateForm(ref))) return` */
export async function validateForm(formRef) {
  try {
    await formRef?.validate?.()
    return true
  } catch (e) {
    // 校验不过时 Element 会把字段对象作为 rejection 值抛出来，这里刻意不打印：
    // 用户已经能在表单上看到红字，控制台不该再多一条红色报错
    return false
  }
}

/**
 * 二次确认：返回 true 表示用户点了确认，点取消或关闭都返回 false。
 * danger 只用于不可逆操作（删除、清空、强制下线），它同时把确认按钮标红。
 */
export async function confirmAction({
  message,
  title = '请确认',
  confirmText = '确定',
  cancelText = '取消',
  danger = false
} = {}) {
  try {
    await ElMessageBox.confirm(message, title, {
      type: danger ? 'warning' : 'info',
      confirmButtonText: confirmText,
      cancelButtonText: cancelText,
      closeOnClickModal: false,
      confirmButtonClass: danger ? 'zk-confirm__danger' : ''
    })
    return true
  } catch (e) {
    return false
  }
}

export function notifyError(error, fallback = '操作失败') {
  ElMessage.error(errorMessage(error, fallback))
}

export function notifySuccess(message) {
  ElMessage.success(message)
}
