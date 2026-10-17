<template>
  <AppPage
    title="动效演示"
    description="全站动效的集中验收台：每条都有自己的演示舞台，点「播放」必定能看到效果"
  >
    <template #actions>
      <el-switch v-model="reduced" active-text="模拟减弱动态效果" @change="toggleReduced" />
      <el-button :icon="Refresh" @click="replay">重播全部</el-button>
    </template>

    <el-alert
      class="zk-mb-4"
      type="info"
      show-icon
      :closable="false"
      title="为什么每条动效都要有自己的舞台"
      description="原来这个页面的做法是把工具类名直接挂到一个方块上。结果像「路由切换过渡」「侧栏折叠」「菜单指示条」「进度条」这些条目根本不动——因为它们的动效发生在特定的元素结构上（transition 需要状态变化、::after 需要伪元素、进度条需要 scaleX），脱离上下文就什么都不会发生。现在每条都用一个自包含的小舞台复现真实结构，播放按钮一定能看到效果。"
    />

    <div class="demo-grid">
      <section v-for="item in items" :key="item.no" class="demo-card">
        <header class="demo-card__head">
          <span class="demo-card__no">{{ item.no }}</span>
          <div class="demo-card__meta">
            <h4 class="demo-card__title">{{ item.title }}</h4>
            <p class="demo-card__where">{{ item.where }}</p>
          </div>
          <el-button text type="primary" size="small" :icon="VideoPlay" @click="play(item.no)">
            播放
          </el-button>
        </header>

        <!-- 舞台：key 里带 tick，播放时整个舞台重挂载，CSS 动画自然从头开始 -->
        <div :key="`${item.no}-${tick}`" class="demo-card__stage">
          <!-- 1. 路由切换过渡（纯透明度，短时长） -->
          <template v-if="item.stage === 'route'">
            <div class="demo-route" :class="{ 'is-from': phase === 'from' }">
              <div class="demo-route__bar" />
              <div class="demo-route__block" />
              <div class="demo-route__block demo-route__block--short" />
            </div>
          </template>

          <!-- 2. 侧栏折叠：宽度过渡（布局变化无法用 transform 替代） -->
          <template v-else-if="item.stage === 'sidebar'">
            <div class="demo-sidebar" :class="{ 'is-open': sidebarOpen }">
              <div class="demo-sidebar__logo" />
              <div class="demo-sidebar__item" />
              <div class="demo-sidebar__item" />
              <div class="demo-sidebar__item" />
            </div>
            <div class="demo-sidebar__body" />
          </template>

          <!-- 3. 菜单指示条：scaleY 从 0 展开（真实实现是 ::after 伪元素） -->
          <template v-else-if="item.stage === 'bar'">
            <div class="demo-menu">
              <div class="demo-menu__item is-active">
                <span class="demo-menu__bar" />
                <span class="demo-menu__label">题库管理</span>
              </div>
              <div class="demo-menu__item">
                <span class="demo-menu__label">试卷组卷</span>
              </div>
            </div>
          </template>

          <!-- 4. 卡片入场：上移 + 淡入 -->
          <template v-else-if="item.stage === 'enter-up'">
            <div class="demo-enter-box zk-enter-up">卡片入场</div>
          </template>

          <!-- 5. 数字滚动：真实复用 StatCard 用的 AnimatedNumber -->
          <template v-else-if="item.stage === 'counter'">
            <div class="demo-counter">
              <AnimatedNumber :value="counterTarget" :duration="600" />
              <span class="demo-counter__unit">人</span>
            </div>
          </template>

          <!-- 6. 表格行 hover：用高亮类模拟悬停态（真机上就是 :hover） -->
          <template v-else-if="item.stage === 'table'">
            <div class="demo-table">
              <div
                v-for="(row, index) in tableRows"
                :key="row"
                class="demo-table__row"
                :class="{ 'is-hover': hoverIndex === index }"
              >
                <span>{{ row }}</span>
                <span class="demo-table__score">{{ 80 + index * 3 }} 分</span>
              </div>
            </div>
          </template>

          <!-- 7. 骨架屏扫光 -->
          <template v-else-if="item.stage === 'shimmer'">
            <div class="demo-shimmer">
              <div class="demo-shimmer__line zk-shimmer" />
              <div class="demo-shimmer__line demo-shimmer__line--short zk-shimmer" />
              <div class="demo-shimmer__line demo-shimmer__line--mid zk-shimmer" />
            </div>
          </template>

          <!-- 8. 题目切换：横向滑入 -->
          <template v-else-if="item.stage === 'question'">
            <div class="demo-question zk-question-in">
              <div class="demo-question__no">第 3 题 / 共 10 题</div>
              <div class="demo-question__text">以下哪个集合实现是线程安全的？</div>
            </div>
          </template>

          <!-- 9. 选项选中反馈：描边 + 轻微缩放 -->
          <template v-else-if="item.stage === 'scale'">
            <div class="demo-option zk-scale-in">
              <span class="demo-option__label">B</span>
              <span>ConcurrentHashMap</span>
            </div>
          </template>

          <!-- 10. 倒计时告急：呼吸脉冲 -->
          <template v-else-if="item.stage === 'pulse'">
            <div class="demo-countdown zk-pulse">
              <el-icon><Timer /></el-icon>
              <span>00:42</span>
            </div>
          </template>

          <!-- 11. 交卷确认弹窗：scale(0.92→1) + fade -->
          <template v-else-if="item.stage === 'modal'">
            <div class="demo-modal zk-scale-in">
              <div class="demo-modal__title">确认交卷？</div>
              <div class="demo-modal__body">交卷后不能再修改答案</div>
              <div class="demo-modal__actions">
                <span class="demo-modal__btn">取消</span>
                <span class="demo-modal__btn demo-modal__btn--primary">确定交卷</span>
              </div>
            </div>
          </template>

          <!-- 12. 流式打字：光标闪烁 + 三点脉冲 -->
          <template v-else-if="item.stage === 'typing'">
            <div class="demo-typing">
              <span class="demo-typing__text zk-caret">根据题库统计，共 128 道题目</span>
            </div>
            <div class="demo-thinking">
              <span class="zk-dot" />
              <span class="zk-dot" />
              <span class="zk-dot" />
              <span class="zk-text-xs zk-text-placeholder">思考中</span>
            </div>
          </template>

          <!-- 13. 顶部进度条：scaleX 推进 -->
          <template v-else-if="item.stage === 'progress'">
            <div class="demo-progress">
              <div class="demo-progress__track">
                <div class="demo-progress__bar" />
              </div>
              <div class="demo-progress__hint">切页时出现在页面顶部</div>
            </div>
          </template>

          <!-- 14. 全局降级：由顶部开关控制，这里展示当前状态 -->
          <template v-else>
            <div class="demo-reduced" :class="{ 'is-on': reduced }">
              <el-icon :size="18">
                <component :is="reduced ? 'VideoPause' : 'VideoPlay'" />
              </el-icon>
              <span>{{ reduced ? '已降级：动效时长压至 0.001ms' : '正常：动效按 Token 时长播放' }}</span>
            </div>
          </template>
        </div>

        <footer class="demo-card__foot">
          <code>{{ item.cls }}</code>
          <span class="zk-text-xs zk-text-placeholder">{{ item.duration }}</span>
        </footer>
      </section>
    </div>
  </AppPage>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { Refresh, Timer, VideoPlay } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import AnimatedNumber from '@/components/AnimatedNumber.vue'

// 组件名与路由 name 一致，keep-alive 才能命中
defineOptions({ name: 'AnimationDemo' })

/**
 * 动效演示台（三期重写）。
 *
 * <p><b>为什么必须重写：</b>原来每条动效只把「工具类名」挂到一个方块上。
 * 但动效是否可见取决于元素结构与状态变化，而不是类名本身：
 * <ul>
 *   <li>路由过渡用的是 Vue 的 transition 类，需要「进入/离开」两个状态，静态挂上去不动；</li>
 *   <li>侧栏折叠是 width 的 transition，需要宽度真的变化；</li>
 *   <li>菜单指示条作用在 {@code ::after} 伪元素上，挂在父元素上无效；</li>
 *   <li>进度条是 scaleX 推进、数字滚动是 requestAnimationFrame、表格行是 :hover——都不是普通 class 能触发的。</li>
 * </ul>
 * 结论：每条动效都必须有自己的「舞台」，把真实结构复现出来。
 * 现在每个卡片的舞台都是自包含的，播放按钮通过改 key 触发整个舞台重挂载，
 * 所以动画一定能从头播一遍。
 *
 * <p>「减弱动态效果」开关：注入一条全局规则把动画时长压到 0，
 * 用来验证系统的全局降级是否真的生效（而不是在无障碍设置里改了也没人管）。
 */
const items = [
  { no: 1, title: '路由切换过渡', where: '一级路由切换', cls: 'zk-route-*', duration: '150ms', stage: 'route' },
  { no: 2, title: '侧栏折叠 / 展开', where: '布局侧边栏', cls: 'width transition', duration: '240ms', stage: 'sidebar' },
  { no: 3, title: '菜单选中指示条', where: '侧边菜单', cls: 'zk-bar-grow', duration: '150ms', stage: 'bar' },
  { no: 4, title: '卡片入场', where: '首页 / 看板', cls: 'zk-enter-up', duration: '400ms', stage: 'enter-up' },
  { no: 5, title: '统计数字滚动', where: 'StatCard', cls: 'requestAnimationFrame', duration: '600ms', stage: 'counter' },
  { no: 6, title: '表格行 hover', where: '全部列表页', cls: 'tr:hover', duration: '150ms', stage: 'table' },
  { no: 7, title: '骨架屏扫光', where: '列表首屏', cls: 'zk-shimmer', duration: '1.4s 循环', stage: 'shimmer' },
  { no: 8, title: '题目切换', where: '学生答题', cls: 'zk-question-in', duration: '240ms', stage: 'question' },
  { no: 9, title: '选项选中反馈', where: '学生答题', cls: 'zk-scale-in', duration: '240ms', stage: 'scale' },
  { no: 10, title: '倒计时告急脉冲', where: '剩余 < 60s', cls: 'zk-pulse', duration: '1s 循环', stage: 'pulse' },
  { no: 11, title: '确认弹窗', where: '交卷前', cls: 'zk-scale-in', duration: '240ms', stage: 'modal' },
  { no: 12, title: 'AI 流式打字', where: '智能助手', cls: 'zk-caret / zk-dot', duration: '逐字追加', stage: 'typing' },
  { no: 13, title: '顶部路由进度条', where: '全局', cls: 'zk-progress-grow', duration: '1.4s', stage: 'progress' },
  { no: 14, title: '全局降级', where: '全部动效', cls: 'prefers-reduced-motion', duration: '—', stage: 'reduced' }
]

const tableRows = ['张三', '李四', '王五']

const tick = ref(0)
const reduced = ref(false)
const phase = ref('to')
const sidebarOpen = ref(true)
const hoverIndex = ref(0)
const counterTarget = ref(1280)
let timers = []

/** 记录定时器统一清理：页面切走时不留下悬空回调 */
function later(fn, delay) {
  timers.push(setTimeout(fn, delay))
}

function clearTimers() {
  timers.forEach(clearTimeout)
  timers = []
}

/**
 * 播放：先把 tick +1 让舞台重挂载（CSS 动画随之重头开始），
 * 再按条目类型补上「状态驱动型」动效需要的状态切换。
 * 纯 CSS 关键帧动画（4/8/9/10/11）不需要额外状态，重挂载即可；
 * transition / 循环模拟类的需要下面这些额外动作。
 */
function play(no) {
  clearTimers()
  tick.value++

  if (no === 1) {
    // 路由过渡：先进入 from 态，下一帧去掉 → 触发 opacity 过渡
    phase.value = 'from'
    requestAnimationFrame(() => requestAnimationFrame(() => (phase.value = 'to')))
  } else if (no === 2) {
    sidebarOpen.value = !sidebarOpen.value
  } else if (no === 6) {
    // 表格 hover 无法用脚本真正触发，这里依次高亮三行演示效果
    hoverIndex.value = 0
    later(() => (hoverIndex.value = 1), 500)
    later(() => (hoverIndex.value = 2), 1000)
    later(() => (hoverIndex.value = 0), 1500)
  } else if (no === 5) {
    // 每次播放换一个目标值，才能看到「从旧值滚到新值」而非停在原地
    counterTarget.value = 600 + Math.round(Math.random() * 2000)
  }
}

function replay() {
  items.forEach((item, index) => later(() => play(item.no), index * 260))
}

/**
 * 模拟系统的 prefers-reduced-motion：
 * 直接注入一条全局规则把动画时长压到 0，效果与系统设置一致。
 */
function toggleReduced() {
  const id = 'zk-reduced-motion-sim'
  document.getElementById(id)?.remove()
  if (reduced.value) {
    const style = document.createElement('style')
    style.id = id
    style.textContent =
      '*,*::before,*::after{animation-duration:.001ms !important;animation-iteration-count:1 !important;transition-duration:.001ms !important;}'
    document.head.appendChild(style)
  }
}

onBeforeUnmount(() => {
  clearTimers()
  document.getElementById('zk-reduced-motion-sim')?.remove()
})
</script>

<style scoped>
.demo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--zk-space-4);
}

.demo-card {
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-lg);
  box-shadow: var(--zk-shadow-sm);
  padding: var(--zk-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--zk-space-3);
}

.demo-card__head {
  display: flex;
  align-items: flex-start;
  gap: var(--zk-space-2);
}

.demo-card__meta {
  flex: 1;
  min-width: 0;
}

.demo-card__no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-primary-light);
  color: var(--zk-primary);
  font-size: var(--zk-font-xs);
  font-weight: 600;
}

.demo-card__title {
  font-size: var(--zk-font-base);
  font-weight: 600;
}

.demo-card__where {
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.demo-card__stage {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--zk-space-3);
  height: 104px;
  padding: var(--zk-space-3);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-page);
  overflow: hidden;
}

.demo-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-2);
}

.demo-card__foot code {
  font-size: var(--zk-font-xs);
  color: var(--zk-primary);
  background: var(--zk-primary-lighter);
  padding: 1px 6px;
  border-radius: var(--zk-radius-sm);
}

/* ===== 1. 路由过渡 ===== */
.demo-route {
  width: 100%;
  padding: var(--zk-space-2);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  opacity: 1;
  transition: opacity var(--zk-duration-fast) var(--zk-ease);
}

.demo-route.is-from {
  opacity: 0;
}

.demo-route__bar {
  height: 8px;
  width: 40%;
  border-radius: 4px;
  background: var(--zk-primary);
  margin-bottom: 8px;
}

.demo-route__block {
  height: 8px;
  border-radius: 4px;
  background: var(--zk-border);
  margin-bottom: 6px;
}

.demo-route__block--short {
  width: 60%;
  margin-bottom: 0;
}

/* ===== 2. 侧栏折叠 ===== */
.demo-sidebar {
  width: 44px;
  flex: 0 0 auto;
  align-self: stretch;
  padding: 6px;
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-sidebar);
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 布局宽度变化无法用 transform 替代，这是全站唯一的 width 过渡 */
  transition: width var(--zk-duration-base) var(--zk-ease);
  will-change: width;
  overflow: hidden;
}

.demo-sidebar.is-open {
  width: 108px;
}

.demo-sidebar__logo {
  height: 12px;
  border-radius: 3px;
  background: var(--zk-primary);
  margin-bottom: 4px;
}

.demo-sidebar__item {
  height: 8px;
  border-radius: 3px;
  background: var(--zk-sidebar-text);
  opacity: 0.5;
}

.demo-sidebar__body {
  flex: 1;
  align-self: stretch;
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
}

/* ===== 3. 菜单指示条 ===== */
.demo-menu {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.demo-menu__item {
  position: relative;
  display: flex;
  align-items: center;
  height: 30px;
  padding-left: 14px;
  border-radius: var(--zk-radius-sm);
  font-size: var(--zk-font-sm);
  color: var(--zk-text-secondary);
}

.demo-menu__item.is-active {
  background: var(--zk-primary-light);
  color: var(--zk-primary);
}

/* 真实实现里这条指示条挂在 .el-menu-item::after 上，所以挂在父元素上不会动 */
.demo-menu__bar {
  position: absolute;
  left: 0;
  top: 20%;
  height: 60%;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: var(--zk-primary);
  transform-origin: center;
  animation: zk-bar-grow var(--zk-duration-fast) var(--zk-ease) both;
}

/* ===== 4. 卡片入场 ===== */
.demo-enter-box {
  padding: var(--zk-space-2) var(--zk-space-5);
  border-radius: var(--zk-radius-md);
  background: var(--zk-primary);
  color: var(--zk-text-inverse);
  font-size: var(--zk-font-sm);
}

/* ===== 5. 数字滚动 ===== */
.demo-counter {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 30px;
  font-weight: 600;
  color: var(--zk-primary);
}

.demo-counter__unit {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-secondary);
}

/* ===== 6. 表格 hover ===== */
.demo-table {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.demo-table__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 8px;
  border-radius: var(--zk-radius-sm);
  background: var(--zk-bg-card);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-regular);
  transition: transform var(--zk-duration-fast) var(--zk-ease),
    box-shadow var(--zk-duration-fast) var(--zk-ease);
}

.demo-table__row.is-hover {
  transform: translateY(-1px);
  box-shadow: var(--zk-shadow-md);
}

.demo-table__score {
  color: var(--zk-primary);
}

/* ===== 7. 骨架屏扫光 ===== */
.demo-shimmer {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-shimmer__line {
  height: 10px;
  border-radius: 5px;
  background: linear-gradient(
    90deg,
    var(--zk-bg-hover) 25%,
    var(--zk-border-light) 37%,
    var(--zk-bg-hover) 63%
  );
  background-size: 400% 100%;
  animation: zk-shimmer 1.4s ease-in-out infinite;
}

.demo-shimmer__line--short {
  width: 70%;
}

.demo-shimmer__line--mid {
  width: 85%;
}

/* ===== 8. 题目切换 ===== */
.demo-question {
  width: 100%;
  padding: var(--zk-space-3);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-card);
  border-left: 3px solid var(--zk-primary);
}

.demo-question__no {
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.demo-question__text {
  margin-top: 4px;
  font-size: var(--zk-font-sm);
  color: var(--zk-text-primary);
}

/* ===== 9. 选项选中 ===== */
.demo-option {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  padding: 7px var(--zk-space-3);
  border-radius: var(--zk-radius-md);
  border: 1px solid var(--zk-primary);
  background: var(--zk-primary-light);
  font-size: var(--zk-font-sm);
  color: var(--zk-text-primary);
}

.demo-option__label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--zk-primary);
  color: var(--zk-text-inverse);
  font-size: 11px;
}

/* ===== 10. 倒计时告急 ===== */
.demo-countdown {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  background: var(--zk-danger-light);
  color: var(--zk-danger);
  font-size: var(--zk-font-base);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ===== 11. 确认弹窗 ===== */
.demo-modal {
  width: 190px;
  padding: var(--zk-space-3);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-card);
  box-shadow: var(--zk-shadow-lg);
  border: 1px solid var(--zk-border-light);
}

.demo-modal__title {
  font-size: var(--zk-font-sm);
  font-weight: 600;
}

.demo-modal__body {
  margin: 4px 0 var(--zk-space-2);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-secondary);
}

.demo-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.demo-modal__btn {
  padding: 2px 10px;
  border-radius: var(--zk-radius-sm);
  border: 1px solid var(--zk-border);
  font-size: 11px;
  color: var(--zk-text-regular);
}

.demo-modal__btn--primary {
  border-color: var(--zk-primary);
  background: var(--zk-primary);
  color: var(--zk-text-inverse);
}

/* ===== 12. 流式打字 ===== */
.demo-typing {
  max-width: 100%;
}

.demo-typing__text {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-primary);
}

.demo-thinking {
  display: flex;
  align-items: center;
  gap: var(--zk-space-1);
  color: var(--zk-primary);
}

/* ===== 13. 进度条 ===== */
.demo-progress {
  width: 100%;
}

.demo-progress__track {
  height: 4px;
  border-radius: 2px;
  background: var(--zk-border-light);
  overflow: hidden;
}

.demo-progress__bar {
  height: 100%;
  border-radius: 2px;
  background: var(--zk-primary);
  transform-origin: left center;
  /* 真实实现里这条在 zk-progress 组件中每条路由切换触发一次 */
  animation: zk-progress-grow 1.4s var(--zk-ease) both;
}

.demo-progress__hint {
  margin-top: 6px;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

/* ===== 14. 全局降级状态 ===== */
.demo-reduced {
  display: flex;
  align-items: center;
  gap: var(--zk-space-2);
  padding: var(--zk-space-2) var(--zk-space-3);
  border-radius: var(--zk-radius-md);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-secondary);
}

.demo-reduced.is-on {
  border-color: var(--zk-warning);
  background: var(--zk-warning-light);
  color: var(--zk-warning);
}
</style>
