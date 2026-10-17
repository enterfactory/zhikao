import request from '@/utils/request'
export const studyTopics = () => request.get('/v2/learning/topics')
export const practiceHistory = () => request.get('/v2/learning/history')
export const startPractice = (data) => request.post('/v2/learning/sessions', data)
export const getPractice = (id) => request.get(`/v2/learning/sessions/${id}`)
export const answerPractice = (id, data) =>
  request.post(`/v2/learning/sessions/${id}/answers`, data)
export const candidate = (id) => request.get(`/v2/curation/candidates/${id}`)
export const saveCandidate = (id, data) => request.patch(`/v2/curation/candidates/${id}`, data)
export const publishCandidate = (id, expectedVersion) =>
  request.post(`/v2/curation/candidates/${id}/publish`, { expectedVersion })
export const releaseResults = (id, level) => request.post(`/v2/exams/${id}/releases`, { level })
export const documentPageImage = (id, page) =>
  request.get(`/v2/curation/documents/${id}/pages/${page}`, { responseType: 'blob', silent: true })
export const documentSource = (id) =>
  request.get(`/v2/curation/documents/${id}/source`, { responseType: 'blob' })
export const retryImport = (id) => request.post(`/v2/curation/tasks/${id}/retry`)

export const startCandidateReview = (id) => request.post(`/v2/curation/candidates/${id}/start`)
export const candidateEvents = (id) => request.get(`/v2/curation/candidates/${id}/events`)
export const rejectCandidate = (id, expectedVersion, note) => request.post(`/v2/curation/candidates/${id}/reject`, {expectedVersion, note})
