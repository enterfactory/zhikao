import request from '@/utils/request'

/**
 * 学生门户首页聚合接口（五期新增）。
 *
 * 契约（后端并行开发中）：
 * {
 *   banners:      [{ id, title, subtitle, cta, theme, link }],
 *   subjectStats: [{ subjectId, subjectName, totalQuestions, doneCount }],
 *   leaderboard:  [{ userId, name, score, rank }],
 *   resume:       { subjectName, setName, done, total, accuracy, lastAt } | null,
 *   nextExam:     { examId, examName, subjectName, startAt } | null,
 *   notices:      [{ id, title, tag, time }]
 * }
 *
 * silent: true —— 本接口是「首页锦上添花」的聚合层，后端未上线 / 偶发失败时
 * 调用方（StudentHome）会逐块降级到既有接口。若不静默，学生每次进首页都会
 * 被一条全局红色报错 toast 打断，这与「优雅降级」的约定相悖。
 * request.js 仅在该标记为 true 时跳过 ElMessage，不改任何拆包 / 鉴权逻辑。
 */
export const portalHome = () => request.get('/portal/home', { silent: true })
