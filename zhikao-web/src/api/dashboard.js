import request from '@/utils/request'

// 首页看板（按角色返回不同内容）
export const dashboardOverview = () => request.get('/dashboard/overview')
