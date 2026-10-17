import request from '@/utils/request'

// ===== 考试公告 =====
export const announcementList = (examId) => request.get(`/exam/${examId}/announcement`)

/** 新增公告。push 默认 true：写公告的目的通常就是让人看到，不推等于白写 */
export const announcementAdd = (examId, content, push = true) =>
  request.post(`/exam/${examId}/announcement`, { content, push: push ? 1 : 0 })

export const announcementRemove = (id) => request.delete(`/exam/announcement/${id}`)
