import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vite 配置：Vue 插件 + @ 别名 + 开发代理 + Element-Plus 按需引入 + 生产分包。
 *
 * 「按需引入」的两条路径要分清，否则很容易踩坑：
 * 1. **模板里用到的组件**（`<el-table>` 等）由 unplugin-vue-components 自动解析并注入样式；
 * 2. **命令式调用的组件**（ElMessage / ElMessageBox / v-loading）不走模板，
 *    自动引入覆盖不到 —— 它们的样式在 main.js 里显式 import，
 *    `v-loading` 指令也单独注册。这是最容易被漏掉的一处。
 */
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      // 只用 Element-Plus 的解析器：本项目自己的组件保持显式 import，读代码时更清楚依赖关系
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    // 目标：首屏 JS 体积较一期（全量 Element-Plus + 全量 ECharts）下降 30% 以上
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        // 手工分包：把变化频率低的第三方库单独切出来，利用浏览器缓存
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element'
          if (id.includes('vue') || id.includes('pinia')) return 'vendor-vue'
          return 'vendor'
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 开发态将 /api 原样代理到后端 8080，避免跨域。
      // 注意：后端已配置 server.servlet.context-path=/api，故此处不可再 rewrite 去掉 /api 前缀，
      // 否则 /api/auth/login 会被转发成 /auth/login，后端匹配不到导致 404。
      '/api': {
        target: process.env.ZHIKAO_API_PROXY || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
