import request from '@/utils/request'

// ==================== 题库 ====================

/** 分页查询题目：{ pageNum, pageSize, subjectId, type, difficulty, keyword } */
export function pageQuestions(params) {
  return request.get('/question/page', { params })
}

/** 题目详情 */
export function getQuestion(id) {
  return request.get(`/question/${id}`)
}

/** 新增或修改题目（id 为空即新增） */
export function saveQuestion(data) {
  return request.post('/question', data)
}

/**
 * 批量新增题目。
 * 主要用于「AI 出题草稿勾选后一键入库」，但它本身是通用批量写入接口：
 * 后端逐条复用单题新建的校验，所以入库质量与手工录入一致。
 */
export function saveQuestionsBatch(items) {
  return request.post('/question/batch', items)
}

/** 删除题目 */
export function deleteQuestion(id) {
  return request.delete(`/question/${id}`)
}

/**
 * 批量导入题目到指定科目（formData 内含 file 字段）。
 * preview 传 true 时只校验不落库，用于「先看结果再决定」。
 */
export function importQuestions(subjectId, formData, preview = false) {
  return request.post('/question/import', formData, { params: { subjectId, preview } })
}

/** 下载题目导入模板 */
export function downloadQuestionTemplate() {
  return request.get('/question/import/template', { responseType: 'blob' })
}

/**
 * 入库前查重：找出与待入库题目字面高度重合的已有题目。
 * 只提示不阻止——同一个知识点出两道不同问法的题是合理的，决定权留给教师。
 */
export function findSimilarQuestions(data) {
  return request.post('/question/similarity', data)
}

// ==================== 试卷 / 组卷 ====================

/** 分页查询试卷 */
export function pagePapers(params) {
  return request.get('/paper/page', { params })
}

/** 全部试卷 */
export function listPapers() {
  return request.get('/paper/list')
}

/** 试卷详情（含题目与答案，供教师预览） */
export function getPaper(id) {
  return request.get(`/paper/${id}`)
}

/** 手工组卷 */
export function genManualPaper(data) {
  return request.post('/paper/genManual', data)
}

/** 随机组卷 */
export function genRandomPaper(data) {
  return request.post('/paper/genRandom', data)
}

/** 发布 / 撤回试卷 */
export function updatePaperStatus(id, status) {
  return request.put(`/paper/${id}/status`, null, { params: { status } })
}

/** 删除试卷 */
export function deletePaper(id) {
  return request.delete(`/paper/${id}`)
}

// ==================== 考试 ====================

/** 分页查询考试 */
export function pageExams(params) {
  return request.get('/exam/page', { params })
}

/** 考试详情 */
export function getExam(id) {
  return request.get(`/exam/${id}`)
}

/** 创建考试 */
export function createExam(data) {
  return request.post('/exam', data)
}

/** 发布考试 */
export function publishExam(id) {
  return request.post(`/exam/${id}/publish`)
}

/** 提前结束考试 */
export function finishExam(id) {
  return request.post(`/exam/${id}/finish`)
}

/** 删除考试 */
export function deleteExam(id) {
  return request.delete(`/exam/${id}`)
}

/** 考试实时监控 */
export function monitorExam(id) {
  return request.get(`/exam/${id}/monitor`)
}

// ==================== 答题（学生） ====================

/** 学生本人考试列表 */
export function myExams() {
  return request.get('/exam/mine')
}

/** 进入考试，返回答题卷 */
export function enterExam(id) {
  return request.post(`/exam/${id}/enter`)
}

/** 保存单题作答，返回剩余秒数 */
export function saveAnswer(id, data) {
  return request.put(`/exam/${id}/answer`, data)
}

/**
 * 上报切屏等异常。
 * 带上「第几题」能让教师事后判断是偶发还是持续，所以能带就带。
 */
export function reportCheat(id, payload) {
  return request.post(`/exam/${id}/cheat`, payload || {})
}

/** 交卷 */
export function submitExam(id) {
  return request.post(`/exam/${id}/submit`)
}

// ==================== 阅卷与成绩 ====================

/** 某场考试的主观题作答列表（教师批改用） */
export function subjectiveList(examId) {
  return request.get(`/exam/${examId}/subjective`)
}

/**
 * 主观题打分。
 * reason 在「改分」时必填：批改一定会出错，错了要能改，但改了必须留下理由，
 * 否则成绩争议时无法回答「为什么从 5 分变成了 8 分」。
 */
export function markAnswer(answerId, score, reason) {
  const params = { score }
  if (reason) params.reason = reason
  return request.put(`/exam/answer/${answerId}/mark`, null, { params })
}

/** 某场考试的批改留痕：谁在什么时候把哪道题从多少分改成了多少分 */
export function markLogs(examId) {
  return request.get(`/exam/${examId}/mark-logs`)
}

/** 教师视角：某场考试成绩统计 */
export function examGrade(examId) {
  return request.get(`/grade/exam/${examId}`)
}

/**
 * 教师视角：同步导出成绩 Excel。
 * 人数少的场次走这条链路最直接：点了就下载，不需要轮询。
 */
export function exportExamGrade(examId) {
  return request.get(`/grade/exam/${examId}/export`, { responseType: 'blob' })
}

/** 教师视角：提交异步导出任务（人数多时用，避免一条慢请求一直转圈） */
export function submitGradeExportTask(examId) {
  return request.post(`/grade/exam/${examId}/export/task`)
}

/** 查询导出任务状态（生成完成后带 downloadUrl） */
export function getExportTask(taskId) {
  return request.get(`/grade/export/task/${taskId}`)
}

/** 我最近发起的导出任务 */
export function myExportTasks() {
  return request.get('/grade/export/tasks')
}

/** 学生视角：本人成绩列表 */
export function myGrades() {
  return request.get('/grade/mine')
}

/** 学生视角：本人某场考试卷面明细（含解析） */
export function myPaper(examId) {
  return request.get(`/grade/mine/${examId}`)
}

// ==================== AI（可选增强） ====================
// AI 相关接口统一收敛到 src/api/ai.js，避免同一个后端接口在两处各写一份。
//
// 二期修正：这里原来有个 `aiAnalysis(data)` 用 POST 去调一个 GET 接口
// （/ai/analysis/{examId}），必然返回 405 —— 属于「写了但从没被调用过」的死代码。
// 现在 AI 接口只有 ai.js 一个入口，不会再出现这种前后端不一致。
