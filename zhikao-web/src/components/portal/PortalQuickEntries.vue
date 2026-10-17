<template>
  <div class="quick-row">
    <button
      v-for="(entry, i) in entries"
      :key="entry.key"
      class="quick"
      type="button"
      :style="{ animationDelay: `${i * 40}ms` }"
      @click="go(entry)"
    >
      <span class="qi" :style="tileStyle(entry)">
        <el-icon :size="22"><component :is="entry.icon" /></el-icon>
      </span>
      <span class="qlabel">{{ entry.label }}</span>
      <span v-if="badgeOf(entry)" class="badge" :class="`badge-${entry.badgeTone}`">
        {{ badgeOf(entry) }}
      </span>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Calendar,
  ChatDotRound,
  Collection,
  Finished,
  TrendCharts,
  WarningFilled
} from '@element-plus/icons-vue'
import { subjectColor } from '@/utils/subjectColors'

/**
 * 门户 6 快捷入口（五期首页）。
 *
 * 路由均为现网真实路径（/student/exam、/student/grade、/student/wrong、/ai/chat）；
 * 「历年真题」暂无独立页，按搜索条同一约定 toast 占位，不造假路由。
 * 配色复用学科色 + 语义 Token：入口色块与全站「同色贯穿」同一出处。
 */
const props = defineProps({
  /** 错题本待复习数（/learn/wrong/summary 的 total；null/失败不显示角标） */
  wrongCount: { type: Number, default: null }
})

const router = useRouter()

const cn = subjectColor('语文')
const en = subjectColor('英语')
const phy = subjectColor('物理')

const entries = computed(() => [
  { key: 'practice', label: '刷题练习', icon: Finished, main: subjectColor('数学').main, soft: subjectColor('数学').soft, to: '/student/exam' },
  { key: 'mock', label: '模拟考试', icon: Calendar, main: en.main, soft: en.soft, to: '/student/exam', badge: 'HOT', badgeTone: 'danger' },
  { key: 'history', label: '历年真题', icon: Collection, main: cn.main, soft: cn.soft, hint: '历年真题按年份 / 试卷筛选能力建设中' },
  { key: 'wrong', label: '错题本', icon: WarningFilled, main: 'var(--zk-warning)', soft: 'var(--zk-warning-light)', to: '/student/wrong', badgeCount: true, badgeTone: 'danger' },
  { key: 'grade', label: '成绩报告', icon: TrendCharts, main: 'var(--zk-success)', soft: 'var(--zk-success-light)', to: '/student/grade' },
  { key: 'ai', label: '智能答疑', icon: ChatDotRound, main: phy.main, soft: phy.soft, to: '/ai/chat', badge: 'AI', badgeTone: 'info' }
])

function tileStyle(entry) {
  return { background: entry.soft, color: entry.main }
}

function badgeOf(entry) {
  if (entry.badgeCount) {
    const n = props.wrongCount
    return n && n > 0 ? String(n) : ''
  }
  return entry.badge || ''
}

function go(entry) {
  if (entry.to) {
    router.push(entry.to)
    return
  }
  ElMessage.info(entry.hint || '能力建设中，敬请期待')
}
</script>

<style scoped>
.quick-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.quick {
  background: var(--zk-portal-card);
  border: 1px solid var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-md);
  padding: 16px 8px 13px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--zk-portal-ink);
  position: relative;
  cursor: pointer;
  transition: all 0.18s ease;
  animation: rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) backwards;
}
.quick:hover {
  transform: translateY(-3px);
  box-shadow: var(--zk-portal-shadow-m);
  border-color: transparent;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.quick .qi {
  width: 46px;
  height: 46px;
  border-radius: 15px;
  display: grid;
  place-items: center;
}

.quick .badge {
  position: absolute;
  top: 8px;
  right: 12px;
  font-size: 9.5px;
  font-weight: 900;
  color: #fff;
  padding: 1.5px 7px;
  border-radius: 999px;
  letter-spacing: 0.5px;
}
.badge-danger {
  background: var(--zk-danger);
}
.badge-info {
  background: var(--zk-info);
}

/* ≤860px：3 列（视觉稿约定） */
@media (max-width: 860px) {
  .quick-row {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
