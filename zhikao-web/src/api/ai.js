import request from '@/utils/request'

// ===== AI 增强（智能出题 / 辅助阅卷 / 错题分析）=====
export const aiStatus = () => request.get('/ai/status')

export const aiGenQuestion = (data) => request.post('/ai/genQuestion', data)

export const aiAssistMark = (data) => request.post('/ai/assistMark', data)

export const aiAnalysis = (examId) => request.get(`/ai/analysis/${examId}`)

// ===== 智能体对话 =====
/**
 * 当前角色可用的 Agent 清单（含能力范围说明）。
 * 由后端下发而不是前端硬编码角色判断：权限的真相只能有一处，
 * 前端再写一遍必然和后端漂移。
 */
export const aiCatalog = () => request.get('/ai/chat/catalog')

export const aiConversations = () => request.get('/ai/chat/conversations')

export const aiMessages = (id) => request.get(`/ai/chat/conversations/${id}/messages`)

export const aiDeleteConversation = (id) => request.delete(`/ai/chat/conversations/${id}`)

export const aiChat = (data) => request.post('/ai/chat', data)
