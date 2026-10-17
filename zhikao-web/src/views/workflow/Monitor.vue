<template>
  <div v-loading="loading">
    <div class="workspace-page-title">
      <div>
        <h1>考试监控与发布</h1>
        <p>核对收卷和评分进度后，分别开放成绩与解析。</p>
      </div>
      <el-button @click="load">刷新状态</el-button>
    </div>
    <div class="business-panel monitor-selector">
      <el-select v-model="examId" placeholder="选择一场考试" filterable @change="refresh">
        <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id" />
      </el-select>
      <span class="tag">{{ selected?.statusName || '等待选择考试' }}</span>
      <span class="muted">{{ selected?.className }}</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="monitor">
      <el-alert :title="monitor.rosterBasis === 'publication_snapshot' ? '应考人数依据发布时冻结的名单，转班不会改变本场考试统计。' : '历史考试未保留发布名单：人数仅反映实际参与者，不能视为完整应考人数。'" type="info" :closable="false" />
      <div class="business-metrics">
        <div v-for="s in stats" :key="s.key">
          <span>{{ s.label }}</span>
          <strong>
            {{ monitor[s.key] ?? 0 }}
            <small>人</small>
          </strong>
        </div>
      </div>
      <section class="business-panel">
        <div class="section-heading">
          <h2>成绩发布流程</h2>
          <span class="tag">
            {{ ['尚未发布', '已开放成绩', '已开放解析'][selected?.releaseStatus || 0] }}
          </span>
        </div>
        <div class="release-step">
          <span>01</span>
          <div>
            <h3>结束考试与收卷</h3>
            <p>考试结束后，系统会自动收取超时答卷。请刷新状态核对人数。</p>
          </div>
          <el-button :disabled="!selected?.published || selected?.status === 2" @click="finish">
            提前结束考试
          </el-button>
        </div>
        <div class="release-step">
          <span>02</span>
          <div>
            <h3>完成阅卷</h3>
            <p>
              当前已交卷 {{ monitor.submittedCount }} 人，已完成评分 {{ monitor.markedCount }} 人。
            </p>
          </div>
          <router-link :to="{ path: '/teacher/marking', query: { examId: examId } }">
            前往阅卷 →
          </router-link>
        </div>
        <div class="release-step">
          <span>03</span>
          <div>
            <h3>开放成绩</h3>
            <p>全场结束且全部已收答卷完成评分后，学生可以查询本人成绩。</p>
          </div>
          <el-button :disabled="selected?.releaseStatus >= 1" @click="release(1)">
            发布成绩
          </el-button>
        </div>
        <div class="release-step">
          <span>04</span>
          <div>
            <h3>开放解析与考后练习</h3>
            <p>学生可查看原卷解析，并基于已开放试题创建知识点练习。</p>
          </div>
          <el-button type="primary" :disabled="selected?.releaseStatus === 2" @click="release(2)">
            发布解析与练习
          </el-button>
        </div>
      </section>
    </template>
  </div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { pageExams, monitorExam, finishExam } from '@/api/exam'
import { releaseResults } from '@/api/workflow'
import { ElMessage, ElMessageBox } from 'element-plus'
const route = useRoute(),
  exams = ref([]),
  examId = ref(Number(route.query.examId || route.query.id) || null),
  monitor = ref(null),
  loading = ref(false),
  error = ref(''),
  selected = computed(() => exams.value.find((e) => String(e.id) === String(examId.value))),
  stats = [
    { key: 'shouldCount', label: '应考人数' },
    { key: 'enteredCount', label: '已进入考试' },
    { key: 'submittedCount', label: '已交卷' },
    { key: 'markedCount', label: '已完成评分' },
  ]
let timer
async function refresh() {
  if (!examId.value) return
  try {
    monitor.value = await monitorExam(examId.value)
    error.value = ''
  } catch (e) {
    error.value = e.message || '加载失败'
  }
}
async function load() {
  loading.value = true
  try {
    const r = await pageExams({ pageNum: 1, pageSize: 200 })
    exams.value = r.records
    if (!examId.value) examId.value = r.records[0]?.id
    await refresh()
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
async function finish() {
  try {
    await ElMessageBox.confirm(
      '提前结束后，考生将无法继续作答，系统将收取已保存答案。确认结束？',
      '结束考试',
      { type: 'warning' },
    )
    await finishExam(examId.value)
    ElMessage.success('考试已结束，系统正在收卷')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = e.message || '操作失败'
  }
}
async function release(level) {
  try {
    await ElMessageBox.confirm(
      level === 2
        ? '开放后学生可以查看标准答案、解析并开始练习。确认发布？'
        : '确认向本场学生发布成绩？',
      '发布确认',
    )
    await releaseResults(examId.value, level)
    ElMessage.success('已发布')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = e.message || '发布失败'
  }
}
onMounted(() => {
  load()
  timer = setInterval(() => {
    if (examId.value) refresh()
  }, 15000)
})
onUnmounted(() => clearInterval(timer))
</script>
