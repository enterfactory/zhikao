<template>
  <div class="exam-shell" v-loading="loading">
    <header class="exam-topbar">
      <div>
        <h1>{{ paper?.examName || '在线考试' }}</h1>
        <p>
          考生 {{ name }}
          <span v-if="paper">· 共 {{ questions.length }} 题 · {{ paper.totalScore }} 分</span>
        </p>
      </div>
      <div v-if="!receipt && paper" class="exam-timer" :class="{ urgent: seconds < 300 }">
        <small>
          剩余答题时间
          <br />
          以服务器时间为准
        </small>
        <strong>{{ clock }}</strong>
      </div>
      <router-link v-else to="/student/exam">返回考试安排 →</router-link>
    </header>
    <section v-if="receipt" class="exam-receipt">
      <div class="receipt-check">✓</div>
      <h1>答卷已提交</h1>
      <p>{{ receipt.message || '系统已收到你的答卷。成绩与解析由教师完成审核后公布。' }}</p>
      <div v-if="receipt.totalScore != null" class="result-score">{{ receipt.totalScore }} 分</div>
      <p class="study-help">考试编号 {{ examId }} · {{ receipt.statusName }}</p>
      <el-button type="primary" @click="router.replace('/student/exam')">返回考试安排</el-button>
    </section>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
        <button v-if="!paper" class="text-button" @click="load">重试加载</button>
      </el-alert>
      <el-alert v-if="draftNotice" :title="draftNotice" type="info" :closable="false" />
      <el-alert
        v-if="queueState.error"
        type="error"
        :closable="false"
        show-icon
        title="部分作答尚未确认保存，请保持页面开启。"
      >
        <span>{{ queueState.error.message }}</span>
        <el-button v-if="!conflict" link type="primary" @click="retry">重试保存</el-button>
        <el-button v-else link type="primary" @click="reloadConflict">重新载入服务器版本</el-button>
      </el-alert>
      <div v-if="question" class="exam-workspace">
        <section class="study-panel exam-main-question">
          <div class="question-topline">
            <span class="tag">{{ question.typeName || names[question.type] }}</span>
            <span>第 {{ index + 1 }} 题 · {{ question.score }} 分</span>
            <button
              class="exam-flag"
              :class="{ marked: flags.has(question.id) }"
              @click="toggleFlag"
            >
              {{ flags.has(question.id) ? '● 已标记' : '⚑ 稍后检查' }}
            </button>
          </div>
          <RichContent class="question-stem" :html="question.content" />
          <QuestionInput
            :model-value="answers[question.id] || ''"
            :type="question.type"
            :options="question.options"
            :disabled="submitting || seconds <= 0 || conflict"
            @update:model-value="edit(question.id, $event)"
          />
          <div class="question-actions">
            <el-button :disabled="index === 0" @click="index--">上一题</el-button>
            <span>{{ index + 1 }} / {{ questions.length }}</span>
            <el-button type="primary" :disabled="index === questions.length - 1" @click="index++">
              下一题 →
            </el-button>
          </div>
        </section>
        <aside class="study-panel exam-card">
          <div class="section-heading">
            <h3>答题卡</h3>
            <span class="muted">{{ answeredCount }} / {{ questions.length }}</span>
          </div>
          <p class="exam-save-status" :class="{ failed: queueState.error }">
            {{
              queueState.error
                ? '保存未确认'
                : queueState.saving
                  ? '正在保存…'
                  : queueState.pending
                    ? '等待保存…'
                    : '✓ 所有修改已保存至服务器'
            }}
          </p>
          <div class="question-grid">
            <button
              v-for="(q, i) in questions"
              :key="q.id"
              :class="{
                active: index === i,
                done: !!answers[q.id]?.trim(),
                flagged: flags.has(q.id),
              }"
              @click="index = i"
            >
              {{ i + 1 }}
            </button>
          </div>
          <div class="outline-key">
            <span>● 已作答</span>
            <span>○ 未作答</span>
          </div>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="conflict"
            @click="submit(false)"
          >
            提交试卷
          </el-button>
          <p class="study-help">提交前会确认所有作答保存成功。到时后，服务器按已保存内容收卷。</p>
          <p v-if="draftUnavailable" class="study-help">
            浏览器无法存储本地草稿，请关注服务器保存状态。
          </p>
        </aside>
      </div>
    </template>
  </div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { enterExam, saveAnswer, submitExam, myExams, reportCheat } from '@/api/exam'
import { useUserStore } from '@/store/user'
import { createAnswerQueue } from '@/utils/answerQueue'
import QuestionInput from '@/components/QuestionInput.vue'
import RichContent from '@/components/RichContent.vue'
const route = useRoute(),
  router = useRouter(),
  user = useUserStore(),
  examId = String(route.query.id || ''),
  paper = ref(null),
  loading = ref(true),
  error = ref(''),
  receipt = ref(null),
  index = ref(0),
  answers = reactive({}),
  saved = reactive({}),
  versions = reactive({}),
  flags = reactive(new Set()),
  seconds = ref(0),
  submitting = ref(false),
  draftNotice = ref(''),
  draftUnavailable = ref(false),
  queueState = ref({ pending: 0, saving: false, error: null })
const name = computed(() => user.userInfo?.realName || user.userInfo?.username || ''),
  questions = computed(() => paper.value?.questions || []),
  question = computed(() => questions.value[index.value]),
  answeredCount = computed(() => questions.value.filter((q) => answers[q.id]?.trim()).length),
  conflict = computed(() => queueState.value.error?.code === 40901),
  names = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题' },
  draftKey = `zhikao:draft:${user.userInfo?.id || user.userInfo?.username}:${examId}`
let deadline = 0,
  ticker,
  saveTimer,
  queue,
  disposed = false,
  timeoutAttempt = 0
const clock = computed(
  () =>
    `${String(Math.floor(seconds.value / 3600)).padStart(2, '0')}:${String(Math.floor((seconds.value % 3600) / 60)).padStart(2, '0')}:${String(seconds.value % 60).padStart(2, '0')}`,
)
function calibrate(n) {
  deadline = performance.now() + Number(n) * 1000
  seconds.value = Math.max(0, Number(n))
}
function storeDraft() {
  try {
    if (receipt.value) return
    const pending = {}
    for (const id of Object.keys(answers))
      if (answers[id] !== saved[id])
        pending[id] = { answer: answers[id], baseVersion: versions[id] || 0 }
    localStorage.setItem(draftKey, JSON.stringify(pending))
  } catch {
    draftUnavailable.value = true
  }
}
function removeDraft() {
  try {
    localStorage.removeItem(draftKey)
  } catch {}
}
function initializeQueue() {
  queue = createAnswerQueue({
    answers,
    versions,
    saved,
    send: async (d) => {
      const left = await saveAnswer(examId, d)
      if (!disposed) calibrate(left)
    },
    onChange: (s) => {
      queueState.value = s
      storeDraft()
    },
  })
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    paper.value = await enterExam(examId)
    Object.assign(answers, paper.value.savedAnswers)
    Object.assign(saved, paper.value.savedAnswers)
    Object.assign(versions, paper.value.answerVersions)
    for (const q of questions.value) {
      answers[q.id] ??= ''
      saved[q.id] ??= ''
    }
    calibrate(paper.value.remainingSeconds)
    initializeQueue()
    try {
      const draft = JSON.parse(localStorage.getItem(draftKey) || '{}')
      let restored = 0,
        stale = 0
      for (const [id, d] of Object.entries(draft)) {
        if (!(id in answers)) continue
        if ((versions[id] || 0) !== d.baseVersion) {
          stale++
          continue
        }
        queue.edit(id, String(d.answer))
        restored++
      }
      if (restored) {
        draftNotice.value = `已恢复 ${restored} 道题的本地草稿，正在补存至服务器。`
        retry()
      }
      if (stale) draftNotice.value = '检测到其他页面更新的作答，已使用服务器最新版本。'
    } catch {
      draftUnavailable.value = true
    }
  } catch (e) {
    try {
      const exams = await myExams()
      const e = exams.find((x) => String(x.id) === examId)
      if (e?.myStatus >= 2) {
        receipt.value = await submitExam(examId)
        removeDraft()
        return
      }
    } catch {}
    error.value = e.message || '考试加载失败，请检查考试状态'
  } finally {
    loading.value = false
  }
}
function edit(id, value) {
  queue.edit(id, value)
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => retry(), 650)
}
async function retry() {
  if (!queue || conflict.value || receipt.value || disposed) return
  try {
    await queue.flush()
  } catch {}
}
function toggleFlag() {
  const id = question.value.id
  flags.has(id) ? flags.delete(id) : flags.add(id)
}
async function reloadConflict() {
  try {
    await ElMessageBox.confirm(
      '当前页面存在其他窗口更新的作答。重新载入将以服务器已保存的版本为准，请先记下本页未保存内容。',
      '重新载入确认',
    )
    removeDraft()
    window.location.reload()
  } catch {}
}
async function submit(timeout = false) {
  if (submitting.value || receipt.value) return
  if (!timeout) {
    try {
      const missing = questions.value.length - answeredCount.value
      await ElMessageBox.confirm(
        missing
          ? `还有 ${missing} 道题未作答，确认提交试卷？`
          : '确认提交试卷？提交后无法继续修改。',
        '提交试卷',
        { confirmButtonText: '确认提交', cancelButtonText: '继续检查' },
      )
    } catch {
      return
    }
  }
  submitting.value = true
  error.value = ''
  clearTimeout(saveTimer)
  try {
    if (!timeout) await queue.flush()
    receipt.value = await submitExam(examId)
    removeDraft()
    clearInterval(ticker)
  } catch (e) {
    error.value = timeout
      ? '自动交卷暂未确认。请保持页面开启，系统将重试；服务器也会自动收卷。'
      : `交卷未完成：${e.message || '请重试'}`
    storeDraft()
  } finally {
    submitting.value = false
  }
}
function beforeUnload(e) {
  if (paper.value && !receipt.value) {
    storeDraft()
    e.preventDefault()
    e.returnValue = ''
  }
}
function blur() {
  if (paper.value?.cheatCheck === 1 && !receipt.value)
    reportCheat(examId, { type: 'hidden', paperQuestionId: question.value?.id })
      .then((r) => {
        if (r.action === 'force_submitted') submit(true)
      })
      .catch(() => {})
}
function visibility() {
  if (document.visibilityState === 'hidden') blur()
}
onBeforeRouteLeave(async () => {
  if (!paper.value || receipt.value) return true
  try {
    await ElMessageBox.confirm(
      '离开不会停止考试计时。已保存的作答可以继续，本地未确认内容会保留草稿。',
      '离开考试',
      { confirmButtonText: '保存并离开', cancelButtonText: '继续答题' },
    )
    await queue.flush()
    return true
  } catch {
    return false
  }
})
onMounted(async () => {
  await load()
  ticker = setInterval(() => {
    if (!paper.value || receipt.value) return
    seconds.value = Math.max(0, Math.ceil((deadline - performance.now()) / 1000))
    if (seconds.value === 0 && Date.now() - timeoutAttempt > 10000) {
      timeoutAttempt = Date.now()
      submit(true)
    }
  }, 500)
  window.addEventListener('beforeunload', beforeUnload)
  window.addEventListener('online', retry)
  document.addEventListener('visibilitychange', visibility)
})
onUnmounted(() => {
  disposed = true
  clearInterval(ticker)
  clearTimeout(saveTimer)
  window.removeEventListener('beforeunload', beforeUnload)
  window.removeEventListener('online', retry)
  document.removeEventListener('visibilitychange', visibility)
})
</script>
