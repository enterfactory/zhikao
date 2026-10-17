<template>
  <div v-loading="loading">
    <div class="study-page-title">
      <div>
        <p class="eyebrow">EXAM SCHEDULE</p>
        <h1>考试安排</h1>
        <p>确认考试时间，留出从容作答的空间。</p>
      </div>
      <button class="subtle-button" @click="load">↻ 刷新安排</button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="study-tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        :class="{ active: tab === t.key }"
        @click="tab = t.key"
      >
        {{ t.label }}
        <span>{{ count(t.key) }}</span>
      </button>
    </div>
    <section class="study-panel exam-list">
      <article v-for="e in shown" :key="e.id" class="exam-row">
        <div class="exam-date">
          <strong>{{ date(e.startTime).slice(8, 10) }}</strong>
          <span>{{ date(e.startTime).slice(0, 7) }}</span>
        </div>
        <div class="exam-info">
          <div>
            <span class="tag" :class="{ 'tag-success': e.myStatus >= 2 }">
              {{ e.myStatus >= 2 ? '已交卷' : e.statusName }}
            </span>
            <span class="muted">{{ e.subjectName }}</span>
          </div>
          <h2>{{ e.examName }}</h2>
          <p>
            {{ date(e.startTime) }} — {{ date(e.endTime) }}
            <span>答题时长 {{ e.duration }} 分钟</span>
          </p>
        </div>
        <div class="exam-action">
          <template v-if="e.myStatus >= 2">
            <strong>
              {{ e.releaseStatus >= 1 ? `${e.myTotalScore ?? '—'} 分` : '已收到答卷' }}
            </strong>
            <p>{{ e.releaseStatus >= 1 ? '成绩已公布' : '等待教师完成阅卷与发布' }}</p>
            <router-link v-if="e.releaseStatus >= 1" to="/student/grade">
              查看学习记录 →
            </router-link>
          </template>
          <el-button
            v-else
            type="primary"
            :disabled="!canEnter(e)"
            @click="router.push({ path: '/student/answering', query: { id: e.id } })"
          >
            {{ e.myStatus === 1 ? '继续答题' : canEnter(e) ? '进入考试' : '等待开考 / 已结束' }}
          </el-button>
        </div>
      </article>
      <el-empty v-if="!shown.length && !loading" description="暂无相关考试" />
    </section>
    <p class="study-help">交卷后可查看提交状态。成绩与解析由教师在全场考试结束后分别发布。</p>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myExams } from '@/api/exam'
const router = useRouter(),
  rows = ref([]),
  loading = ref(false),
  error = ref(''),
  tab = ref('all'),
  tabs = [
    { key: 'all', label: '全部考试' },
    { key: 'pending', label: '待参加' },
    { key: 'done', label: '已交卷' },
  ]
const filter = (r, k) => k === 'all' || (k === 'done' ? r.myStatus >= 2 : !(r.myStatus >= 2))
const count = (k) => rows.value.filter((r) => filter(r, k)).length,
  shown = computed(() => rows.value.filter((r) => filter(r, tab.value)))
const date = (v) =>
  String(v || '')
    .replace('T', ' ')
    .slice(0, 16)
const canEnter = (e) =>
  new Date(e.startTime) <= new Date() && new Date(e.endTime) > new Date() && e.status === 1
async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await myExams()
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
