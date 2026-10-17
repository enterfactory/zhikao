import axios from 'axios'

/**
 * 统一请求实例。
 *
 * 错误码约定（与后端 ErrorCode 枚举一一对应）：
 *   200    成功
 *   40001  参数错误
 *   40101  未登录 / token 过期  -> 清登录态并跳登录页
 *   40301  无权限
 *   42901  触发限流
 *   42902  账号临时锁定
 *   50001  业务失败（如重名、状态非法）
 *   50000  系统异常
 *
 * 注意：后端统一返回 HTTP 200，业务结果全在 body.code 里，
 * 所以前端只需要在这一处判断，不必再关心 HTTP 状态码。
 */
export const CODE = {
  SUCCESS: 200,
  PARAM_ERROR: 40001,
  UNAUTHORIZED: 40101,
  FORBIDDEN: 40301,
  PASSWORD_CHANGE_REQUIRED: 40302,
  RATE_LIMITED: 42901,
  ACCOUNT_LOCKED: 42902,
  BUSINESS_ERROR: 50001,
  SYSTEM_ERROR: 50000
}

const request = axios.create({
  baseURL: '/api',
  // 30 秒：AI 相关的同步兜底接口耗时较长，15 秒会误报超时
  timeout: 30000
})

// 请求拦截：自动携带 Sa-Token 的 token（请求头名称与后端 sa-token.token-name 一致）
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
})

let redirecting = false

// 响应拦截：统一拆解业务结构 R{code,msg,data}
request.interceptors.response.use(
  (response) => {
    // 文件流（模板下载、数据导出）：直接返回原始数据，不按 R 结构解析
    if (response.config.responseType === 'blob') {
      return response.data
    }

    const data = response.data
    if (data.code === CODE.SUCCESS) {
      return data.data // 直接返回业务数据
    }

    // 未登录：清理并跳转登录。加 redirecting 闸门，
    // 避免页面同时发起 5 个请求时弹出 5 条提示并跳 5 次。
    if (data.code === CODE.UNAUTHORIZED) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      if (!redirecting) {
        redirecting = true
        Promise.all([import('@/store/user'), import('@/router')]).then(([users, router]) => {
          const store = users.useUserStore()
          store.token = ''
          store.userInfo = null
          return router.default.push('/login')
        }).finally(() => { redirecting = false })
      }
    }

    if (data.code === CODE.PASSWORD_CHANGE_REQUIRED && !redirecting) {
      redirecting = true
      Promise.all([import('@/store/user'), import('@/router')]).then(([users, router]) => {
        const store = users.useUserStore()
        store.userInfo = { ...store.userInfo, passwordChangeRequired: true }
        localStorage.setItem('userInfo', JSON.stringify(store.userInfo))
        return router.default.push('/profile')
      }).finally(() => { redirecting = false })
    }

    // 限流与锁定用「警告」而非「错误」：这不是 bug，是预期内的保护
    if (data.code === CODE.RATE_LIMITED || data.code === CODE.ACCOUNT_LOCKED) {
      if (!response.config.silent) ElMessage.warning(data.msg || '操作过于频繁')
    } else if (data.code !== CODE.UNAUTHORIZED && !response.config.silent) {
      // silent（opt-in 配置项）：聚合类接口（如门户首页 /portal/home）允许失败，
      // 调用方自带降级数据，不该在首屏弹一条「请求失败」红条吓人
      ElMessage.error(data.msg || '请求失败')
    }
    return Promise.reject(Object.assign(new Error(data.msg || '请求失败'), { code: data.code }))
  },
  (error) => {
    const message = error.response?.data?.msg || error.message || '网络错误'
    // 同上：HTTP 层错误（404 / 502 等）也尊重 silent 标记——/portal/home 在后端
    // 未实现时就是 404，静默交给调用方降级
    if (!error.config?.silent) ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
