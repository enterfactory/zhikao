<template>
  <component
    :is="inline ? 'span' : 'div'"
    v-if="hasContent"
    class="rich-content"
    :class="[inline ? 'rich-content--inline' : 'rich-content--block', isPlainText ? 'is-pre' : 'is-html']"
    v-html="rendered"
  />
  <span v-else class="rich-content rich-content--empty">{{ emptyText }}</span>
</template>

<script setup>
import { computed } from 'vue'
import katex from 'katex'
import 'katex/dist/katex.min.css'

/**
 * 统一富文本 + 数学公式渲染（任务 #19）。
 *
 * 题目内容（content / analysis / options）来自爬虫与批量导入，形态不固定：
 * 可能是纯文本、带 <table>/<img> 的 HTML 片段，也可能含 $x^2$ 或 \(x^2\) 公式。
 * 这里把它们收口成一条管线：**原文保真 + 白名单净化 + 公式渲染**。
 *
 * 三步顺序很关键：
 * 1. maskMath：先按定界符切出公式段并替换成 PUA 占位符（\uE000 序号 \uE001）。
 *    这一步在 HTML 解析之前，公式里的 `<`、`>`、`&` 才不会被 DOMParser 当成标签吃掉
 *    （例如 `$a<b$`）；同时用「是否在标签内」的扫描状态保证不碰属性值里的 $，
 *    避免把 `<img src="/api/file/1$2.png">` 识别成公式。
 * 2. sanitize：DOMParser 遍历净化（白名单标签 + 白名单属性，剥掉 script/style/iframe 与 on* 事件属性），
 *    非白名单标签做 unwrap（保留其中文本），保证内容不丢只掉壳。
 * 3. 占位符回填：把 KaTeX 的 HTML 结果插回原位。KaTeX 默认 trust:false，输出本身安全，
 *    因此不再走净化；渲染 throwOnError:false，单条公式解析失败也只影响这一条。
 *
 * 降级路径：KaTeX 不可用 / 公式解析失败时，公式段以原样文本显示（$x^2$ 可见不丢）；
 * DOMParser 不可用的环境退回文本转义。
 */
defineOptions({ name: 'RichContent' })

const props = defineProps({
  // 题目字段形态多样：数字/对象也接住，统一 String 化，避免调用点先做类型判断
  html: {
    type: [String, Number, Object, Array],
    default: ''
  },
  // inline：表格单元格、选项行等单行场景——不产生块级外边距，p 也按行内渲染
  inline: { type: Boolean, default: false },
  emptyText: { type: String, default: '' }
})

const DROP_TAGS = new Set([
  'script',
  'style',
  'iframe',
  'object',
  'embed',
  'link',
  'meta',
  'base',
  'form',
  'input',
  'button',
  'select',
  'textarea',
  'option',
  'fieldset',
  'svg',
  'math',
  'canvas',
  'template',
  'slot',
  'frame',
  'frameset',
  'audio',
  'video',
  'source',
  'track',
  'applet',
  'marquee',
  'noscript'
])

const ALLOWED_TAGS = new Set([
  'table',
  'thead',
  'tbody',
  'tr',
  'td',
  'th',
  'img',
  'b',
  'i',
  'em',
  'strong',
  'sub',
  'sup',
  'br',
  'p',
  'span',
  'ul',
  'ol',
  'li'
])

// 属性同样白名单：on* / style / class 一律掉，表格只留合并单元格信息，img 只留 src 与 alt
const ALLOWED_ATTRS = {
  img: ['src', 'alt'],
  td: ['colspan', 'rowspan'],
  th: ['colspan', 'rowspan']
}

const SCHEME_RE = /^[a-zA-Z][a-zA-Z0-9+.-]*:/
const PLACEHOLDER_RE = /\uE000(\d+)\uE001/g

const textOf = computed(() => {
  const v = props.html
  if (v === null || v === undefined) return ''
  if (typeof v === 'string') return v
  if (typeof v === 'object') return stringifyOptions(v)
  return String(v)
})

const hasContent = computed(() => textOf.value.trim() !== '')

/**
 * 对象/数组形态的选项也支持：{ A: 'x', B: 'y' } 或 ['x','y'] 渲染成 A. x / B. y 逐行。
 * 生成的片段仍要走净化，所以只用白名单标签（b / br），其余标量原样文本化。
 */
function stringifyOptions(val) {
  const list = Array.isArray(val) ? val.map((item, i) => [String.fromCharCode(65 + i), item]) : Object.entries(val)
  return list
    .filter(([, v]) => v !== null && v !== undefined && String(v).trim() !== '')
    .map(([k, v]) => `<b>${escapeText(String(k))}.</b> ${typeof v === 'object' ? escapeText(JSON.stringify(v)) : String(v)}`)
    .join('<br>')
}

function escapeText(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 公式段 → 占位符；返回 { html, math } —— math[i] 为 { tex, display, raw } */
function maskMath(src) {
  const math = []
  let out = ''
  let i = 0
  let inTag = false
  const n = src.length
  while (i < n) {
    const ch = src[i]
    if (inTag) {
      // 标签内部（含属性值）不做公式识别，直接原样带走
      out += ch
      if (ch === '>') inTag = false
      i += 1
      continue
    }
    if (ch === '<') {
      inTag = true
      out += ch
      i += 1
      continue
    }
    const hit = matchMathAt(src, i)
    if (hit) {
      math.push(hit)
      out += `\uE000${math.length - 1}\uE001`
      i = hit.end
      continue
    }
    out += ch
    i += 1
  }
  return { html: out, math }
}

/** 在位置 i 尝试匹配四种定界符；不匹配返回 null */
function matchMathAt(src, i) {
  // 转义的 \$ 视为普通字符
  if (src[i] === '$' && src[i - 1] === '\\') return null

  if (src.startsWith('$$', i)) {
    const close = src.indexOf('$$', i + 2)
    if (close > i + 2) {
      const tex = src.slice(i + 2, close)
      if (tex.trim()) return { tex, display: true, raw: src.slice(i, close + 2), end: close + 2 }
    }
    return null
  }
  if (src.startsWith('\\(', i)) {
    const close = src.indexOf('\\)', i + 2)
    if (close > i + 2) {
      const tex = src.slice(i + 2, close)
      if (tex.trim()) return { tex, display: false, raw: src.slice(i, close + 2), end: close + 2 }
    }
    return null
  }
  if (src.startsWith('\\[', i)) {
    const close = src.indexOf('\\]', i + 2)
    if (close > i + 2) {
      const tex = src.slice(i + 2, close)
      if (tex.trim()) return { tex, display: true, raw: src.slice(i, close + 2), end: close + 2 }
    }
    return null
  }
  if (src[i] === '$') {
    const m = /^\$([^$\n]+?)\$/.exec(src.slice(i, i + 200))
    if (m) {
      const tex = m[1]
      // 「$ 与 $ 之间不能紧贴空白」：挡住「定价 $5 与 $6」这类货币误判
      if (tex.trim() && !/^\s|\s$/.test(tex)) {
        return { tex, display: false, raw: m[0], end: i + m[0].length }
      }
    }
  }
  return null
}

/** 公式里的 HTML 实体先解回来：`$a &lt; b$` 也应当渲染成 a < b */
function decodeEntities(tex) {
  return tex
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
}

const canKatex = typeof katex?.renderToString === 'function'

function renderFormula(item) {
  if (!canKatex) return escapeText(item.raw) // 降级：公式原样文本显示
  try {
    return katex.renderToString(decodeEntities(item.tex), {
      displayMode: item.display,
      throwOnError: false,
      output: 'html' // 只出 HTML 视图，不带 MathML：表格 tooltip / 纯文本兜底不会重复一串源码
    })
  } catch (e) {
    return escapeText(item.raw) // 单条公式炸掉不影响整段
  }
}

function safeImgSrc(src) {
  const s = String(src || '').trim()
  if (!s) return false
  if (!SCHEME_RE.test(s)) return true // 相对路径：/api/file/x.png、uploads/x.png
  return /^https?:/i.test(s) // 绝对地址只留 http(s)，挡掉 data: / javascript: / file:
}

function cleanElement(el) {
  for (const child of Array.from(el.childNodes)) {
    if (child.nodeType === 3) continue // 文本节点保留
    if (child.nodeType !== 1) {
      child.remove() // 注释 / DOCTYPE 等
      continue
    }
    const tag = child.tagName.toLowerCase()
    if (DROP_TAGS.has(tag)) {
      child.remove()
      continue
    }
    if (!ALLOWED_TAGS.has(tag)) {
      // 非白名单：剥壳留内容（如 <a>、<font>、<div>），保证文字不丢
      cleanElement(child)
      while (child.firstChild) el.insertBefore(child.firstChild, child)
      child.remove()
      continue
    }
    const allowed = ALLOWED_ATTRS[tag] || []
    for (const attr of Array.from(child.attributes)) {
      const name = attr.name.toLowerCase()
      if (!allowed.includes(name)) child.removeAttribute(attr.name)
    }
    if (tag === 'img' && !safeImgSrc(child.getAttribute('src'))) {
      child.remove()
      continue
    }
    cleanElement(child)
  }
}

function sanitize(html) {
  if (typeof DOMParser === 'undefined') return escapeText(html)
  const doc = new DOMParser().parseFromString(html, 'text/html')
  cleanElement(doc.body)
  return doc.body.innerHTML
}

const parsed = computed(() => {
  const raw = textOf.value
  if (!raw) return { html: '', math: [] }
  // 公式先摘出来（占位符只出现在文本区，因此下面的「是否含标签」判断不会被公式里的 < 误判）
  return maskMath(raw)
})

// 纯文本题干保留换行（沿用各页原来的 pre-wrap 观感）；含标签的 HTML 交给浏览器折叠空白
const isPlainText = computed(() => !/<[a-zA-Z!/]/.test(parsed.value.html))

const rendered = computed(() => {
  const { html, math } = parsed.value
  if (!html) return ''
  if (isPlainText.value) return renderMathInText(escapeText(html), math)
  const clean = sanitize(html)
  // 回填按占位符序号查表：占位符可能随 unwrap 挪位置，序号始终对得上
  return clean.replace(PLACEHOLDER_RE, (_, idx) => renderFormula(math[Number(idx)] || { raw: '' }))
})

/** 纯文本分支：转义后逐段插回公式，等价于「文本 + KaTeX」的拼接 */
function renderMathInText(escapedHtml, math) {
  return escapedHtml.replace(PLACEHOLDER_RE, (_, idx) => renderFormula(math[Number(idx)] || { raw: '' }))
}
</script>

<style scoped>
/* 继承宿主字号/行高：管理端弹窗、答题页大字题干共用一套 */
.rich-content {
  color: inherit;
  word-break: break-word;
  overflow-wrap: anywhere;
}

/* 纯文本题干：保留学生/录入端的换行（等价于原来的 pre-wrap 插值） */
.rich-content.is-pre {
  white-space: pre-wrap;
}

/* HTML 题干：显式折叠空白，覆盖宿主容器的 pre-wrap，
   否则源码里标签之间的缩进会被渲染成一堆空行 */
.rich-content.is-html {
  white-space: normal;
}

.rich-content--inline {
  display: inline;
}

.rich-content--block :deep(p:first-child) {
  margin-top: 0;
}

.rich-content--block :deep(p:last-child) {
  margin-bottom: 0;
}

.rich-content :deep(p) {
  margin: 0 0 0.4em;
}

/* 行内模式：块级标签按行内排，避免把单行选项撑成多行 */
.rich-content--inline :deep(p),
.rich-content--inline :deep(ul),
.rich-content--inline :deep(ol),
.rich-content--inline :deep(li) {
  display: inline;
  margin: 0;
  padding: 0;
}

/* 行内模式遇到表格：保留表格语义（inline-table），只去掉块级外边距 */
.rich-content--inline :deep(table) {
  display: inline-table;
  margin: 0;
  vertical-align: middle;
}

.rich-content :deep(ul),
.rich-content :deep(ol) {
  margin: 0.3em 0 0.5em;
  padding-left: 1.6em;
}

.rich-content :deep(li) {
  margin: 0;
}

.rich-content :deep(sub),
.rich-content :deep(sup) {
  font-size: 0.75em;
}

/* ===== 表格：爬虫来的题表按视觉稿补边框与斑马纹，宽度撑满不溢出 ===== */
.rich-content :deep(table) {
  width: 100%;
  max-width: 100%;
  margin: 0.5em 0;
  border-collapse: collapse;
  table-layout: fixed; /* 长单元格不撑破沉浸布局，靠换行消化 */
  font-size: 0.95em;
}

.rich-content :deep(th),
.rich-content :deep(td) {
  border: 1px solid var(--zk-border);
  padding: 6px 8px;
  text-align: left;
  vertical-align: top;
  word-break: break-word;
}

.rich-content :deep(thead th) {
  background: var(--zk-primary-lighter);
  font-weight: 700;
}

.rich-content :deep(tbody tr:nth-child(even) td) {
  background: var(--zk-bg-page);
}

.rich-content :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 4px 0;
  border-radius: var(--zk-radius-sm);
}

/* 块级公式在窄屏（答题页手机态）横向滚动，不允许把版面顶宽 */
.rich-content :deep(.katex-display) {
  margin: 0.4em 0;
  overflow-x: auto;
  overflow-y: hidden;
  max-width: 100%;
}

.rich-content--empty {
  display: inline;
  color: var(--zk-text-placeholder);
}
</style>
