import request from '@/utils/request'

// ===== 错题本与复习建议（学生本人）=====
export const wrongPage = (params) => request.get('/learn/wrong/page', { params })

export const wrongSummary = () => request.get('/learn/wrong/summary')

export const wrongKnowledge = () => request.get('/learn/wrong/knowledge')

export const recommendByPoint = (knowledgePoint) =>
  request.get('/learn/recommend', { params: { knowledgePoint } })
