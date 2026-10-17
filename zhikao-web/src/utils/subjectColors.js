/**
 * 学科色：五期学生端「同色贯穿」的唯一出处。
 *
 * 考试卡色带、题库网格、错题本 chips、成绩条都调用 subjectColor(科目名)，
 * 保证「数学」在五个屏永远是同一个蓝。学生记住的是颜色，不是科目全称。
 *
 * 未登记的科目按名称哈希取色——同一科目名永远得到同一颜色，
 * 教务新建科目不需要改代码（但建议为主科在此登记专属色）。
 */
const PALETTE = [
  { main: '#3E6FE0', soft: '#EAEFFF' }, // 蓝
  { main: '#E8654F', soft: '#FDEDE9' }, // 砖红
  { main: '#7C5CE6', soft: '#F0EBFD' }, // 紫
  { main: '#0EA5A4', soft: '#E2F5F5' }, // 青
  { main: '#D9901A', soft: '#FBF1DC' }, // 琥珀
  { main: '#58A939', soft: '#EAF6E2' }, // 草绿
  { main: '#A5673F', soft: '#F5EAE0' }, // 棕
  { main: '#2B7FA8', soft: '#E3F0F6' }, // 钢蓝
  { main: '#C0457A', soft: '#FAE8EF' }, // 玫红
  { main: '#0E9F6E', soft: '#E3F5EC' }  // 翠绿
]

const NAMED = {
  语文: 1, 数学: 0, 英语: 2, 物理: 3, 化学: 4, 生物: 5,
  历史: 6, 地理: 7, 政治: 8, 道德与法治: 8, 道法: 8, 科学: 9,
  历史与社会: 6, 信息技术: 7, 音乐: 2, 美术: 4, 体育: 3
}

function hashCode(str) {
  let h = 0
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0
  return Math.abs(h)
}

/** 返回 { main, soft }；入参是科目名（允许带前后缀，如「数学 必修一」） */
export function subjectColor(name) {
  const key = String(name || '').trim()
  if (!key) return PALETTE[0]
  if (NAMED[key] !== undefined) return PALETTE[NAMED[key]]
  const hit = Object.keys(NAMED).find((n) => key.includes(n))
  if (hit) return PALETTE[NAMED[hit]]
  return PALETTE[hashCode(key) % PALETTE.length]
}

/** 供 :style 直接展开使用：{ '--sc': 主色, '--sc-soft': 浅底 } */
export function subjectVars(name) {
  const { main, soft } = subjectColor(name)
  return { '--sc': main, '--sc-soft': soft }
}
