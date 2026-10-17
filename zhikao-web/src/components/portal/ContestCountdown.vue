<template>
  <div v-if="exam" class="card side-card contest">
    <h3 class="sec-title"><span class="bar" />联考倒计时</h3>
    <p class="c-when">{{ exam.subjectName ? `${exam.subjectName} · ` : '' }}{{ exam.examName }} · {{ whenText }}</p>
    <div class="c-clock" v-if="!started">
      <div class="cu"><b>{{ parts.d }}</b><span>天</span></div>
      <div class="cu"><b>{{ parts.h }}</b><span>时</span></div>
      <div class="cu"><b>{{ parts.m }}</b><span>分</span></div>
      <div class="cu"><b>{{ parts.s }}</b><span>秒</span></div>
    </div>
    <div v-else class="c-clock">
      <div class="cu cu-wide"><b>考试已开始</b><span>祝你好运</span></div>
    </div>
    <button class="btn-enter" type="button" @click="goExam">报名并设置提醒</button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 联考倒计时卡（右栏首卡）。
 * 数据 = /portal/home 的 nextExam；契约缺失时 StudentHome 从 /exam/mine
 * 取最早一场「未开始」的考试兜底；再没有则父级隐藏整卡。
 * 秒级刷新，卸载时清理 interval。
 */
const props = defineProps({
  exam: { type: Object, default: null }
})

const router = useRouter()
const now = ref(Date.now())
let timer = null

/** 后端 LocalDateTime 序列化为「YYYY-MM-DD HH:mm:ss」或 ISO T 分隔，统一转 Date */
function parseTime(str) {
  if (!str) return null
  const s = String(str).trim()
  const t = new Date(s.includes('T') ? s : s.replace(' ', 'T'))
  return Number.isNaN(t.getTime()) ? null : t
}

const target = computed(() => parseTime(props.exam?.startAt))

const started = computed(() => {
  if (!target.value) return false
  return target.value.getTime() <= now.value
})

const parts = computed(() => {
  if (!target.value) return { d: '--', h: '--', m: '--', s: '--' }
  const diff = Math.max(0, Math.floor((target.value.getTime() - now.value) / 1000))
  const pad = (x) => String(x).padStart(2, '0')
  return {
    d: String(Math.floor(diff / 86400)),
    h: pad(Math.floor((diff % 86400) / 3600)),
    m: pad(Math.floor((diff % 3600) / 60)),
    s: pad(diff % 60)
  }
})

const whenText = computed(() => {
  if (!target.value) return '时间待定'
  const p = (x) => String(x).padStart(2, '0')
  return `${target.value.getMonth() + 1}月${target.value.getDate()}日 ${p(target.value.getHours())}:${p(target.value.getMinutes())}`
})

function goExam() {
  // 「报名」的真实落点是考试列表（学生场次由教师分配，列表页可见可进入的考试）
  router.push('/student/exam')
}

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.card {
  background: var(--zk-portal-card);
  border: 1px solid var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-lg);
  box-shadow: var(--zk-portal-shadow-s);
}
.side-card {
  padding: 18px 20px;
}

.contest {
  background: linear-gradient(135deg, #123c8c, #3e6fe0);
  border: none;
  color: #fff;
}

.sec-title {
  font-family: var(--zk-font-display);
  font-size: 17px;
  font-weight: 900;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  margin: 0;
}
.sec-title .bar {
  width: 5px;
  height: 17px;
  border-radius: 3px;
  background: #8fb6ff;
}

.c-when {
  font-size: 12.5px;
  opacity: 0.85;
  margin: 6px 0 0;
}

.c-clock {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}
.cu {
  background: rgba(0, 0, 0, 0.28);
  border-radius: 10px;
  padding: 7px 0;
  text-align: center;
  flex: 1;
  backdrop-filter: blur(2px);
}
.cu b {
  display: block;
  font-family: var(--zk-font-display);
  font-size: 20px;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.cu span {
  font-size: 10px;
  opacity: 0.7;
  letter-spacing: 1.5px;
}
.cu-wide b {
  font-size: 16px;
}

.btn-enter {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 11px 20px;
  border-radius: var(--zk-portal-radius-md);
  font-size: 14.5px;
  font-weight: 700;
  background: #fff;
  color: #123c8c;
  border: none;
  cursor: pointer;
  transition: all 0.18s ease;
}
.btn-enter:hover {
  transform: translateY(-1px);
  box-shadow: var(--zk-portal-shadow-m);
}
</style>
