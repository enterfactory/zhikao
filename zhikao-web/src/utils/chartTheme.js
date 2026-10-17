import { computed } from 'vue'
import { useTheme } from '@/composables/useTheme'

/**
 * 图表色解析工具。
 *
 * 为什么需要它：ECharts 把颜色写进 canvas 绘制指令，不经过 CSS 解析，
 * 所以 option 里写 `var(--zk-border)` 只会得到无效值（三期首页的两处轴色
 * 就是这么静默失效的）。唯一可靠的办法是用 getComputedStyle 把 Token
 * 的**实际值**读出来再交给图表。
 *
 * 主题切换（html 上的 data-theme）会覆写主色梯度，这里 watch useTheme 的
 * theme 让 computed 失效重取——调用方拿到的永远是当前主题的色值。
 */
export function useChartTheme() {
  const { theme } = useTheme()

  /** 依赖 theme：主题变化时下面整个 computed 会重算 */
  const colors = computed(() => {
    // theme 本身没有被读取内容，这里只是建立响应式依赖
    void theme.value
    const style = getComputedStyle(document.documentElement)
    const read = (token, fallback) => (style.getPropertyValue(token) || fallback).trim()

    return {
      primary: read('--zk-primary', '#2f6bff'),
      success: read('--zk-success', '#16a34a'),
      warning: read('--zk-warning', '#f59e0b'),
      danger: read('--zk-danger', '#dc2626'),
      info: read('--zk-info', '#0ea5e9'),
      textPrimary: read('--zk-text-primary', '#1f2937'),
      textRegular: read('--zk-text-regular', '#4b5563'),
      textSecondary: read('--zk-text-secondary', '#6b7280'),
      border: read('--zk-border', '#e5e7eb'),
      bgPage: read('--zk-bg-page', '#f5f7fa'),
      bgCard: read('--zk-bg-card', '#ffffff')
    }
  })

  /** 常用组合：坐标轴线 + 分隔线，直接展开进 option 的 axisLine/splitLine */
  const axis = computed(() => ({
    lineColor: colors.value.border,
    splitColor: colors.value.bgPage,
    labelColor: colors.value.textSecondary
  }))

  /** 折线/柱状调色板：主色永远排第一，随主题切换 */
  const palette = computed(() => [
    colors.value.primary,
    colors.value.success,
    colors.value.warning,
    colors.value.info,
    colors.value.danger
  ])

  return { colors, axis, palette }
}
