/**
 * ECharts 按需引入。
 *
 * 一期是 `import * as echarts from 'echarts'` 全量引入——实测打包后 1041KB，
 * 而本项目只用到折线图与柱状图。这里改为从 echarts/core 引入核心 +
 * 按需注册图表与组件，实测降到 535KB（减少约一半），
 * 再配合 ChartCard 的动态加载，图表库彻底移出首屏依赖图。
 *
 * 注册清单刻意保持最小：**没用到的不注册**。
 * （例如饼图 PieChart 与数据集 DatasetComponent：本项目没有用到，就不引入。
 *  新增图表类型时在这里补一行即可。）
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer
])

/** 统一的图表主题色（取自设计 Token 的语义色，保证图表与页面同色系） */
export const CHART_COLORS = ['#2f6bff', '#16a34a', '#f59e0b', '#dc2626', '#0ea5e9', '#7c5cff']

/**
 * 语义色常量。
 *
 * 为什么图表里必须用常量而不是 `var(--zk-danger)`：
 * ECharts 把颜色写进 canvas 绘制指令，不经过 CSS 解析，CSS 变量传进去只会变成无效值。
 * 所以图表这一处是「Token 化」的合法例外——把值集中在这里，
 * 与 tokens.css 中的语义色保持一一对应，改色时两处一起改（已在注释中标注对应关系）。
 */
export const SEMANTIC = {
  primary: '#2f6bff', // 对应 --zk-primary
  success: '#16a34a', // 对应 --zk-success
  warning: '#f59e0b', // 对应 --zk-warning
  danger: '#dc2626', // 对应 --zk-danger
  info: '#0ea5e9' // 对应 --zk-info
}

/** 薄弱判定阈值（与后端 AiServiceImpl / WrongBookService 保持一致） */
export const WEAK_ACCURACY = 60

/** 常用的基础配置：统一字体、去掉多余留白、网格线弱化 */
export function baseOption() {
  return {
    color: CHART_COLORS,
    textStyle: { fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif', color: '#4b5563' },
    grid: { left: 12, right: 16, top: 32, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    legend: { icon: 'roundRect', itemWidth: 10, itemHeight: 10, textStyle: { color: '#6b7280' } }
  }
}

export default echarts
