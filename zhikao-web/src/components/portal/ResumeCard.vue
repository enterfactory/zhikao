<template>
  <div v-if="resume" class="resume" :style="vars">
    <span class="r-ic">
      <el-icon :size="24"><CaretRight /></el-icon>
    </span>
    <div class="r-main">
      <b>继续上次练习 · {{ resume.subjectName || '未知科目' }}「{{ resume.setName || '套卷' }}」 {{ done }}/{{ total }} 题</b>
      <span>{{ lastAtText }}<template v-if="accuracy !== null"> · 正确率 {{ accuracy }}%</template></span>
      <div class="r-prog"><i :style="{ width: pct + '%' }" /></div>
    </div>
    <span v-if="accuracy !== null" class="r-pct">{{ accuracy }}%</span>
    <button class="btn-continue" type="button" @click="goExam">继续练习</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { CaretRight } from '@element-plus/icons-vue'
import { subjectVars } from '@/utils/subjectColors'

/**
 * 「继续练习」卡：左竖条 / 图标底 / 进度条全部走 resume 科目的学科色，
 * 与题库网格、考试列表同色贯穿。无数据时由父级 v-if 整体隐藏。
 */
const props = defineProps({
  resume: { type: Object, default: null }
})

const router = useRouter()

const vars = computed(() => subjectVars(props.resume?.subjectName))
const done = computed(() => Number(props.resume?.done || 0))
const total = computed(() => Number(props.resume?.total || 0))
const pct = computed(() => (total.value > 0 ? Math.min(100, Math.round((done.value / total.value) * 100)) : 0))

/** accuracy 兼容 0-1 与 0-100 两种口径 */
const accuracy = computed(() => {
  const a = props.resume?.accuracy
  if (a === null || a === undefined || a === '') return null
  const n = Number(a)
  if (Number.isNaN(n)) return null
  return Math.round(n > 1 ? n : n * 100)
})

const lastAtText = computed(() => {
  const t = String(props.resume?.lastAt || '')
  if (!t) return '上次练习时间未知'
  const short = t.slice(5, 16).replace('T', ' ')
  return `上次练习：${short}`
})

function goExam() {
  // 答题页需要 examId 上下文，草稿续答统一从考试列表进入（那里能区分「继续答题」场次）
  router.push('/student/exam')
}
</script>

<style scoped>
.resume {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 16px 22px;
  border-left: 6px solid var(--sc);
  background: var(--zk-portal-card);
  border-top: 1px solid var(--zk-portal-line);
  border-right: 1px solid var(--zk-portal-line);
  border-bottom: 1px solid var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-lg);
  box-shadow: var(--zk-portal-shadow-s);
}

.r-ic {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: var(--sc-soft);
  color: var(--sc);
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.r-main {
  flex: 1;
  min-width: 0;
}
.r-main b {
  font-size: 15px;
  display: block;
  color: var(--zk-portal-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.r-main span {
  font-size: 12.5px;
  color: var(--zk-portal-muted);
}

.r-prog {
  height: 7px;
  border-radius: 4px;
  background: var(--zk-portal-bg-deep);
  margin-top: 7px;
  overflow: hidden;
  max-width: 340px;
}
.r-prog i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--sc);
  transform-origin: left;
  animation: grow 1s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}
@keyframes grow {
  from {
    transform: scaleX(0);
  }
}

.r-pct {
  font-family: var(--zk-font-display);
  font-weight: 900;
  font-size: 20px;
  color: var(--sc);
  flex: 0 0 auto;
}

.btn-continue {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 11px 20px;
  border-radius: var(--zk-portal-radius-md);
  font-size: 14.5px;
  font-weight: 700;
  background: var(--zk-primary);
  color: #fff;
  border: none;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(14, 159, 110, 0.3);
  transition: all 0.18s ease;
  flex: 0 0 auto;
}
.btn-continue:hover {
  background: var(--zk-primary-active);
  transform: translateY(-1px);
}

@media (max-width: 860px) {
  .resume {
    gap: 12px;
    padding: 14px 16px;
  }
  .r-pct {
    display: none;
  }
}
</style>
