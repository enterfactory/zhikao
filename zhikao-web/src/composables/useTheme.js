import { ref } from 'vue'

/**
 * 主题色切换。
 *
 * 实现方式：只改 <html> 上的 data-theme 属性，具体的颜色值由 tokens.css 里的
 * `:root[data-theme='green'] { ... }` 覆写主色梯度。
 * 组件里一律用 var(--zk-primary)，所以**没有任何组件需要知道主题的存在**——
 * 这是 Token 化最大的收益。
 */
const STORAGE_KEY = 'zk-theme'

export const THEMES = [
  { value: 'blue', label: '专业蓝', color: '#2f6bff' },
  { value: 'green', label: '青竹绿', color: '#10a37f' },
  { value: 'purple', label: '典雅紫', color: '#7c5cff' }
]

// 模块级单例：多个组件同时使用 useTheme 时共享同一份状态
const current = ref(localStorage.getItem(STORAGE_KEY) || 'blue')

function apply(theme) {
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem(STORAGE_KEY, theme)
}

// 初始化时立即应用，避免刷新页面后闪烁
apply(current.value)

export function useTheme() {
  const setTheme = (theme) => {
    if (!THEMES.some((item) => item.value === theme)) return
    current.value = theme
    apply(theme)
  }

  const toggle = () => {
    const index = THEMES.findIndex((item) => item.value === current.value)
    setTheme(THEMES[(index + 1) % THEMES.length].value)
  }

  return { theme: current, themes: THEMES, setTheme, toggle }
}
