<template>
  <!--
    智能助手页面壳：对标主流 AI 对话产品的三段式（左栏会话 / 中央消息流 / 底部输入）。
    本次重构只重排「视觉与交互层」：会话增删、切换、快捷提问、状态探测的业务逻辑原样保留。
    移除了 AppPage 的「标题 + 描述」页头——进入即是全屏对话工作区，与 ChatGPT / Kimi 一致。
  -->
  <div class="ai-chat-page console-chat-workspace" :class="{ 'is-drawer-open': drawerOpen }">
    <!-- ≤900px 时左栏变浮层抽屉，需要遮罩承接点击 -->
    <div v-if="drawerOpen" class="ai-chat-page__mask" @click="drawerOpen = false" />

    <!-- ==================== 左栏：历史会话 ==================== -->
    <aside class="ai-side" :class="{ 'is-collapsed': asideCollapsed }">
      <!-- 展开态 -->
      <template v-if="!asideCollapsed">
        <div class="ai-side__top">
          <el-button class="ai-side__new" type="primary" :icon="Plus" @click="newChat">
            新建对话
          </el-button>
          <button class="ai-side__collapse" title="收起侧栏" @click="toggleAside">
            <el-icon :size="16"><DArrowLeft /></el-icon>
          </button>
        </div>

        <el-scrollbar class="ai-side__list">
          <EmptyState
            v-if="!conversations.length"
            title="还没有对话"
            description="从右侧开始你的第一次提问"
          />
          <!-- 时间分组：今天 / 昨天 / 更早（updateTime 缺失时归入更早，不猜测） -->
          <div v-for="group in groupedConversations" :key="group.label" class="ai-side__group">
            <div class="ai-side__group-label">{{ group.label }}</div>
            <div
              v-for="item in group.items"
              :key="item.id"
              class="ai-side__conv"
              :class="{ 'is-active': item.id === activeId }"
              @click="openConversation(item)"
            >
              <div class="ai-side__conv-main">
                <div class="ai-side__conv-title zk-ellipsis">{{ item.title || '未命名对话' }}</div>
                <div class="ai-side__conv-meta">
                  {{ item.agentLabel }} · {{ item.messageCount }} 条
                </div>
              </div>
              <!-- hover 才出现的操作：删除（后端有接口）。
                   重命名后端未提供接口，故不放假按钮。 -->
              <div class="ai-side__conv-actions">
                <button
                  class="ai-side__conv-del"
                  title="删除会话"
                  @click.stop="removeConversation(item)"
                >
                  <el-icon :size="14"><Delete /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </el-scrollbar>

        <!-- 能力边界：由后端下发，前端不重复判断角色 -->
        <div v-if="scope" class="ai-side__scope">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ scope }}</span>
        </div>
      </template>

      <!-- 收起态：56px 图标条 -->
      <div v-else class="ai-side__mini">
        <button class="ai-side__mini-btn" title="展开侧栏" @click="toggleAside">
          <el-icon :size="16"><Expand /></el-icon>
        </button>
        <button class="ai-side__mini-btn is-primary" title="新建对话" @click="newChat">
          <el-icon :size="16"><Plus /></el-icon>
        </button>
        <button
          v-for="item in conversations.slice(0, 12)"
          :key="item.id"
          class="ai-side__mini-item"
          :class="{ 'is-active': item.id === activeId }"
          :title="item.title || '未命名对话'"
          @click="openConversation(item)"
        >
          <el-icon :size="14"><ChatDotRound /></el-icon>
        </button>
      </div>
    </aside>

    <!-- ==================== 右栏：对话工作区 ==================== -->
    <section class="ai-main">
      <!-- 窄屏工具条：左栏已收成浮层，这里给出呼出入口（≥901px 隐藏） -->
      <div class="ai-main__bar">
        <button class="ai-main__open" @click="openDrawer">
          <el-icon :size="14"><Expand /></el-icon>
          <span>历史会话</span>
        </button>
      </div>

      <AiChat
        ref="chatRef"
        :ai-ready="aiReady"
        :catalog="catalog"
        :role="role"
        :scope="scope"
        :quick-questions="quickQuestions"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ChatDotRound, DArrowLeft, Delete, Expand, InfoFilled, Plus } from '@element-plus/icons-vue'
import AiChat from '@/components/AiChat.vue'
import EmptyState from '@/components/EmptyState.vue'
import { aiCatalog, aiConversations, aiDeleteConversation, aiStatus } from '@/api/ai'

// 组件名与路由 name 一致，keep-alive 才能命中（见 layout 的 cachedViews）
defineOptions({ name: 'AiChat' })

/**
 * 页面壳职责：
 * - 拉取会话列表 / 能力清单 / 模型状态（探测再断言，不写死 ai-ready）；
 * - 会话的打开、新建、删除，全部通过 chatRef 调 AiChat 暴露的原方法；
 * - 学生快捷提问数据仍在这里维护，传入 AiChat 渲染在输入框上方。
 *
 * catalog 由后端按当前登录角色过滤后下发，前端不做「是不是学生」的越权判断。
 */
const chatRef = ref(null)
const conversations = ref([])
const activeId = ref(null)
const aiReady = ref(true)
const catalog = ref([])
const role = ref('')
const scope = ref('')

/** 左栏折叠态（桌面）与浮层抽屉态（≤900px） */
const asideCollapsed = ref(false)
const drawerOpen = ref(false)
const NARROW = '(max-width: 900px)'
const narrow = typeof window !== 'undefined' ? window.matchMedia(NARROW) : null
// 断点回调需成对解绑，故存具名引用
const onNarrowChange = () => {
  drawerOpen.value = false
  // 跨过断点时回到展开态：否则窄屏抽屉里会显示 56px 图标条（浮层无折叠概念）
  asideCollapsed.value = false
}

/** 学生端快捷提问（视觉稿屏 5 的 chips；仅 role==='student' 时下发给输入区） */
const QUICK_STUDENT = [
  '给我出 3 道薄弱点练习题',
  '帮我分析最近的错题原因',
  '考前我应该重点复习什么？',
]

const quickQuestions = computed(() => (role.value === 'student' ? QUICK_STUDENT : []))

function isNarrow() {
  return Boolean(narrow?.matches)
}

/** 同一个开关：窄屏开浮层，宽屏切折叠条 */
function toggleAside() {
  if (isNarrow()) {
    drawerOpen.value = !drawerOpen.value
    return
  }
  asideCollapsed.value = !asideCollapsed.value
}

function openDrawer() {
  drawerOpen.value = true
}

/** 按 updateTime 归组；字段缺失（旧数据）时落到「更早」，不做无依据的推断 */
const groupedConversations = computed(() => {
  const today = []
  const yesterday = []
  const earlier = []
  const now = new Date()
  const dayKey = (d) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
  const todayKey = dayKey(now)
  const yest = new Date(now.getTime() - 86400000)
  const yestKey = dayKey(yest)

  conversations.value.forEach((item) => {
    const raw = item.updateTime || item.createTime
    if (!raw) {
      earlier.push(item)
      return
    }
    const d = new Date(raw)
    if (Number.isNaN(d.getTime())) {
      earlier.push(item)
      return
    }
    const key = dayKey(d)
    if (key === todayKey) today.push(item)
    else if (key === yestKey) yesterday.push(item)
    else earlier.push(item)
  })

  const groups = []
  if (today.length) groups.push({ label: '今天', items: today })
  if (yesterday.length) groups.push({ label: '昨天', items: yesterday })
  if (earlier.length) groups.push({ label: '更早', items: earlier })
  return groups
})

async function loadConversations() {
  try {
    conversations.value = await aiConversations()
  } catch (e) {
    conversations.value = []
  }
}

function newChat() {
  activeId.value = null
  chatRef.value?.newConversation()
  if (isNarrow()) drawerOpen.value = false
}

async function openConversation(item) {
  activeId.value = item.id
  await chatRef.value?.loadConversation(item.id)
  if (isNarrow()) drawerOpen.value = false
}

async function removeConversation(item) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${item.title || '未命名对话'}」吗？`, '提示', {
      type: 'warning',
    })
  } catch (e) {
    return
  }
  await aiDeleteConversation(item.id)
  ElMessage.success('已删除')
  if (activeId.value === item.id) {
    newChat()
  }
  await loadConversations()
}

onMounted(async () => {
  await loadConversations()
  // 能力清单与模型状态互不依赖，失败也不互相牵连
  try {
    const data = await aiCatalog()
    catalog.value = data?.agents || []
    role.value = data?.role || ''
    scope.value = data?.scope || ''
  } catch (e) {
    catalog.value = []
  }
  try {
    const status = await aiStatus()
    aiReady.value = Boolean(status?.enabled)
  } catch (e) {
    aiReady.value = false
  }
  // 跨过 900px 断点时清掉浮层态并回到展开，避免遮罩残留 / 抽屉里显示折叠条
  narrow?.addEventListener?.('change', onNarrowChange)
})

onBeforeUnmount(() => {
  narrow?.removeEventListener?.('change', onNarrowChange)
})
</script>

<style scoped>
/* ===== 工作区：全屏三段式 =====
   高度 = 视口 - 壳层头部/内边距（壳不同则不同，见底部 .portal 覆写）。
   min-height 兜底，避免极矮视口下输入区被挤没。
   --ai-neg：抵消布局内容区 padding 的负边距，需与 ConsoleLayout 各断点的
   padding（24 / 16 / 12）保持一致，否则左右会露出底色缝隙。 */
.ai-chat-page {
  --ai-neg: var(--zk-space-5);
  position: relative;
  display: flex;
  gap: var(--zk-space-3);
  height: calc(100vh - var(--ai-shell-offset, 144px));
  min-height: 460px;
  margin: calc(var(--ai-neg) * -1);
  padding: var(--zk-space-3);
  /* 工作区底色与左栏同族（浅灰），对话区是浮在其上的白面：
     侧栏因此视觉上通到壳边缘，接近 ChatGPT 网页版的分区方式 */
  background: var(--zk-bg-page);
}

@media (max-width: 1280px) {
  .ai-chat-page {
    --ai-neg: var(--zk-space-4);
  }
}

@media (max-width: 768px) {
  .ai-chat-page {
    --ai-neg: var(--zk-space-3);
  }
}

/* ==================== 左栏 ====================
   Console 侧：侧栏用页面浅灰底与白色对话区自然分层（靠底色而不是再加一层卡片边框），
   hover 用更深的 border-light，选中用主色浅底——三级状态可分辨。 */
.ai-side {
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--zk-bg-page);
  border-right: 1px solid var(--zk-border-light);
  overflow: hidden;
  transition: flex-basis var(--zk-duration-base) var(--zk-ease);
}

.ai-side.is-collapsed {
  flex-basis: 56px;
}

.ai-side__top {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  padding: var(--zk-space-3) var(--zk-space-3) var(--zk-space-2);
}

.ai-side__new {
  flex: 1;
  height: 38px;
  border-radius: var(--zk-radius-lg);
  font-weight: 500;
}

.ai-side__collapse {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--zk-radius-md);
  background: none;
  color: var(--zk-text-placeholder);
  cursor: pointer;
  transition:
    background-color var(--zk-duration-fast) var(--zk-ease),
    color var(--zk-duration-fast) var(--zk-ease);
}

.ai-side__collapse:hover {
  background: var(--zk-bg-hover);
  color: var(--zk-text-regular);
}

.ai-side__list {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0 var(--zk-space-2);
}

.ai-side__group {
  margin-bottom: var(--zk-space-3);
}

.ai-side__group-label {
  padding: var(--zk-space-2) var(--zk-space-2) var(--zk-space-1);
  font-size: var(--zk-font-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--zk-text-placeholder);
}

.ai-side__conv {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--zk-space-1);
  padding: var(--zk-space-2) var(--zk-space-2);
  border-radius: var(--zk-radius-md);
  cursor: pointer;
  transition: background-color var(--zk-duration-fast) var(--zk-ease);
}

.ai-side__conv:hover {
  background: var(--zk-border-light);
}

.ai-side__conv.is-active {
  background: var(--zk-primary-light);
}

.ai-side__conv-main {
  flex: 1;
  min-width: 0;
}

.ai-side__conv-title {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-regular);
  line-height: 1.5;
}

.ai-side__conv.is-active .ai-side__conv-title {
  color: var(--zk-text-primary);
  font-weight: 500;
}

.ai-side__conv-meta {
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

/* hover 出现操作图标（默认隐藏，避免整列长满按钮） */
.ai-side__conv-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity var(--zk-duration-fast) var(--zk-ease);
}

.ai-side__conv:hover .ai-side__conv-actions {
  opacity: 1;
}

.ai-side__conv-del {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--zk-radius-sm);
  background: none;
  color: var(--zk-text-placeholder);
  cursor: pointer;
  transition:
    background-color var(--zk-duration-fast) var(--zk-ease),
    color var(--zk-duration-fast) var(--zk-ease);
}

.ai-side__conv-del:hover {
  background: var(--zk-danger-light);
  color: var(--zk-danger);
}

/* 收起后的 56px 图标条 */
.ai-side__mini {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--zk-space-2);
  padding: var(--zk-space-3) 0;
  overflow-y: auto;
  scrollbar-width: none;
}

.ai-side__mini::-webkit-scrollbar {
  display: none;
}

.ai-side__mini-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-card);
  color: var(--zk-text-secondary);
  cursor: pointer;
  transition: all var(--zk-duration-fast) var(--zk-ease);
}

.ai-side__mini-btn:hover {
  border-color: var(--zk-primary);
  color: var(--zk-primary);
}

.ai-side__mini-btn.is-primary {
  background: var(--zk-primary);
  border-color: var(--zk-primary);
  color: var(--zk-text-inverse);
}

.ai-side__mini-btn.is-primary:hover {
  background: var(--zk-primary-hover);
  color: var(--zk-text-inverse);
}

.ai-side__mini-item {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--zk-radius-md);
  background: none;
  color: var(--zk-text-placeholder);
  cursor: pointer;
  transition:
    background-color var(--zk-duration-fast) var(--zk-ease),
    color var(--zk-duration-fast) var(--zk-ease);
}

.ai-side__mini-item:hover {
  background: var(--zk-bg-hover);
  color: var(--zk-text-regular);
}

.ai-side__mini-item.is-active {
  background: var(--zk-primary-light);
  color: var(--zk-primary);
}

/* 能力边界说明：常驻左栏底部，只靠上边框分层（侧栏已是浅灰底） */
.ai-side__scope {
  display: flex;
  align-items: flex-start;
  gap: var(--zk-space-2);
  flex: 0 0 auto;
  padding: var(--zk-space-3);
  border-top: 1px solid var(--zk-border-light);
  background: none;
  font-size: var(--zk-font-xs);
  line-height: 1.6;
  color: var(--zk-text-secondary);
}

.ai-side__scope .el-icon {
  margin-top: 2px;
  flex: 0 0 auto;
  color: var(--zk-primary);
}

/* ==================== 右栏 ==================== */
.ai-main {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--zk-space-2);
}

.ai-main__bar {
  display: none;
  flex: 0 0 auto;
}

.ai-main__open {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 var(--zk-space-3);
  border: 1px solid var(--zk-border);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-card);
  color: var(--zk-text-secondary);
  font-size: var(--zk-font-sm);
  font-family: inherit;
  cursor: pointer;
  transition: all var(--zk-duration-fast) var(--zk-ease);
}

.ai-main__open:hover {
  color: var(--zk-primary);
  border-color: var(--zk-primary);
}

/* Console 侧：三段式齐平（ChatGPT 网页版形态）——左栏靠右边框分隔，
   对话区不再套卡片描边；Portal 侧在底部 .portal 块里改回纸卡观感。 */
.ai-main :deep(.ai-chat) {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

/* 浮层遮罩 */
.ai-chat-page__mask {
  position: absolute;
  inset: 0;
  z-index: 19;
  border-radius: inherit;
  background: var(--zk-bg-mask);
}

/* ==================== ≤900px：左栏变浮层抽屉 ==================== */
@media (max-width: 900px) {
  .ai-chat-page {
    gap: 0;
  }

  .ai-main__bar {
    display: block;
  }

  /* 抽屉始终落在页面框内：收起态用 opacity/visibility 而不是移出容器，
     否则负边距 + 位移会把横向滚动条顶出来（验收项「窄屏无横向滚动」）。 */
  .ai-side {
    position: absolute;
    top: var(--zk-space-3);
    bottom: var(--zk-space-3);
    left: var(--zk-space-3);
    z-index: 20;
    width: 268px;
    flex-basis: 268px;
    background: var(--zk-bg-card);
    border: 1px solid var(--zk-border-light);
    border-radius: var(--zk-radius-lg);
    box-shadow: var(--zk-shadow-lg);
    opacity: 0;
    visibility: hidden;
    transform: translateX(-12px);
    transition:
      opacity var(--zk-duration-base) var(--zk-ease),
      transform var(--zk-duration-base) var(--zk-ease),
      visibility var(--zk-duration-base) var(--zk-ease);
  }

  .ai-chat-page.is-drawer-open .ai-side {
    opacity: 1;
    visibility: visible;
    transform: translateX(0);
  }

  /* 抽屉态下折叠条无意义，强制展开 */
  .ai-side.is-collapsed {
    flex-basis: 268px;
    width: 268px;
  }
}
</style>

<!-- ============================================================
  Portal 壳（学生端）覆写 —— 纸质暖色观感。
  Console 壳的上方 scoped 样式原样生效，此处所有选择器都挂在
  `.portal`（PortalLayout 根类）之下，教师端观感零影响。
  只改视觉，不触碰流式 / 发送 / 会话逻辑。
============================================================ -->
<style>
/* 壳层高度差：Portal 只有 56px 头部 + 容器内边距（24 / 24 / 48，且不随断点变化） */
.portal .ai-chat-page {
  --ai-shell-offset: 128px;
  --ai-neg: var(--zk-space-5);
  margin-bottom: -48px;
  background: var(--zk-portal-bg);
}

/* 左栏：米色纸感 */
.portal .ai-chat-page .ai-side {
  background: var(--zk-portal-card);
  border-right-color: var(--zk-portal-line);
}

.portal .ai-chat-page .ai-side__new {
  border-radius: var(--zk-portal-radius-md);
  font-weight: 700;
}

.portal .ai-chat-page .ai-side__conv {
  border-radius: var(--zk-portal-radius-md);
}

.portal .ai-chat-page .ai-side__conv:hover {
  background: var(--zk-portal-bg);
}

.portal .ai-chat-page .ai-side__conv.is-active {
  background: var(--zk-portal-bg-deep);
}

.portal .ai-chat-page .ai-side__conv-title {
  color: var(--zk-portal-ink);
}

.portal .ai-chat-page .ai-side__group-label {
  font-family: var(--zk-font-display);
  color: var(--zk-portal-muted);
}

.portal .ai-chat-page .ai-side__scope {
  background: var(--zk-portal-bg);
  border-top-color: var(--zk-portal-line);
  color: var(--zk-portal-muted);
}

.portal .ai-chat-page .ai-side__mini-btn {
  border-color: var(--zk-portal-line);
  background: var(--zk-portal-card);
  color: var(--zk-portal-ink-soft);
}

.portal .ai-chat-page .ai-side__mini-btn.is-primary {
  background: var(--zk-portal-ink);
  border-color: var(--zk-portal-ink);
  color: #fff;
}

/* 对话主体：Portal 侧回到「纸卡」形态。
   注意要整条重写 border —— 基座（Console）用的是 border: none，
   只写 border-color 不会画出边框。 */
.portal .ai-chat-page .ai-main .ai-chat {
  background: var(--zk-portal-card);
  border: 1px solid var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-lg);
  box-shadow: var(--zk-portal-shadow-s);
}

/* 消息流底色用暖米，正文墨色 */
.portal .ai-chat-page .ai-chat__stream {
  color: var(--zk-portal-ink);
}

.portal .ai-chat-page .ai-chat__scroll {
  background:
    radial-gradient(circle at 12% 8%, rgba(124, 92, 230, 0.05), transparent 42%),
    var(--zk-portal-bg);
}

.portal .ai-chat-page .ai-chat__welcome-mark {
  background: var(--zk-portal-ink);
  color: #fff;
  border-radius: var(--zk-portal-radius-md);
}

.portal .ai-chat-page .ai-chat__welcome-title {
  font-family: var(--zk-font-display);
  font-weight: 900;
  color: var(--zk-portal-ink);
}

.portal .ai-chat-page .ai-chat__welcome-hint {
  color: var(--zk-portal-muted);
}

.portal .ai-chat-page .ai-chat__card {
  background: var(--zk-portal-card);
  border: 1.5px dashed var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-md);
  color: var(--zk-portal-ink-soft);
  box-shadow: none;
}

.portal .ai-chat-page .ai-chat__card:hover {
  border-style: solid;
  border-color: #7c5ce6;
  color: #7c5ce6;
  box-shadow: var(--zk-portal-shadow-s);
}

/* 助手消息：无底色直接铺正文（门户墨色）；用户消息：米色浅底块 */
.portal .ai-chat-page .ai-chat__content {
  color: var(--zk-portal-ink);
}

.portal .ai-chat-page .ai-chat__avatar {
  background: var(--zk-portal-bg-deep);
  color: var(--zk-portal-ink);
  border-radius: 10px;
}

.portal .ai-chat-page .ai-chat__msg.is-user .ai-chat__avatar {
  background: var(--zk-portal-line);
  color: var(--zk-portal-ink-soft);
}

.portal .ai-chat-page .ai-chat__msg.is-user .ai-chat__content {
  background: var(--zk-portal-bg-deep);
  border: 1px solid var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-md);
  color: var(--zk-portal-ink);
}

/* 以下覆盖写在非 scoped 块里：v-html 生成的 .md-* 类没有 data-v 属性，
   普通后代选择器即可命中，不能写 :deep()（那是 scoped 专用语法）。 */
.portal .ai-chat-page .ai-chat__content .md-code {
  background: var(--zk-portal-bg-deep);
  color: var(--zk-portal-ink);
}

.portal .ai-chat-page .ai-chat__content .md-table th,
.portal .ai-chat-page .ai-chat__content .md-table td {
  border-color: var(--zk-portal-line);
}

.portal .ai-chat-page .ai-chat__content .md-table th {
  background: var(--zk-portal-bg);
}

.portal .ai-chat-page .ai-chat__tool {
  background: var(--zk-portal-bg);
}

/* 底部输入区 */
.portal .ai-chat-page .ai-chat__footer {
  background: var(--zk-portal-card);
  border-top-color: var(--zk-portal-line);
}

.portal .ai-chat-page .ai-chat__chip {
  border-color: var(--zk-portal-line);
  color: var(--zk-portal-ink-soft);
}

.portal .ai-chat-page .ai-chat__chip:hover {
  border-color: #7c5ce6;
  color: #7c5ce6;
}

.portal .ai-chat-page .ai-chat__composer {
  background: var(--zk-portal-bg);
  border-color: var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-lg);
}

.portal .ai-chat-page .ai-chat__composer:focus-within {
  border-color: #7c5ce6;
  background: var(--zk-portal-card);
  box-shadow: 0 0 0 3px rgba(124, 92, 230, 0.12);
}

.portal .ai-chat-page .ai-chat__composer .el-textarea__inner {
  color: var(--zk-portal-ink);
}

.portal .ai-chat-page .ai-chat__jump {
  background: var(--zk-portal-card);
  border-color: var(--zk-portal-line);
  color: var(--zk-portal-ink-soft);
  box-shadow: var(--zk-portal-shadow-s);
}

.portal .ai-chat-page .ai-chat__note {
  color: var(--zk-portal-muted);
}

/* 窄屏抽屉：浮在其他内容之上，需要圆角 + 阴影（桌面态左栏是齐平的，保持方角） */
@media (max-width: 900px) {
  .portal .ai-chat-page .ai-side {
    border-radius: var(--zk-portal-radius-md);
    box-shadow: var(--zk-portal-shadow-m);
  }
}
</style>
