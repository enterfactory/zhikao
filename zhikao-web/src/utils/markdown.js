/**
 * 轻量 Markdown 渲染（够用即可，不引第三方库）。
 *
 * 为什么自己写：
 * - 引入 marked + DOMPurify 会让包体增加 ~120KB，而这里只需要渲染
 *   大模型最常见的几类输出（标题、列表、表格、粗体、行内代码、代码块）；
 * - 更重要的是**安全边界自己可控**：先整体转义 HTML，再做结构化替换，
 *   因此模型即使吐出 `<script>` 也只会显示为文本，不存在 XSS 面。
 *
 * 设计顺序不可颠倒：**先转义，后解析**。反过来就是把模型输出当 HTML 执行。
 */

/** HTML 转义：所有渲染的起点 */
export function escapeHtml(text) {
  if (text === null || text === undefined) return ''
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 行内格式：粗体、斜体、行内代码 */
function inline(text) {
  return text
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>')
    .replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
}

/** 表格行：| a | b | */
function isTableRow(line) {
  return /^\s*\|.*\|\s*$/.test(line)
}

function parseTableRow(line) {
  return line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim())
}

/**
 * 渲染 Markdown 为安全 HTML 字符串。
 * @param {string} source 模型输出的原始文本
 */
export function renderMarkdown(source) {
  if (!source) return ''
  const lines = escapeHtml(source).split('\n')
  const html = []

  let index = 0
  let listType = null // 'ul' | 'ol' | null

  const closeList = () => {
    if (listType) {
      html.push(`</${listType}>`)
      listType = null
    }
  }

  while (index < lines.length) {
    const line = lines[index]

    // ===== 代码块 =====
    if (line.trim().startsWith('```')) {
      closeList()
      const buffer = []
      index++
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        buffer.push(lines[index])
        index++
      }
      index++ // 跳过结束标记
      html.push(`<pre class="md-pre"><code>${buffer.join('\n')}</code></pre>`)
      continue
    }

    // ===== 表格 =====
    if (isTableRow(line) && index + 1 < lines.length && /^\s*\|[\s:|-]+\|\s*$/.test(lines[index + 1])) {
      closeList()
      const header = parseTableRow(line)
      index += 2 // 跳过表头与分隔行
      const body = []
      while (index < lines.length && isTableRow(lines[index])) {
        body.push(parseTableRow(lines[index]))
        index++
      }
      const thead = `<thead><tr>${header.map((cell) => `<th>${inline(cell)}</th>`).join('')}</tr></thead>`
      const tbody = `<tbody>${body
        .map((row) => `<tr>${row.map((cell) => `<td>${inline(cell)}</td>`).join('')}</tr>`)
        .join('')}</tbody>`
      html.push(`<table class="md-table">${thead}${tbody}</table>`)
      continue
    }

    // ===== 标题 =====
    const heading = /^(#{1,4})\s+(.*)$/.exec(line)
    if (heading) {
      closeList()
      const level = heading[1].length + 2 // h3-h6：页面里已有 h1/h2，避免层级冲突
      html.push(`<h${level} class="md-heading">${inline(heading[2])}</h${level}>`)
      index++
      continue
    }

    // ===== 无序列表 =====
    const ul = /^\s*[-*+]\s+(.*)$/.exec(line)
    if (ul) {
      if (listType !== 'ul') {
        closeList()
        html.push('<ul class="md-list">')
        listType = 'ul'
      }
      html.push(`<li>${inline(ul[1])}</li>`)
      index++
      continue
    }

    // ===== 有序列表 =====
    const ol = /^\s*\d+[.)]\s+(.*)$/.exec(line)
    if (ol) {
      if (listType !== 'ol') {
        closeList()
        html.push('<ol class="md-list">')
        listType = 'ol'
      }
      html.push(`<li>${inline(ol[1])}</li>`)
      index++
      continue
    }

    // ===== 空行 =====
    if (!line.trim()) {
      closeList()
      index++
      continue
    }

    // ===== 普通段落 =====
    closeList()
    html.push(`<p class="md-p">${inline(line)}</p>`)
    index++
  }

  closeList()
  return html.join('')
}

/** 预览用：把 markdown 压成纯文本（会话标题、折叠摘要） */
export function toPlainText(source, maxLength = 80) {
  if (!source) return ''
  const flat = String(source)
    .replace(/```[\s\S]*?```/g, '')
    .replace(/[#*`>|-]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return flat.length <= maxLength ? flat : flat.substring(0, maxLength) + '...'
}
