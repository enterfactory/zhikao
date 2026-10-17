/**
 * 展示层格式化工具。
 *
 * 后端统一返回 `LocalDateTime` 序列化出来的 `2026-10-12T09:14:22`，
 * 而页面要的是「表格里的短日期」「活动列表里的相对时间」。以前这两件事
 * 是在 5 个页面里各写一遍 `String(v).replace('T',' ').slice(0,16)` 完成的，
 * 于是有的页面显示到分、有的显示到秒、有的直接把 T 原样摊给用户看。
 */

/** 后端时间串 → `2026-10-12 09:14`；无值时给一个明确的占位而不是空白单元格 */
export function formatDateTime(value, { withSeconds = false, fallback = '-' } = {}) {
  if (!value) return fallback
  const text = String(value).replace('T', ' ')
  return text.slice(0, withSeconds ? 19 : 16)
}

/** 只要日期部分 */
export function formatDate(value, fallback = '-') {
  if (!value) return fallback
  return String(value).replace('T', ' ').slice(0, 10)
}

/**
 * 相对时间：用于「最近一次登录」「3 分钟前」这类要看新旧而不要看精确值的场合。
 * 超过 30 天退回绝对日期——「320 天前」既不好读也不准。
 */
export function timeAgo(value) {
  if (!value) return '未知时间'
  const time = new Date(String(value).replace(/-/g, '/').replace('T', ' '))
  if (Number.isNaN(time.getTime())) return formatDateTime(value)
  const diff = Math.max(0, Date.now() - time.getTime())
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 30 * day) return `${Math.floor(diff / day)} 天前`
  return formatDateTime(value)
}

/** 毫秒耗时：慢请求要在表格里一眼看出来，所以按阈值给语义色 */
export function costTone(costMs) {
  const value = Number(costMs)
  if (!Number.isFinite(value)) return 'neutral'
  if (value >= 3000) return 'danger'
  if (value >= 1000) return 'warning'
  return 'neutral'
}
