import { defineStore } from 'pinia'
import { login as loginApi, info as infoApi, logout as logoutApi } from '@/api/auth'

// 用户状态：token 与用户信息持久化到 localStorage，刷新页面不丢失
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    // 粗粒度身份：admin / teacher / student（决定落哪套壳）
    role: state => state.userInfo?.role || '',
    // RBAC 细粒度：角色码与权限点由后端 /auth/info、/auth/login 一并下发
    roleCodes: state => state.userInfo?.roleCodes || [],
    permissions: state => state.userInfo?.permissions || []
  },
  actions: {
    // 登录：调用接口并落库
    async login(form) {
      const res = await loginApi(form)
      this.token = res.token
      this.userInfo = res.userInfo
      localStorage.setItem('token', res.token)
      localStorage.setItem('userInfo', JSON.stringify(res.userInfo))
      return res
    },
    // 拉取当前用户信息（刷新页面或 token 恢复后使用）
    async fetchInfo() {
      const res = await infoApi()
      this.userInfo = res.userInfo
      localStorage.setItem('userInfo', JSON.stringify(res.userInfo))
      return res
    },
    // 退出登录：先通知后端注销 token，再清理本地状态
    // 接口失败（如 token 已过期）也要清理，否则会卡在登录态无法重新登录
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 忽略登出接口异常
      }
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
