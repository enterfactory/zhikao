<template>
  <AppPage title="阅卷工作台" description="核对学生作答与参考答案，独立完成教师评分。">
    <template #actions>
      <span class="console-cell-note">
        已完成 {{ rows.length - pendingCount }} / {{ rows.length }}
      </span>
      <el-button :disabled="!examId" @click="openLogs">批改记录</el-button>
    </template>
    <section class="console-panel console-context">
      <el-select
        :model-value="examId"
        :disabled="saving"
        placeholder="选择考试"
        filterable
        @change="switchExam"
      >
        <el-option
          v-for="e in exams"
          :key="e.id"
          :label="e.examName + ' · ' + (e.className || '')"
          :value="e.id"
        />
      </el-select>
      <el-button :disabled="saving" @click="refresh">刷新</el-button>
      <el-switch
        :model-value="pendingOnly"
        :disabled="saving"
        active-text="只看待评"
        @change="switchPending"
      />
    </section>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <SkeletonBlock v-if="loading && !rows.length" :rows="6" />
    <EmptyState
      v-else-if="!filteredRows.length"
      title="暂无待阅答卷"
      description="可切换考试或关闭只看待评，查看已完成的批改。"
    />
    <div v-else class="marking-workspace" v-loading="loading">
      <aside class="console-panel console-directory">
        <div class="console-panel-head">答卷目录</div>
        <button
          v-for="r in filteredRows"
          :key="r.answerId"
          :disabled="saving"
          :class="{ active: selected?.answerId === r.answerId }"
          @click="selectAnswer(r)"
        >
          <span>
            {{ r.studentName }}
            <small class="console-cell-note">第 {{ r.sort }} 题</small>
          </span>
          <el-tag size="small" :type="r.marked === 1 ? 'success' : 'warning'">
            {{ r.marked === 1 ? '已评' : '待评' }}
          </el-tag>
        </button>
      </aside>
      <section v-if="selected" class="console-panel">
        <header class="console-panel-head">
          <h3>{{ selected.studentName }}的答卷</h3>
          <span>{{ selected.fullScore }} 分</span>
        </header>
        <div class="console-workspace-tabs">
          <button :class="{ active: answerTab === 'answer' }" @click="answerTab = 'answer'">
            题目与作答
          </button>
          <button :class="{ active: answerTab === 'reference' }" @click="answerTab = 'reference'">
            参考答案
          </button>
        </div>
        <div class="console-panel-body">
          <div class="console-question-meta">第 {{ selected.sort }} 题</div>
          <RichContent :html="selected.content" />
          <template v-if="answerTab === 'answer'">
            <div class="console-section-label">学生作答</div>
            <div class="student-response">
              <RichContent :html="selected.studentAnswer" empty-text="未作答" />
            </div>
          </template>
          <template v-else>
            <div class="console-section-label">参考答案</div>
            <RichContent :html="selected.standardAnswer" />
          </template>
        </div>
      </section>
      <aside v-if="selected" class="console-panel">
        <header class="console-panel-head">本题评分</header>
        <div class="console-panel-body">
          <label for="mark-score">教师评分 / {{ selected.fullScore }} 分</label>
          <el-input-number
            id="mark-score"
            v-model="selected.inputScore"
            :min="0"
            :max="Number(selected.fullScore)"
            :step="0.5"
            :precision="2"
            :disabled="saving"
          />
          <div class="score-shortcuts">
            <el-button size="small" :disabled="saving" @click="selected.inputScore = 0">
              零分
            </el-button>
            <el-button
              size="small"
              :disabled="saving"
              @click="selected.inputScore = Number(selected.fullScore) / 2"
            >
              半分
            </el-button>
            <el-button
              size="small"
              :disabled="saving"
              @click="selected.inputScore = Number(selected.fullScore)"
            >
              满分
            </el-button>
          </div>
          <div v-if="suggestion" class="console-answer-box">
            <h4>AI 评分建议 · {{ suggestion.suggestScore }} 分</h4>
            <p>{{ suggestion.reason }}</p>
            <el-button text type="primary" :disabled="saving" @click="applySuggestion">
              采用建议分数
            </el-button>
          </div>
          <p class="console-form-hint">
            评分建议仅供参考，以教师确认保存的分数为准。修改已评分答卷需填写理由。
          </p>
          <el-button text type="primary" :loading="suggesting" :disabled="saving" @click="suggest">
            获取 AI 建议
          </el-button>
          <div class="mark-actions">
            <el-button :loading="saving" @click="save(false)">保存评分</el-button>
            <el-button type="primary" :loading="saving" @click="save(true)">保存并下一份</el-button>
          </div>
        </div>
      </aside>
    </div>
    <ZkDrawer v-model="logVisible" title="批改留痕" size="lg">
      <el-table v-loading="logLoading" :data="logs" size="small" border>
        <el-table-column prop="studentName" label="学生" width="90" />
        <el-table-column label="题目" width="70">
          <template #default="{ row }">第 {{ row.sort }} 题</template>
        </el-table-column>
        <el-table-column label="动作" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.action === 2 ? 'warning' : 'info'">
              {{ row.actionName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分数变化" width="130">
          <template #default="{ row }">
            <span v-if="row.oldScore != null">{{ row.oldScore }} →</span>
            <b>{{ row.newScore }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="理由" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="90" />
        <el-table-column prop="createTime" label="时间" width="150" />
      </el-table>
      <EmptyState
        v-if="!logLoading && !logs.length"
        title="暂无批改记录"
        description="首评与改分都会留痕，改分时必须填写理由"
      />
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import AppPage from '@/components/AppPage.vue'
import RichContent from '@/components/RichContent.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import EmptyState from '@/components/EmptyState.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import { markAnswer, markLogs, pageExams, subjectiveList } from '@/api/exam'
import { aiAssistMark } from '@/api/ai'
const route = useRoute(),
  exams = ref([]),
  examId = ref(null),
  rows = ref([]),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  pendingOnly = ref(false),
  selectedId = ref(null),
  answerTab = ref('answer'),
  suggestion = ref(null),
  suggesting = ref(false),
  logs = ref([]),
  logVisible = ref(false),
  logLoading = ref(false)
const pendingCount = computed(() => rows.value.filter((r) => r.marked !== 1).length),
  filteredRows = computed(() => rows.value.filter((r) => !pendingOnly.value || r.marked !== 1)),
  selected = computed(
    () => filteredRows.value.find((r) => r.answerId === selectedId.value) || filteredRows.value[0],
  )
let version = 0
const dirty = computed(() => rows.value.some((r) => Number(r.inputScore) !== Number(r.score || 0)))
async function allowDiscard() {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('有尚未保存的评分，是否放弃修改？', '未保存评分', {
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续评分',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}
async function refresh() {
  if (await allowDiscard()) await load()
}
function beforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}
onBeforeRouteLeave(() => (saving.value ? false : allowDiscard()))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
async function load() {
  const v = ++version
  loading.value = true
  error.value = ''
  try {
    const list = examId.value ? await subjectiveList(examId.value) : []
    if (v === version) {
      rows.value = list.map((r) => ({ ...r, inputScore: Number(r.score || 0) }))
      suggestion.value = null
    }
  } catch (e) {
    if (v === version) error.value = e.msg || e.message || '答卷加载失败'
  } finally {
    if (v === version) loading.value = false
  }
}
function resetScores() {
  rows.value.forEach((r) => {
    r.inputScore = Number(r.score || 0)
  })
}
async function switchPending(value) {
  if (!(await allowDiscard())) return
  resetScores()
  pendingOnly.value = value
  suggestion.value = null
}
async function selectAnswer(r) {
  if (r.answerId === selected.value?.answerId || !(await allowDiscard())) return
  resetScores()
  selectedId.value = r.answerId
  suggestion.value = null
  answerTab.value = 'answer'
}
async function switchExam(id) {
  if (saving.value || !(await allowDiscard())) return
  examId.value = id
  selectedId.value = null
  suggestion.value = null
  load()
}
async function save(next) {
  const r = selected.value
  if (!r || saving.value) return
  if (!Number.isFinite(r.inputScore) || r.inputScore < 0 || r.inputScore > Number(r.fullScore)) {
    ElMessage.warning('评分必须在零分和满分之间')
    return
  }
  let reason = ''
  saving.value = true
  try {
    if (r.marked === 1) {
      try {
        const p = await ElMessageBox.prompt('请说明修改评分的理由', '修改评分', {
          inputValidator: (v) => (v?.trim() ? true : '请填写改分理由'),
        })
        reason = p.value.trim()
      } catch {
        return
      }
    }
    const before = filteredRows.value
    const nextId = before[before.findIndex((q) => q.answerId === r.answerId) + 1]?.answerId
    await markAnswer(r.answerId, r.inputScore, reason)
    r.score = r.inputScore
    r.marked = 1
    await load()
    if (next) {
      selectedId.value =
        nextId || filteredRows.value.find((q) => q.marked !== 1)?.answerId || r.answerId
      suggestion.value = null
    }
    ElMessage.success('评分已保存')
  } finally {
    saving.value = false
  }
}
async function openLogs() {
  logVisible.value = true
  logLoading.value = true
  try {
    logs.value = await markLogs(examId.value)
  } finally {
    logLoading.value = false
  }
}
async function suggest() {
  const r = selected.value
  if (!r) return
  const id = r.answerId
  suggesting.value = true
  try {
    const s = await aiAssistMark({
      fullScore: Number(r.fullScore),
      question: r.content,
      standardAnswer: r.standardAnswer,
      studentAnswer: r.studentAnswer,
    })
    if (selected.value?.answerId === id) suggestion.value = s
  } finally {
    suggesting.value = false
  }
}
function applySuggestion() {
  const n = Number(suggestion.value?.suggestScore)
  if (Number.isFinite(n))
    selected.value.inputScore = Math.min(Number(selected.value.fullScore), Math.max(0, n))
}
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  try {
    const r = await pageExams({ pageNum: 1, pageSize: 100 })
    exams.value = r.records || []
    examId.value = Number(route.query.examId) || exams.value[0]?.id
    await load()
  } catch (e) {
    error.value = e.msg || e.message || '考试加载失败'
  }
})
</script>
<style scoped>
.marking-workspace {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) 250px;
  gap: 16px;
  align-items: start;
}
.student-response {
  padding: 20px;
  background: #f7f8fa;
  border: 1px solid #e8ebf0;
  border-radius: 4px;
  min-height: 180px;
}
.score-shortcuts {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}
.mark-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 20px;
}
.mark-actions .el-button {
  margin: 0;
}
.marking-workspace .console-panel-body > .el-input-number {
  display: flex;
  margin-top: 12px;
  width: 100%;
}
@media (max-width: 1150px) {
  .marking-workspace {
    grid-template-columns: 150px minmax(0, 1fr);
  }
  .marking-workspace > aside:last-child {
    grid-column: 2;
  }
}
@media (max-width: 700px) {
  .marking-workspace {
    grid-template-columns: 1fr;
  }
  .marking-workspace > aside:last-child {
    grid-column: auto;
  }
  .marking-workspace .console-directory {
    max-height: 200px;
    overflow: auto;
  }
}
</style>
