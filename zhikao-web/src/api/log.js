import request from '@/utils/request'

// 操作日志（仅管理员）
export const logPage = (params) => request.get('/log/page', { params })

export const logModules = () => request.get('/log/modules')
