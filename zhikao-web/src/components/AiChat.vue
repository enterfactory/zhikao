<template>
  <div class="ai-chat">
    <!-- ==================== 消息流：唯一可滚动区域 ==================== -->
    <!--
      对齐主流 AI 产品的三段式中的「中段」：
      - 内容列 max-width 760px 居中，两侧自然留白；
      - 助手消息不再套重气泡，直接铺 Markdown 正文 + 左上角小头像；
      - 用户消息用浅底圆角块右对齐，弱化「聊天窗」而强调「文档流」。
    -->
    <div class="ai-chat__stage">
      <div ref="scrollRef" class="ai-chat__scroll" @scroll.passive="onScrollArea">
        <div class="ai-chat__stream">
          <!-- 空会话欢迎区：助手名 + 一句话介绍 + 推荐问题卡（2 列网格） -->
          <div v-if="!messages.length" class="ai-chat__welcome">
            <div class="ai-chat__welcome-mark">
              <el-icon :size="22"><MagicStick /></el-icon>
            </div>
            <h3 class="ai-chat__welcome-title">{{ currentAgent.label }}</h3>
            <p class="ai-chat__welcome-hint">{{ currentAgent.hint }}</p>
            <div v-if="currentAgent.samples?.length" class="ai-chat__cards">
              <button
                v-for="sample in currentAgent.samples.slice(0, 4)"
                :key="sample"
                class="ai-chat__card"
                @click="quickAsk(sample)"
              >
                <span class="ai-chat__card-text">{{ sample }}</span>
                <el-icon class="ai-chat__card-arrow"><Right /></el-icon>
              </button>
            </div>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="ai-chat__msg"
            :class="[
              `is-${message.role}`,
              { 'is-streaming': streaming && index === messages.length - 1 },
            ]"
          >
            <!-- 头像：助手常驻显示；用户侧只在浅底块上方留空位，保持栅格对齐 -->
            <div class="ai-chat__avatar" aria-hidden="true">
              <el-icon v-if="message.role === 'user'"><UserFilled /></el-icon>
              <el-icon v-else><MagicStick /></el-icon>
            </div>

            <div class="ai-chat__col">
              <!-- 工具调用轨迹：折叠展示「Agent 是怎么得出结论的」（逻辑与数据源未动） -->
              <div v-if="message.toolCalls?.length" class="ai-chat__tools">
                <el-collapse>
                  <el-collapse-item>
                    <template #title>
                      <span class="ai-chat__tools-title">
                        <el-icon><Connection /></el-icon>
                        已调用 {{ message.toolCalls.length }} 个工具
                      </span>
                    </template>
                    <div
                      v-for="(call, callIndex) in message.toolCalls"
                      :key="callIndex"
                      class="ai-chat__tool"
                    >
                      <div class="ai-chat__tool-head">
                        <el-tag size="small" effect="plain" type="primary">{{ call.name }}</el-tag>
                        <span class="zk-text-xs zk-text-placeholder">
                          {{ call.state === 'finished' ? '已返回' : '调用中…' }}
                        </span>
                      </div>
                      <pre v-if="call.resultSummary" class="ai-chat__tool-result">{{
                        call.resultSummary
                      }}</pre>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>

              <!-- 正文 -->
              <div class="ai-chat__content-wrap">
                <div
                  v-if="message.content"
                  class="ai-chat__content"
                  :class="{ 'zk-caret': streaming && index === messages.length - 1 }"
                  v-html="renderMarkdown(message.content)"
                />

                <!-- 思考中：三点脉冲（未收到任何 delta 时） -->
                <div
                  v-else-if="streaming && index === messages.length - 1"
                  class="ai-chat__thinking"
                >
                  <span class="zk-dot" />
                  <span class="zk-dot" />
                  <span class="zk-dot" />
                  <span class="ai-chat__thinking-text">{{ thinkingText }}</span>
                </div>

                <!-- hover 工具条：复制（纯前端能力，真实可用）。
                   「重新生成」未提供：后端没有重生成/删除末条消息的接口，
                   直接重发会在会话历史里留下重复轮次，故不放假按钮。 -->
                <div
                  v-if="message.content && !(streaming && index === messages.length - 1)"
                  class="ai-chat__toolbar"
                >
                  <button class="ai-chat__tool-btn" title="复制回答" @click="copyMessage(message)">
                    <el-icon><CopyDocument /></el-icon>
                    <span>{{ copiedIndex === index ? '已复制' : '复制' }}</span>
                  </button>
                  <span v-if="message.tokenUsage" class="ai-chat__usage">
                    {{ message.tokenUsage }} tokens
                  </span>
                </div>
                <div v-else-if="message.tokenUsage" class="ai-chat__toolbar">
                  <span class="ai-chat__usage">{{ message.tokenUsage }} tokens</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 用户上滚后浮出的「回到底部」 -->
      <Transition name="ai-fade">
        <button v-show="showJump" class="ai-chat__jump" title="回到底部" @click="backToBottom">
          <el-icon><ArrowDown /></el-icon>
        </button>
      </Transition>
    </div>

    <!-- ==================== 底部输入区 ==================== -->
    <footer class="ai-chat__footer">
      <div class="ai-chat__footer-inner">
        <!-- 快捷提问 chips：一行横滑（数据由页面传入，点击仍走 quickAsk 原链路） -->
        <div v-if="quickQuestions.length" class="ai-chat__chips">
          <button v-for="q in quickQuestions" :key="q" class="ai-chat__chip" @click="quickAsk(q)">
            {{ q }}
          </button>
        </div>

        <div class="ai-chat__composer" :class="{ 'is-disabled': !aiReady }">
          <el-input
            ref="inputRef"
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            resize="none"
            :disabled="!aiReady"
            :placeholder="
              aiReady
                ? '输入你的问题，Enter 发送，Shift+Enter 换行'
                : '对话服务暂不可用，请稍后重试'
            "
            @keydown.enter.exact.prevent="send"
          />

          <div class="ai-chat__composer-bar">
            <!-- 左下：助手 / 模型选择（沿用原 el-select 数据源与禁用规则）+ 状态标记 -->
            <div class="ai-chat__picker">
              <el-select
                v-model="agentType"
                size="small"
                class="ai-chat__agent-select"
                :style="{ width: agentOptions.length > 1 ? '150px' : '130px' }"
                :disabled="streaming || agentOptions.length <= 1"
              >
                <el-option
                  v-for="agent in agentOptions"
                  :key="agent.value"
                  :label="agent.label"
                  :value="agent.value"
                >
                  <div class="ai-chat__option">
                    <span>{{ agent.label }}</span>
                    <span class="zk-text-xs zk-text-placeholder">{{ agent.toolCount }} 项能力</span>
                  </div>
                </el-option>
              </el-select>
              <span class="ai-chat__status" :class="aiReady ? 'is-on' : 'is-off'">
                <i class="ai-chat__status-dot" />
                {{ aiReady ? '模型已启用' : '未配置模型' }}
              </span>
            </div>

            <!-- 右下：圆形发送 / 停止（stop 复用原 AbortController 收口逻辑） -->
            <div class="ai-chat__submit-wrap">
              <button
                v-if="streaming"
                class="ai-chat__submit is-stop"
                title="停止生成"
                @click="stop"
              >
                <el-icon :size="16"><VideoPause /></el-icon>
              </button>
              <button
                v-else
                class="ai-chat__submit"
                :class="{ 'is-ready': input.trim() && aiReady }"
                :disabled="!input.trim() || !aiReady"
                title="发送"
                @click="send"
              >
                <el-icon :size="16"><Promotion /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <p class="ai-chat__note">回答仅供参考，请核对教学内容与关键数据。</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowDown,
  Connection,
  CopyDocument,
  MagicStick,
  Promotion,
  Right,
  UserFilled,
  VideoPause,
} from '@element-plus/icons-vue'
import { aiConversations, aiMessages } from '@/api/ai'
import { renderMarkdown } from '@/utils/markdown'

/**
 * AI 对话容器。
 *
 * 本次只重构「视觉与交互层」，下列业务实现全部原样保留：
 * 1. **用 fetch + ReadableStream 而不是 EventSource**：EventSource 只支持 GET，
 *    而对话需要 POST 一段较长的消息体；fetch 流式读取是 SSE + POST 的标准解法。
 * 2. **AbortController 支持「停止生成」**：中断的不只是前端的渲染，
 *    连接断开后后端的 SseEmitter.onCompletion 会 dispose 掉模型订阅，真正省下 token。
 * 3. **收到 done 立刻收口**：不再等 TCP 连接自然关闭才把按钮切回发送态；
 *    done 事件一到就 finish()，同时主动 cancel 掉 reader。
 * 4. **布局：只有消息流滚动**。输入区固定底部，不随消息增长上下浮动。
 * 5. **工具调用可视化**：折叠展示工具名与返回摘要——这是「可解释性」。
 * 6. **看门狗降级 / SSE 失败回落同步接口**：逻辑未动（见 send / fallbackSync）。
 *
 * 新增的都是纯前端交互：跟随滚动开关 + 回到底部、复制回答、自动增高输入框。
 */

/** 各 Agent 的界面文案（能力与工具清单由后端 catalog 下发，这里只管「怎么说」） */
const AGENT_META = {
  coordinator: {
    label: '主控助手',
    hint: '通用助手：能直接查数据，也能把专项问题转给对应专家',
    staffSamples: [
      '系统里有哪些考试？各自什么状态？',
      '题库里 Java 程序设计有多少题？',
      '帮我看下最近的考试情况',
    ],
    studentSamples: ['我最近一次考试考了多少分？', '我哪个知识点最薄弱？', '给我出 3 道练习题'],
  },
  question: {
    label: '出题助手',
    hint: '按科目 / 知识点 / 题型 / 难度生成题目草稿，并做质量自检',
    samples: ['围绕 Java 集合框架出 2 道单选题，难度 3', '检索「索引与优化」这个知识点的考纲'],
  },
  mark: {
    label: '阅卷助手',
    hint: '为主观题给评分建议，并结合同题得分分布做一致性检查',
    samples: ['有哪些主观题还没批改？', '帮我看看各主观题的得分分布是否正常'],
  },
  analytics: {
    label: '学情分析',
    hint: '分析班级或个人学情，输出薄弱知识点与可执行的复习建议',
    staffSamples: ['分析最近这场考试，哪些知识点最薄弱', '给出下一步的教学建议'],
    studentSamples: ['我的薄弱知识点有哪些？该怎么复习？', '帮我分析一下我的错题'],
  },
  assistant: {
    label: '系统助手',
    hint: '用自然语言查询系统数据，并告诉你该去哪个页面操作',
    samples: ['有哪些科目？', '有多少场考试还没发布？', '题库一共多少道题？'],
  },
}

const props = defineProps({
  /** 后端是否已配置模型（由父页面探测后传入） */
  aiReady: { type: Boolean, default: true },
  /** 后端下发的可用 Agent 清单（按角色过滤过）；为空时回退到主控 */
  catalog: { type: Array, default: () => [] },
  /** 当前角色，用于挑选更贴合的示例问题 */
  role: { type: String, default: '' },
  /** 角色能力边界说明 */
  scope: { type: String, default: '' },
  /** 页面级快捷提问（渲染在输入框上方横滑一行；点击仍走 quickAsk） */
  quickQuestions: { type: Array, default: () => [] },
})

const messages = ref([])
const input = ref('')
const agentType = ref('coordinator')
const conversationId = ref(null)
const streaming = ref(false)
const thinkingText = ref('思考中…')
const scrollRef = ref(null)
const inputRef = ref(null)
const showJump = ref(false)
const copiedIndex = ref(-1)

/** 是否跟随新消息自动滚底：用户手动上滚即置 false，回到底部或发送时重新置 true */
let autoFollow = true

let controller = null
let watchdog = null
let copyResetTimer = null

/** 可用 Agent：优先用后端 catalog，拿不到时至少保留主控，保证界面可用 */
const agentOptions = computed(() => {
  const list = props.catalog?.length
    ? props.catalog
    : [{ code: 'coordinator', label: '主控助手', description: '', toolCount: 0 }]
  return list.map((item) => ({
    value: item.code,
    label: AGENT_META[item.code]?.label || item.label || item.code,
    hint: AGENT_META[item.code]?.hint || item.description || '',
    toolCount: item.toolCount ?? 0,
    samples: samplesOf(item.code),
  }))
})

function samplesOf(code) {
  const meta = AGENT_META[code] || {}
  if (meta.staffSamples || meta.studentSamples) {
    return props.role === 'student' ? meta.studentSamples : meta.staffSamples
  }
  return meta.samples || []
}

const currentAgent = computed(
  () => agentOptions.value.find((item) => item.value === agentType.value) || agentOptions.value[0],
)

// 角色切换（重新登录 / 切换账号）时，若当前 Agent 已不可用则回落到第一个
watch(agentOptions, (list) => {
  if (!list.some((item) => item.value === agentType.value)) {
    agentType.value = list[0]?.value || 'coordinator'
  }
})

/**
 * 滚动策略：
 * - force=true（用户刚发送 / 切换会话）无条件置底；
 * - 流式增量只在 autoFollow 时置底，用户上滚阅读历史时不打断。
 */
function scrollToBottom(force = false) {
  if (force) autoFollow = true
  if (!autoFollow) return
  nextTick(() => {
    const el = scrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 距底部小于阈值视为「在底部」，恢复自动跟随 */
function onScrollArea() {
  const el = scrollRef.value
  if (!el) return
  const gap = el.scrollHeight - el.scrollTop - el.clientHeight
  const atBottom = gap < 48
  if (atBottom) {
    autoFollow = true
    showJump.value = false
  } else {
    autoFollow = false
    showJump.value = true
  }
}

function backToBottom() {
  autoFollow = true
  showJump.value = false
  const el = scrollRef.value
  if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
}

/** 复制回答：clipboard 不可用时退回 execCommand（内网 http 环境没有 clipboard API） */
function copyMessage(message) {
  const text = message.content || ''
  if (!text) return
  const done = () => {
    copiedIndex.value = messages.value.indexOf(message)
    if (copyResetTimer) clearTimeout(copyResetTimer)
    copyResetTimer = setTimeout(() => (copiedIndex.value = -1), 1600)
  }
  if (navigator.clipboard?.writeText) {
    navigator.clipboard
      .writeText(text)
      .then(done)
      .catch(() => legacyCopy(text, done))
  } else {
    legacyCopy(text, done)
  }
}

function legacyCopy(text, done) {
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    done()
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

function newConversation() {
  if (streaming.value) return
  conversationId.value = null
  messages.value = []
  autoFollow = true
  showJump.value = false
  // 新建后把光标交给输入框：主流产品的「新对话」即开始输入
  nextTick(() => inputRef.value?.focus?.())
}

function quickAsk(text) {
  input.value = text
  send()
}

/** 统一收口：无论是正常结束、手动中断还是异常，状态只在这里复位 */
function finish(assistant, notice) {
  streaming.value = false
  clearWatchdog()
  if (controller) {
    controller.abort()
    controller = null
  }
  if (assistant && notice && !assistant.content) {
    assistant.content = notice
  }
}

function stop() {
  const last = messages.value[messages.value.length - 1]
  const target = last && last.role === 'assistant' ? last : null
  if (target && !target.content) {
    target.content = '（已停止生成）'
  }
  finish(target)
}

/**
 * 看门狗：超过 N 秒没有任何事件就认为链路已死。
 * 没有它的话，「后端挂了但连接没断」会让按钮永远停在「停止生成」。
 */
function resetWatchdog(assistant) {
  clearWatchdog()
  watchdog = setTimeout(() => {
    const target = messages.value[messages.value.length - 1]
    finish(
      target && target.role === 'assistant' ? target : assistant,
      '⚠️ 响应超时，已停止等待。请稍后重试。',
    )
  }, IDLE_TIMEOUT_MS)
}

function clearWatchdog() {
  if (watchdog) {
    clearTimeout(watchdog)
    watchdog = null
  }
}

const IDLE_TIMEOUT_MS = 120000

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return

  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', toolCalls: [] })
  input.value = ''
  streaming.value = true
  thinkingText.value = '思考中…'
  scrollToBottom(true)

  const assistant = messages.value[messages.value.length - 1]
  controller = new AbortController()
  resetWatchdog(assistant)

  try {
    await streamChat(text, assistant)
  } catch (error) {
    if (error.name === 'AbortError') {
      // 主动停止：状态已在 stop() 里收口
    } else {
      await fallbackSync(text, assistant, error)
    }
  } finally {
    finish(assistant)
    scrollToBottom()
  }
}

/** 流式：fetch + ReadableStream 解析 SSE */
async function streamChat(text, assistant) {
  const token = localStorage.getItem('token')
  const response = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      satoken: token || '',
    },
    body: JSON.stringify({
      conversationId: conversationId.value,
      agentType: agentType.value,
      message: text,
    }),
    signal: controller.signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`流式接口不可用（HTTP ${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let done = false

  while (!done) {
    const chunk = await reader.read()
    if (chunk.done) break
    resetWatchdog(assistant)
    buffer += decoder.decode(chunk.value, { stream: true })

    // SSE 以「空行」分隔事件块
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      if (handleSseBlock(block, assistant)) {
        // 收到 done：立刻收口，不让「停止生成」按钮继续挂着
        done = true
        break
      }
    }
  }
  if (!done && buffer.trim()) {
    handleSseBlock(buffer, assistant)
  }
  // 主动释放连接，避免 socket 停在池里等超时
  reader.cancel().catch(() => {})
}

/**
 * 解析单个 SSE 事件块：event: xxx / data: {...}
 * @returns {boolean} 是否为终止事件（done / error）
 */
function handleSseBlock(block, assistant) {
  let event = 'message'
  const dataLines = []
  block.split('\n').forEach((line) => {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  })
  if (!dataLines.length) return false

  let payload
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch (e) {
    return false
  }

  if (event === 'meta') {
    conversationId.value = payload.conversationId
    if (payload.agentLabel) {
      thinkingText.value = `${payload.agentLabel} 正在处理…`
    }
    return false
  }
  if (event === 'delta') {
    assistant.content += payload.text || ''
    thinkingText.value = '正在生成回答…'
    scrollToBottom()
    return false
  }
  if (event === 'tool_call') {
    assistant.toolCalls = assistant.toolCalls || []
    assistant.toolCalls.push({ name: payload.name, state: 'started', resultSummary: '' })
    thinkingText.value = `正在调用工具 ${payload.name}…`
    scrollToBottom()
    return false
  }
  if (event === 'tool_result') {
    const target = [...(assistant.toolCalls || [])]
      .reverse()
      .find((item) => item.name === payload.name)
    if (target) {
      target.state = 'finished'
      target.resultSummary = payload.resultSummary || ''
    }
    return false
  }
  if (event === 'done') {
    if (payload.conversationId) conversationId.value = payload.conversationId
    if (payload.tokenUsage) assistant.tokenUsage = payload.tokenUsage
    return true
  }
  if (event === 'error') {
    assistant.content = assistant.content || `⚠️ ${payload.message || 'AI 调用失败'}`
    return true
  }
  return false
}

/** 降级：SSE 不可用时走同步接口 */
async function fallbackSync(text, assistant, originalError) {
  try {
    const { aiChat } = await import('@/api/ai')
    const result = await aiChat({
      conversationId: conversationId.value,
      agentType: agentType.value,
      message: text,
    })
    conversationId.value = result.conversationId
    assistant.content = result.content || '（模型未返回内容）'
    assistant.toolCalls = result.toolCalls || []
    assistant.tokenUsage = result.tokenUsage
    if (!result.aiGenerated) {
      ElMessage.warning('当前为降级回复（未配置模型）')
    }
  } catch (error) {
    assistant.content = `⚠️ 对话失败：${error.message || originalError.message}`
  }
}

/** 切换会话时载入历史（供父组件通过 ref 调用） */
async function loadConversation(id) {
  const list = await aiMessages(id)
  conversationId.value = id
  messages.value = list.map((item) => ({
    role: item.role,
    content: item.content,
    toolCalls: item.toolCalls || [],
    tokenUsage: item.tokenUsage,
  }))
  scrollToBottom(true)
}

/** quickAsk 供页面级快捷提问 chips 复用（只是填框并发送，不新增逻辑） */
defineExpose({ loadConversation, newConversation, quickAsk })

onMounted(async () => {
  try {
    const list = await aiConversations()
    if (list?.length) {
      // 默认打开最近一次会话，避免每次进来都是空白
      await loadConversation(list[0].id)
      agentType.value = list[0].agentType || 'coordinator'
    }
  } catch (e) {
    // 会话列表拉取失败不影响新建对话
  }
})

// 组件卸载（切页）时务必中断：否则连接与定时器会留在后台
onBeforeUnmount(() => {
  clearWatchdog()
  if (copyResetTimer) clearTimeout(copyResetTimer)
  if (controller) {
    controller.abort()
    controller = null
  }
})
</script>

<style scoped>
/* ===== 容器 =====
   两栏中的右栏：整块高度由父级（页面工作区）给定，内部只有消息流滚动。 */
.ai-chat {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--zk-bg-card);
  overflow: hidden;
}

/* 舞台层：承载滚动区 + 悬浮的「回到底部」按钮 */
.ai-chat__stage {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
}

.ai-chat__scroll {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

/* 内容列：主流 AI 产品一致的 760px 居中阅读宽度 */
.ai-chat__stream {
  max-width: 760px;
  margin: 0 auto;
  padding: 28px var(--zk-space-5) 36px;
  display: flex;
  flex-direction: column;
  gap: 26px;
}

/* ==================== 空会话欢迎区 ==================== */
.ai-chat__welcome {
  padding: 8vh var(--zk-space-3) var(--zk-space-5);
  text-align: center;
}

.ai-chat__welcome-mark {
  width: 46px;
  height: 46px;
  margin: 0 auto var(--zk-space-4);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zk-primary-light);
  color: var(--zk-primary);
}

.ai-chat__welcome-title {
  font-size: var(--zk-font-xl);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.ai-chat__welcome-hint {
  margin-top: var(--zk-space-2);
  font-size: var(--zk-font-base);
  color: var(--zk-text-secondary);
  line-height: 1.7;
}

/* 推荐问题卡：2 列网格，点击即发送 */
.ai-chat__cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zk-space-3);
  margin-top: var(--zk-space-6);
  text-align: left;
}

.ai-chat__card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-2);
  padding: 14px var(--zk-space-4);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-lg);
  background: var(--zk-bg-card);
  color: var(--zk-text-regular);
  font-size: var(--zk-font-sm);
  font-family: inherit;
  line-height: 1.6;
  cursor: pointer;
  text-align: left;
  transition:
    border-color var(--zk-duration-fast) var(--zk-ease),
    box-shadow var(--zk-duration-fast) var(--zk-ease),
    color var(--zk-duration-fast) var(--zk-ease);
}

.ai-chat__card:hover {
  border-color: var(--zk-primary);
  color: var(--zk-primary);
  box-shadow: var(--zk-shadow-md);
}

.ai-chat__card-arrow {
  flex: 0 0 auto;
  opacity: 0.5;
}

/* ==================== 消息 ==================== */
.ai-chat__msg {
  display: flex;
  gap: var(--zk-space-3);
  align-items: flex-start;
}

.ai-chat__avatar {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--zk-primary-light);
  color: var(--zk-primary);
  font-size: 14px;
}

.ai-chat__col {
  flex: 1 1 auto;
  min-width: 0;
}

/* 助手：无气泡，正文直接铺在页面上（ChatGPT / DeepSeek 的一致做法） */
.ai-chat__content {
  word-break: break-word;
  color: var(--zk-text-primary);
  font-size: var(--zk-font-base);
  line-height: 1.75;
}

.ai-chat__toolbar {
  display: flex;
  align-items: center;
  gap: var(--zk-space-3);
  margin-top: 6px;
  height: 24px;
  opacity: 0;
  transition: opacity var(--zk-duration-fast) var(--zk-ease);
}

.ai-chat__msg:hover .ai-chat__toolbar {
  opacity: 1;
}

.ai-chat__tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: none;
  border-radius: var(--zk-radius-sm);
  background: none;
  color: var(--zk-text-placeholder);
  font-size: var(--zk-font-xs);
  font-family: inherit;
  cursor: pointer;
  transition:
    background-color var(--zk-duration-fast) var(--zk-ease),
    color var(--zk-duration-fast) var(--zk-ease);
}

.ai-chat__tool-btn:hover {
  background: var(--zk-bg-hover);
  color: var(--zk-text-regular);
}

.ai-chat__usage {
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

/* 用户消息：右对齐浅底圆角块，不铺满阅读宽度 */
.ai-chat__msg.is-user {
  flex-direction: row-reverse;
}

.ai-chat__msg.is-user .ai-chat__avatar {
  background: var(--zk-bg-hover);
  color: var(--zk-text-secondary);
}

.ai-chat__msg.is-user .ai-chat__col {
  flex: 0 1 auto;
  max-width: 78%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.ai-chat__msg.is-user .ai-chat__content-wrap {
  width: 100%;
}

.ai-chat__msg.is-user .ai-chat__content {
  width: fit-content;
  max-width: 100%;
  margin-left: auto;
  padding: 10px 16px;
  border-radius: 18px;
  background: var(--zk-primary-light);
  color: var(--zk-text-primary);
}

.ai-chat__msg.is-user .ai-chat__toolbar {
  justify-content: flex-end;
}

/* ==================== 工具调用轨迹 ==================== */
.ai-chat__tools {
  margin-bottom: var(--zk-space-2);
}

.ai-chat__tools-title {
  display: inline-flex;
  align-items: center;
  gap: var(--zk-space-1);
  font-size: var(--zk-font-xs);
  color: var(--zk-primary);
}

.ai-chat__tool {
  padding: var(--zk-space-2);
  margin-bottom: var(--zk-space-1);
  background: var(--zk-bg-hover);
  border-radius: var(--zk-radius-sm);
}

.ai-chat__tool-head {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
}

.ai-chat__tool-result {
  margin: var(--zk-space-1) 0 0;
  max-height: 96px;
  overflow: auto;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

/* ==================== Markdown 正文排版 ==================== */
.ai-chat__content :deep(.md-heading) {
  margin: var(--zk-space-3) 0 var(--zk-space-2);
  font-size: var(--zk-font-md);
  color: var(--zk-text-primary);
}

.ai-chat__content :deep(.md-p) {
  margin: var(--zk-space-2) 0;
  line-height: 1.75;
}

.ai-chat__content :deep(.md-list) {
  padding-left: 1.3em;
  list-style: disc;
  margin: var(--zk-space-2) 0;
  line-height: 1.75;
}

.ai-chat__content :deep(ol.md-list) {
  list-style: decimal;
}

.ai-chat__content :deep(.md-code) {
  padding: 2px 6px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-bg-hover);
  font-size: 0.9em;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.ai-chat__content :deep(.md-pre) {
  padding: var(--zk-space-3);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-sidebar);
  color: #e5e7eb;
  font-size: var(--zk-font-xs);
  overflow-x: auto;
}

.ai-chat__content :deep(.md-table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--zk-space-2) 0;
  font-size: var(--zk-font-sm);
}

.ai-chat__content :deep(.md-table th),
.ai-chat__content :deep(.md-table td) {
  border: 1px solid var(--zk-border);
  padding: 6px 10px;
  text-align: left;
}

.ai-chat__content :deep(.md-table th) {
  background: var(--zk-bg-hover);
}

/* ==================== 思考中 ==================== */
.ai-chat__thinking {
  display: flex;
  align-items: center;
  gap: var(--zk-space-1);
  color: var(--zk-primary);
}

.ai-chat__thinking-text {
  margin-left: var(--zk-space-1);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

/* ==================== 回到底部 ==================== */
.ai-chat__jump {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--zk-border);
  border-radius: 999px;
  background: var(--zk-bg-card);
  color: var(--zk-text-secondary);
  cursor: pointer;
  box-shadow: var(--zk-shadow-md);
  transition:
    color var(--zk-duration-fast) var(--zk-ease),
    border-color var(--zk-duration-fast) var(--zk-ease);
}

.ai-chat__jump:hover {
  color: var(--zk-primary);
  border-color: var(--zk-primary);
}

.ai-fade-enter-active,
.ai-fade-leave-active {
  transition:
    opacity var(--zk-duration-base) var(--zk-ease),
    transform var(--zk-duration-base) var(--zk-ease);
}

.ai-fade-enter-from,
.ai-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 6px);
}

/* ==================== 底部输入区 ==================== */
.ai-chat__footer {
  flex: 0 0 auto;
  padding: var(--zk-space-3) var(--zk-space-5) var(--zk-space-4);
  background: var(--zk-bg-card);
  border-top: 1px solid var(--zk-border-light);
}

.ai-chat__footer-inner {
  max-width: 760px;
  margin: 0 auto;
}

/* 快捷 chips：一行横滑，窄屏不折行 */
.ai-chat__chips {
  display: flex;
  gap: var(--zk-space-2);
  overflow-x: auto;
  padding-bottom: var(--zk-space-2);
  scrollbar-width: none;
}

.ai-chat__chips::-webkit-scrollbar {
  display: none;
}

.ai-chat__chip {
  flex: 0 0 auto;
  border: 1px dashed var(--zk-border-strong);
  border-radius: 999px;
  padding: 5px 14px;
  font-size: var(--zk-font-sm);
  color: var(--zk-text-regular);
  font-weight: 500;
  background: none;
  font-family: inherit;
  cursor: pointer;
  transition: all var(--zk-duration-fast) var(--zk-ease);
  white-space: nowrap;
}

.ai-chat__chip:hover {
  border-color: var(--zk-primary);
  border-style: solid;
  color: var(--zk-primary);
}

/* 输入容器：整体一块浅底圆角，聚焦时描边高亮 */
.ai-chat__composer {
  border: 1.5px solid var(--zk-border);
  border-radius: var(--zk-radius-xl);
  background: var(--zk-bg-hover);
  padding: var(--zk-space-2) var(--zk-space-3) var(--zk-space-2);
  transition:
    border-color var(--zk-duration-fast) var(--zk-ease),
    background-color var(--zk-duration-fast) var(--zk-ease),
    box-shadow var(--zk-duration-fast) var(--zk-ease);
}

.ai-chat__composer:focus-within {
  border-color: var(--zk-primary);
  background: var(--zk-bg-card);
  box-shadow: 0 0 0 3px var(--zk-primary-lighter);
}

.ai-chat__composer.is-disabled {
  opacity: 0.7;
}

.ai-chat__composer :deep(.el-textarea__inner) {
  background: transparent;
  border: none;
  box-shadow: none !important;
  padding: var(--zk-space-1) var(--zk-space-2);
  font-size: var(--zk-font-base);
  line-height: 1.7;
  color: var(--zk-text-primary);
}

.ai-chat__composer :deep(.el-textarea__inner::placeholder) {
  color: var(--zk-text-placeholder);
}

.ai-chat__composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-3);
  margin-top: var(--zk-space-1);
}

.ai-chat__picker {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  min-width: 0;
}

.ai-chat__agent-select :deep(.el-select__wrapper) {
  box-shadow: none;
  background: transparent;
  padding-left: var(--zk-space-1);
}

.ai-chat__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-4);
}

/* 模型状态：小圆点 + 文案，替代原来的 el-tag 双标签 */
.ai-chat__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
  white-space: nowrap;
}

.ai-chat__status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--zk-text-placeholder);
}

.ai-chat__status.is-on .ai-chat__status-dot {
  background: var(--zk-success);
}

.ai-chat__status.is-off .ai-chat__status-dot {
  background: var(--zk-warning);
}

/* 圆形发送按钮：空输入灰、可用主色、生成中转停止态 */
.ai-chat__submit {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: var(--zk-border-strong);
  color: var(--zk-text-inverse);
  cursor: not-allowed;
  transition:
    background-color var(--zk-duration-fast) var(--zk-ease),
    transform var(--zk-duration-fast) var(--zk-ease);
}

.ai-chat__submit.is-ready {
  background: var(--zk-primary);
  cursor: pointer;
}

.ai-chat__submit.is-ready:hover {
  background: var(--zk-primary-hover);
  transform: translateY(-1px);
}

.ai-chat__submit.is-stop {
  background: var(--zk-danger);
  cursor: pointer;
}

.ai-chat__submit.is-stop:hover {
  background: #b91c1c;
}

.ai-chat__note {
  margin-top: var(--zk-space-2);
  text-align: center;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
  line-height: 1.6;
}

/* 窄屏：阅读列与输入列同宽收缩，禁止横向溢出 */
@media (max-width: 720px) {
  .ai-chat__stream,
  .ai-chat__footer-inner {
    padding-left: var(--zk-space-3);
    padding-right: var(--zk-space-3);
  }

  .ai-chat__cards {
    grid-template-columns: minmax(0, 1fr);
  }

  .ai-chat__footer {
    padding-left: var(--zk-space-3);
    padding-right: var(--zk-space-3);
  }
}
</style>
