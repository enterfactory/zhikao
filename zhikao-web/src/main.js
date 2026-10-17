import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import { registerIcons } from './utils/icons'

// 全局样式：Token / 重置 / Element 覆写 / 动画 / 工具类
// （顺序在 index.css 内部保证，这里只引一次入口）
import './assets/styles/index.css'
import './assets/styles/workflow.css'
import './assets/styles/console-v4.css'

/*
 * 命令式组件与指令的样式。
 *
 * 说明：unplugin-auto-import / -vue-components 会为**自动引入**的 ElMessage、
 * ElMessageBox 等注入对应样式；但本项目里它们是通过全局自动引入使用的，
 * 为规避「构建配置变化导致样式静默丢失」这类问题，这里把用到的几份样式显式引入一次。
 * 重复引入会被打包器去重，代价极小，换来的是确定性。
 */
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-loading.css'
import 'element-plus/theme-chalk/el-notification.css'

const app = createApp(App)

// 按需注册图标（见 utils/icons.js 的说明：全量注册会把 2000 个图标打进包体）
registerIcons(app)

// 全量注册 Element Plus 并挂中文语言包：
// ElMessageBox / el-pagination / el-date-picker 等内置文案默认是英文（OK/Cancel），
// 语言包只有在这里注入才会对命令式组件生效，退出确认等弹窗按钮即为中文。
app.use(ElementPlus, { locale: zhCn })

/**
 * 错误边界（二期「其他优化」第 10 条）。
 *
 * 组件渲染 / 生命周期里的异常默认会冒到控制台并让整页白屏。
 * 这里兜住它：给出可读的提示 + 把错误留在控制台供排查，
 * 至少保证「一个组件挂了，其他页面还能用」。
 *
 * 注意：ElMessage 是 auto-import 提供的全局符号，无需 import。
 */
app.config.errorHandler = (err, instance, info) => {
  // eslint-disable-next-line no-console
  console.error('[zhikao] 组件异常：', info, err)
  ElMessage.error('页面出现异常，请刷新重试；若持续出现请联系管理员')
}

app.config.warnHandler = (msg, instance, trace) => {
  // 生产环境只保留警告，避免开发期的提示污染控制台
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.warn('[zhikao]', msg, trace)
  }
}

app.use(createPinia())
app.use(router)
app.mount('#app')
