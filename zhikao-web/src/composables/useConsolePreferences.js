import { reactive, watch } from 'vue'
const key = 'zhikao.console.v4'
let saved = {}
try {
  saved = JSON.parse(localStorage.getItem(key) || '{}')
} catch {
  /* Defaults */
}
const preferences = reactive({
  collapsed: saved?.collapsed === true,
  density: saved?.density === 'compact' ? 'compact' : 'standard',
  sidebar: saved?.sidebar === 'dark' ? 'dark' : 'light',
})
watch(preferences, (value) => {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* Storage is optional */
  }
})
export const useConsolePreferences = () => preferences
