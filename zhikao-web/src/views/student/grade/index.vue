<template>
  <div v-loading="loading">
    <div class="study-page-title">
      <div>
        <p class="eyebrow">LEARNING RECORDS</p>
        <h1>学习记录</h1>
        <p>回顾已经完成的考试与练习，找到下一步的方向。</p>
      </div>
      <button class="subtle-button" @click="load">↻ 刷新记录</button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="study-columns">
      <div>
        <section class="study-panel">
          <div class="section-heading">
            <h2>考试反馈</h2>
            <span class="muted">{{ grades.length }} 场已公布</span>
          </div>
          <article v-for="g in grades" :key="g.examId" class="grade-record">
            <div class="grade-record-score">
              <strong>{{ g.totalScore }}</strong>
              <span>/ {{ g.paperTotalScore }} 分</span>
            </div>
            <div>
              <span class="tag">{{ g.subjectName }}</span>
              <h3>{{ g.examName }}</h3>
              <p>{{ fmt(g.submitTime) }} · {{ g.statusName }}</p>
            </div>
            <el-button :disabled="g.releaseStatus !== 2" @click="review(g)">
              {{ g.releaseStatus === 2 ? '查看试卷与解析' : '解析尚未公布' }}
            </el-button>
          </article>
          <el-empty v-if="!grades.length" description="暂无已公布的考试成绩" />
        </section>
        <section class="study-panel history-panel">
          <div class="section-heading">
            <h2>最近练习</h2>
            <router-link to="/student/wrong">继续练习 →</router-link>
          </div>
          <div v-for="(h, i) in history" :key="i" class="history-row">
            <span class="history-status" :class="{ wrong: h.correct === 0 }">
              {{ h.correct === 1 ? '✓' : h.correct === 0 ? '↻' : '—' }}
            </span>
            <div>
              <strong>{{ h.knowledge_point || h.topic || '综合练习' }}</strong>
              <p>
                {{ fmt(h.answered_at) }} ·
                {{ h.correct === null ? '自查练习' : h.correct === 1 ? '回答正确' : '待巩固' }}
              </p>
            </div>
            <router-link :to="{ path: '/student/wrong', query: { session: h.session_id } }">
              回看 →
            </router-link>
          </div>
          <p v-if="!history.length" class="empty-inline">完成练习后，这里会留下你的学习记录。</p>
        </section>
      </div>
      <aside class="study-panel knowledge-panel">
        <span class="eyebrow">KNOWLEDGE REVIEW</span>
        <h3>考试中的知识点表现</h3>
        <p>来自已公布解析的考试，供复习参考。</p>
        <div v-for="(k, i) in knowledge" :key="i" class="knowledge-row">
          <strong>{{ k.knowledgePoint || k.knowledge_point }}</strong>
          <span>{{ k.accuracy ?? 0 }}%</span>
          <div class="knowledge-bar">
            <i :style="{ width: Math.min(100, Math.max(0, k.accuracy || 0)) + '%' }"></i>
          </div>
        </div>
        <p v-if="!knowledge.length" class="empty-inline">暂无知识点反馈。</p>
        <router-link to="/student/wrong?mode=wrong">回到历史错题 →</router-link>
      </aside>
    </div>
    <el-dialog
      v-model="dialog"
      :title="paper?.examName || '试卷反馈'"
      width="900px"
      class="review-dialog"
    >
      <article v-for="(q, i) in paper?.questions || []" :key="i" class="paper-review-question">
        <div class="question-topline">
          <b>第 {{ i + 1 }} 题 · {{ q.typeName }}</b>
          <span>{{ q.gotScore ?? '待阅' }} / {{ q.fullScore }} 分</span>
        </div>
        <RichContent :html="q.content" />
        <RichContent :html="q.options" />
        <p>我的答案：{{ q.myAnswer || '未作答' }}</p>
        <div class="answer-feedback">
          <p>参考答案：{{ q.standardAnswer }}</p>
          <RichContent :html="q.analysis" />
        </div>
      </article>
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { myGrades, myPaper } from '@/api/exam'
import { wrongKnowledge } from '@/api/learn'
import { practiceHistory } from '@/api/workflow'
import RichContent from '@/components/RichContent.vue'
const grades = ref([]),
  knowledge = ref([]),
  history = ref([]),
  loading = ref(true),
  error = ref(''),
  dialog = ref(false),
  paper = ref(null),
  fmt = (v) =>
    String(v || '')
      .replace('T', ' ')
      .slice(0, 16)
async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[grades.value, knowledge.value, history.value] = await Promise.all([
      myGrades(),
      wrongKnowledge(),
      practiceHistory(),
    ])
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
async function review(g) {
  try {
    paper.value = await myPaper(g.examId)
    dialog.value = true
  } catch (e) {
    error.value = e.message || '试卷加载失败'
  }
}
onMounted(load)
</script>
