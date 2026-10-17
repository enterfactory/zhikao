import request from '@/utils/request'

// 认证接口封装
export function login(form) {
  return request.post('/auth/login', form)
}

export function info() {
  return request.get('/auth/info')
}

export function logout() {
  return request.post('/auth/logout')
}

/** 修改自己的密码（需提供旧密码；成功后其他端会被强制下线） */
export function changePassword(data) {
  return request.post('/auth/password/change', data)
}

/**
 * 本人账号的安全活动：登录 / 登录失败 / 登出 / 改密。
 * 后端只查当前登录账号，所以学生、教师都能看自己的登录轨迹，不需要操作日志权限。
 */
export function securityActivity(limit = 8) {
  return request.get('/auth/security-activity', { params: { limit }, silent: true })
}
