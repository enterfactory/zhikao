import request from '@/utils/request'

/**
 * 题库采集（文档摄取 / 清洗）接口。
 *
 * 七期把采集入口从「上网爬页面」换成「读文档」：docx / doc / pdf / 图片 / md / txt / json / csv。
 * 页面与后端并行开发，所以这里把契约写清楚，页面对缺字段一律做兜底：
 *
 * GET  /crawler/source/list        → [{ id, name, baseUrl, sourceType: 'LOCAL_DIR',
 *                                        licenseNote, rateLimitMs, defaultSubjectId, enabled, remark }]
 * POST /crawler/source             → 新建来源（body 同字段）
 * PUT  /crawler/source/{id}        → 编辑来源
 * POST /crawler/source/{id}/toggle → 启停（无 body，返回切换后的 enabled）
 * POST /crawler/task/run/{sourceId}→ taskId
 * GET  /crawler/task/page?page&size→ { records: [{ id, sourceId, sourceName,
 *                                        status: 'PENDING'|'RUNNING'|'SUCCESS'|'FAILED',
 *                                        fetched, cleaned, staged, error, startedAt, finishedAt }], total }
 * GET  /crawler/question/page?page&size&stage&subjectId&grade&rawId
 *                                  → { records: [{ id, rawId, stage, grade, gradeLevel, subjectId,
 *                                        subjectName, questionType, content, options, answer, analysis,
 *                                        difficulty, knowledgeTags, qualityFlags, createTime }], total }
 * POST /crawler/question/review    → body { ids: [...], action: 'PROMOTE'|'REJECT', note, subjectId }
 * POST /crawler/import/local       → body { dirPath }（异步：只是把目录入成一条采集任务）
 * GET  /crawler/document/page?page&size&taskId&keyword&unresolvedOnly
 *                                  → { records: [{ id, taskId, docName, sourceUrl, viaOcr, charCount,
 *                                        parserName, parsedCount, stagedCount, parseNote, fetchTime }], total }
 * GET  /crawler/document/{id}      → 上面一行 + { contentPreview, contentLength, contentTruncated,
 *                                        questions: [候选题], questionsTruncated }
 * POST /crawler/upload             → multipart 字段 files（可多个），返回
 *                                     { stored, rejected: [原因], sourceId, taskId, ingestDir }
 * GET  /crawler/ocr/status         → { mode, rapidocr: bool, 'qwen-vl': bool }
 * GET  /crawler/stats              → { RAW, PARSED, CLEANED, TAGGED, REVIEWED, PROMOTED, REJECTED }
 *
 * 分页参数口径：后端这两个分页接口用 page / size（与 /user/page 的 pageNum / pageSize 不同），
 * 所以在 API 层做一次转换，页面里仍然按现有约定（pageNum / pageSize）书写，避免两套心智模型。
 *
 * silent：轮询类调用（stats / 任务列表）静默失败。轮询每 10~30 秒一次，
 * 后端未就绪时若每次都弹红条，页面会被消息海淹没；这类失败交给页面内联提示。
 */

// ===== 采集来源 =====
export const crawlerSourceList = () => request.get('/crawler/source/list')

export const crawlerSourceCreate = (data) => request.post('/crawler/source', data)

export const crawlerSourceUpdate = (id, data) => request.put(`/crawler/source/${id}`, data)

export const crawlerSourceToggle = (id) => request.post(`/crawler/source/${id}/toggle`)

// ===== 采集任务 =====
export const crawlerRunTask = (sourceId) => request.post(`/crawler/task/run/${sourceId}`)

export const crawlerTaskPage = ({ pageNum = 1, pageSize = 10, silent = false } = {}) =>
  request.get('/crawler/task/page', { params: { page: pageNum, size: pageSize }, silent })

// ===== 清洗后的候选题目 =====
export const crawlerQuestionPage = ({
  pageNum = 1,
  pageSize = 20,
  stage,
  subjectId,
  grade,
  rawId,
  silent = false
} = {}) =>
  request.get('/crawler/question/page', {
    params: { page: pageNum, size: pageSize, stage, subjectId, grade, rawId },
    silent
  })

/**
 * 批量审核。返回 ImportResultVO：successCount / failCount / errors（最多 20 条）。
 * subjectId 是「科目兜底」：OCR 文档大多认不出科目，主库录入又强制要求科目，
 * 由审核人在入库弹窗里指定一个，只补空缺、不覆盖已有科目。
 */
export const crawlerQuestionReview = ({ ids, action, note, subjectId }) =>
  request.post('/crawler/question/review', { ids, action, note, subjectId })

// ===== 本地目录导入 =====
export const crawlerImportLocal = (dirPath) => request.post('/crawler/import/local', { dirPath })

/**
 * 文档级解析日志分页：一行 = 一份摄取进来的文档 + 它被解析的结论。
 * 任务分页只有 fetched/cleaned/staged 三个聚合数，回答不了「这一份为什么 0 题」，
 * 所以排查从这一页开始。unresolvedOnly=true 时只列出没出过题的文档。
 */
export const crawlerDocumentPage = ({
  pageNum = 1,
  pageSize = 20,
  taskId,
  keyword,
  unresolvedOnly,
  silent = false
} = {}) =>
  request.get('/crawler/document/page', {
    params: { page: pageNum, size: pageSize, taskId, keyword, unresolvedOnly },
    silent
  })

/** 单份文档详情：取字原文片段 + 该文档产出的候选题 */
export const crawlerDocumentDetail = (id) => request.get(`/crawler/document/${id}`)

/**
 * 文档上传采集：一次最多 20 份，字段名固定 files（与后端 @RequestParam("files") 对齐）。
 *
 * 这里显式把 timeout 放宽到 5 分钟：几十兆的扫描件走内网也要一阵，
 * 默认 30 秒会让「明明在传、前端却报超时」。
 *
 * silent：上传失败的具体原因（哪一份、为什么被拒）由页面逐条列在弹窗里，
 * 全局红条只会盖住真正有用的信息。
 */
export const crawlerUpload = (files) => {
  const formData = new FormData()
  // 同名 append 是 multipart 传数组的标准写法，后端按 MultipartFile[] 接收
  Array.from(files).forEach((file) => formData.append('files', file))
  return request.post('/crawler/upload', formData, { timeout: 5 * 60 * 1000, silent: true })
}

// ===== OCR 通道状态 =====
export const crawlerOcrStatus = (silent = true) => request.get('/crawler/ocr/status', { silent })

// ===== 阶段统计 =====
export const crawlerStats = (silent = true) => request.get('/crawler/stats', { silent })

/**
 * 「识别与成本」概览（S3）：取字来源分布 + 级联逐层漏斗 + 升级率与云端成本 + 按天走势 + 待关注文档榜。
 *
 * days：时间窗天数，0 表示全部历史文档。这个接口要扫 crawl_raw 的时间窗，
 * 比 30 秒一次的 crawlerStats 重，所以只在切到该页签或改时间窗时才调。
 */
export const crawlerOcrSummary = (days = 30, silent = true) =>
  request.get('/crawler/ocr/summary', { params: { days }, silent })
