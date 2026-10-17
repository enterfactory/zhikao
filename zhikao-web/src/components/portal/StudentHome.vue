<template>
  <div v-loading="loading">
    <div class="study-page-title">
      <div>
        <p class="eyebrow">MY LEARNING</p>
        <h1>我的学习</h1>
        <p>从一个知识点开始，学得更扎实。</p>
      </div>
      <span class="quiet-date">{{ today }}</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <button class="text-button" @click="load">重新加载</button>
    </el-alert>
    <div class="study-columns">
      <div class="study-primary">
        <div v-if="nextExam" class="exam-reminder">
          <span class="small-badge">考试提醒</span>
          <span>
            <strong>{{ nextExam.examName }}</strong>
            <span class="muted">· {{ fmt(nextExam.startTime) }}</span>
          </span>
          <router-link to="/student/exam">查看安排 →</router-link>
        </div>
        <section class="study-panel learning-feature">
          <div class="math-cover">
            <span class="cover-label">ZHIKAO / STUDY NOTES</span>
            <span class="math-equation">f ′(x)</span>
            <span class="math-small">∫ f(x) dx</span>
            <span class="math-axis"></span>
            <span class="cover-bottom">理解 · 练习 · 巩固</span>
          </div>
          <div class="feature-copy">
            <span class="tag">知识点练习</span>
            <h2>{{ topics[0]?.topic || '开启你的学习计划' }}</h2>
            <p>
              {{
                topics.length
                  ? '回到已学过的题目，在独立练习中理清思路、检验理解。'
                  : '完成考试并等待教师开放解析后，可在这里按知识点巩固练习。'
              }}
            </p>
            <div class="feature-meta">
              <span>{{ topics[0]?.total || 0 }} 道可练习题</span>
              <span>即时反馈与解析</span>
            </div>
            <el-button type="primary" :disabled="!topics.length" @click="practice(topics[0])">
              开始练习
              <span class="button-arrow">→</span>
            </el-button>
          </div>
        </section>
        <section class="study-section">
          <div class="section-heading">
            <h2>按知识点巩固</h2>
            <router-link to="/student/wrong">全部练习 →</router-link>
          </div>
          <div class="topic-grid">
            <button
              v-for="(t, i) in topics.slice(0, 3)"
              :key="t.topic"
              class="topic-tile"
              @click="practice(t)"
            >
              <div class="topic-art" :class="'topic-art-' + i">
                <span>{{ ['ƒ(x)', 'Σ', 'x²'][i] }}</span>
                <small>知识专题 / {{ String(i + 1).padStart(2, '0') }}</small>
              </div>
              <div class="topic-copy">
                <h3>{{ t.topic }}</h3>
                <p>{{ t.total }} 道题 · 自主练习</p>
                <span>
                  进入练习
                  <b>↗</b>
                </span>
              </div>
            </button>
            <div v-if="!topics.length" class="empty-inline">
              暂无已开放的知识点。你可以先查看考试安排。
            </div>
          </div>
        </section>
        <section class="study-panel recent-results">
          <div class="section-heading">
            <h2>最近的学习反馈</h2>
            <router-link to="/student/grade">查看记录 →</router-link>
          </div>
          <div v-for="g in grades.slice(0, 2)" :key="g.examId" class="result-row">
            <span class="document-glyph">▤</span>
            <div>
              <strong>{{ g.examName }}</strong>
              <p>{{ g.subjectName }} · {{ fmt(g.submitTime) }}</p>
            </div>
            <span class="result-score">
              {{ g.totalScore }}
              <small>/ {{ g.paperTotalScore }}</small>
            </span>
          </div>
          <p v-if="!grades.length" class="empty-inline">教师发布成绩后，学习反馈会显示在这里。</p>
        </section>
      </div>
      <aside class="study-secondary">
        <section class="study-panel learner-profile">
          <span class="user-avatar large">{{ name.slice(0, 1) }}</span>
          <h3>{{ name }}，你好</h3>
          <p>把每一次练习，变成下一次的进步。</p>
          <div class="profile-numbers">
            <div>
              <strong>{{ topics.length }}</strong>
              <span>可练习知识点</span>
            </div>
            <div>
              <strong>{{ history.length }}</strong>
              <span>最近练习题数</span>
            </div>
          </div>
        </section>
        <section class="study-panel side-exams">
          <div class="section-heading">
            <h3>考试日程</h3>
            <router-link to="/student/exam">全部 →</router-link>
          </div>
          <div v-for="e in upcoming.slice(0, 2)" :key="e.id" class="side-exam">
            <span class="calendar-icon">{{ day(e.startTime) }}</span>
            <div>
              <strong>{{ e.examName }}</strong>
              <p>{{ fmt(e.startTime) }}</p>
              <span class="tag">{{ e.statusName }}</span>
            </div>
          </div>
          <p v-if="!upcoming.length" class="empty-inline">近期没有待参加的考试。</p>
        </section>
        <section class="study-panel side-note">
          <span class="eyebrow">REVIEW & REFLECT</span>
          <h3>错题，是复习的好起点</h3>
          <p>回看已经开放的考试错题，找到需要再理解一步的地方。</p>
          <router-link to="/student/wrong?mode=wrong">练习历史错题 →</router-link>
        </section>
      </aside>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { myExams, myGrades } from '@/api/exam'
import { studyTopics, practiceHistory } from '@/api/workflow'
const router = useRouter(),
  user = useUserStore(),
  topics = ref([]),
  history = ref([]),
  grades = ref([]),
  exams = ref([]),
  loading = ref(true),
  error = ref('')
const name = computed(() => user.userInfo?.realName || user.userInfo?.username || '同学')
const today = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date())
const upcoming = computed(() =>
  exams.value
    .filter((e) => (e.myStatus ?? 0) < 2 && new Date(e.endTime) > new Date())
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime)),
)
const nextExam = computed(() => upcoming.value[0])
const fmt = (v) => (v ? String(v).replace('T', ' ').slice(5, 16) : '时间待定'),
  day = (v) => (v ? String(v).slice(8, 10) : '—')
function practice(t) {
  router.push({ path: '/student/wrong', query: { topic: t.topic } })
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[topics.value, history.value, grades.value, exams.value] = await Promise.all([
      studyTopics(),
      practiceHistory(),
      myGrades(),
      myExams(),
    ])
  } catch (e) {
    error.value = e.message || '学习数据加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
