<template>
  <div v-loading="loading">
    <div class="study-page-title">
      <div>
        <p class="eyebrow">PRACTICE & REVIEW</p>
        <h1>练习与错题</h1>
        <p>在练习中检验理解，让反馈落到每一道题。</p>
      </div>
      <router-link to="/student/grade">查看学习记录 →</router-link>
    </div>
    <div class="study-tabs">
      <button :class="{ active: !wrongOnly }" @click="switchMode(false)">知识点练习</button>
      <button :class="{ active: wrongOnly }" @click="switchMode(true)">历史错题</button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="practice-toolbar">
      <label>
        练习范围
        <select v-model="topic">
          <option value="">全部知识点</option>
          <option v-for="t in topics" :key="t.topic_key || t.topic" :value="t.topic_key || t.topic">
            {{ t.subject_name ? `${t.subject_name} / ` : '' }}{{ t.topic }} · {{ t.total }} 题
          </option>
        </select>
      </label>
      <el-button type="primary" :loading="creating" @click="begin">
        {{ session ? '开始一组新练习' : '开始练习' }}
      </el-button>
      <span class="muted">每组最多 30 题 · 仅使用已开放解析的考试题</span>
    </div>
    <div v-if="question" class="practice-layout">
      <section class="study-panel practice-question">
        <div class="question-topline">
          <span class="tag">{{ names[question.type] }}</span>
          <span>{{ question.knowledge_point || '综合巩固' }}</span>
          <span class="muted">{{ index + 1 }} / {{ items.length }}</span>
        </div>
        <RichContent class="question-stem" :html="question.content" />
        <QuestionInput
          v-model="answer"
          :type="question.type"
          :options="question.options"
          :disabled="!!feedback || sending"
        />
        <div
          v-if="feedback"
          class="answer-feedback"
          :class="{ incorrect: feedback.correct === false }"
        >
          <strong>
            {{
              feedback.subjective
                ? '请对照参考答案，自查解题过程'
                : feedback.correct
                  ? '回答正确'
                  : '这道题还需要再理解一下'
            }}
          </strong>
          <p>
            参考答案：
            <b>{{ feedback.answer }}</b>
          </p>
          <RichContent :html="feedback.analysis" empty-text="暂无补充解析" />
          <p class="feedback-source">题目来源 · {{ feedback.source }}</p>
        </div>
        <div class="question-actions">
          <el-button :disabled="index === 0" @click="move(-1)">上一题</el-button>
          <span>{{ answeredCount }} / {{ items.length }} 题已练习</span>
          <el-button
            v-if="!feedback"
            type="primary"
            :disabled="!answer.trim()"
            :loading="sending"
            @click="submit"
          >
            提交答案
          </el-button>
          <el-button v-else type="primary" :disabled="index === items.length - 1" @click="move(1)">
            下一题 →
          </el-button>
        </div>
        <div v-if="answeredCount === items.length" class="completion-note">
          本组练习已完成。你可以回看各题，或开始新一组练习。
        </div>
      </section>
      <aside class="study-panel practice-outline">
        <span class="eyebrow">PRACTICE OUTLINE</span>
        <h3>本组练习</h3>
        <p>{{ wrongOnly ? '历史错题回顾' : topic || '综合练习' }}</p>
        <div class="question-grid">
          <button
            v-for="(q, i) in items"
            :key="q.id"
            :class="{ active: index === i, done: !!q.answered_at }"
            @click="select(i)"
          >
            {{ i + 1 }}
          </button>
        </div>
        <div class="outline-key">
          <span>● 已练习</span>
          <span>○ 未练习</span>
        </div>
        <hr />
        <p>练习不会修改原考试成绩。</p>
      </aside>
    </div>
    <section v-else class="study-panel practice-empty">
      <div class="practice-empty-icon">ƒ(x)</div>
      <h2>{{ wrongOnly ? '把错题变成理解的起点' : '选一个知识点，开始巩固' }}</h2>
      <p>
        {{
          topics.length
            ? '选择练习范围后点击开始练习。每题提交后可查看参考答案与解析。'
            : '暂无已开放的练习题。教师公布考试解析后即可开始。'
        }}
      </p>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RichContent from '@/components/RichContent.vue'
import QuestionInput from '@/components/QuestionInput.vue'
import { studyTopics, startPractice, getPractice, answerPractice } from '@/api/workflow'
const route = useRoute(),
  router = useRouter(),
  topics = ref([]),
  topic = ref(route.query.topic || ''),
  wrongOnly = ref(route.query.mode === 'wrong'),
  session = ref(null),
  index = ref(0),
  answer = ref(''),
  feedback = ref(null),
  loading = ref(true),
  creating = ref(false),
  sending = ref(false),
  error = ref(''),
  feedbacks = new Map()
const names = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题' },
  items = computed(() => session.value?.items || []),
  question = computed(() => items.value[index.value]),
  answeredCount = computed(() => items.value.filter((q) => q.answered_at).length)
function select(i) {
  index.value = i
  answer.value = String(question.value?.actual_answer || '')
  feedback.value = feedbacks.get(question.value?.id) || question.value?.feedback || null
}
function move(d) {
  select(index.value + d)
}
async function switchMode(w) {
  wrongOnly.value = w
  session.value = null
  feedbacks.clear()
  await router.replace({ query: { mode: w ? 'wrong' : 'topic', topic: topic.value } })
}
async function begin() {
  creating.value = true
  error.value = ''
  try {
    session.value = await startPractice({ topic: topic.value, wrongOnly: wrongOnly.value })
    feedbacks.clear()
    select(0)
    await router.replace({
      query: {
        session: session.value.id,
        mode: wrongOnly.value ? 'wrong' : 'topic',
        topic: topic.value,
      },
    })
  } catch (e) {
    error.value = e.message || '练习创建失败'
  } finally {
    creating.value = false
  }
}
async function submit() {
  sending.value = true
  error.value = ''
  const q = question.value
  try {
    feedback.value = await answerPractice(session.value.id, { itemId: q.id, answer: answer.value })
    feedbacks.set(q.id, feedback.value)
    q.actual_answer = answer.value
    q.answered_at = new Date().toISOString()
  } catch (e) {
    error.value = e.message || '提交失败，请重试'
  } finally {
    sending.value = false
  }
}
onMounted(async () => {
  try {
    topics.value = await studyTopics()
    if (route.query.session) {
      session.value = await getPractice(route.query.session)
      select(0)
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
})
</script>
