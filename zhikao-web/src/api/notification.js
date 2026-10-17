import request from '@/utils/request'

// ===== 站内通知 =====
export const notificationPage = (params) => request.get('/notification/page', { params })

export const unreadCount = () => request.get('/notification/unread-count')

export const readNotification = (id) => request.put(`/notification/${id}/read`)

export const readAllNotification = () => request.post('/notification/read-all')
