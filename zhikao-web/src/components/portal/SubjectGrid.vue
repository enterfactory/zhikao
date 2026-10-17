<template>
  <div class="subject-grid">
    <div
      v-for="(item, i) in items"
      :key="item.subjectId ?? `subj-${i}`"
      class="subj"
      :style="{ ...subjectVars(item.subjectName), animationDelay: `${i * 50}ms` }"
      role="button"
      tabindex="0"
      @click="open(item)"
      @keydown.enter="open(item)"
    >
      <div class="s-name"><i />{{ item.subjectName }}</div>
      <div class="s-count">
        <template v-if="item.totalQuestions !== null && item.totalQuestions !== undefined">
          题库 <b>{{ formatCount(item.totalQuestions) }}</b> 题 · 已刷 {{ pctOf(item) }}%
        </template>
        <template v-else>题库统计准备中</template>
      </div>
      <template v-if="hasProgress(item)">
        <div class="s-bar"><i :style="{ width: pctOf(item) + '%' }" /></div>
        <span class="s-pct">{{ pctOf(item) }}%</span>
      </template>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { subjectVars } from '@/utils/subjectColors'

/**
 * 科目题库网格（五期首页）。
 * 首选 /portal/home 的 subjectStats；该接口缺失时 StudentHome 用
 * GET /subject/list 的科目名生成降级数据（totalQuestions = null，只展示色块不展示假进度）。
 */
defineProps({
  items: { type: Array, default: () => [] }
})

const router = useRouter()

function pctOf(item) {
  const total = Number(item.totalQuestions || 0)
  if (total <= 0) return 0
  return Math.min(100, Math.round((Number(item.doneCount || 0) / total) * 100))
}

function hasProgress(item) {
  const total = Number(item.totalQuestions || 0)
  return total > 0
}

function formatCount(n) {
  return Number(n).toLocaleString('zh-CN')
}

function open(item) {
  // 学科题库页尚未独立成路由，统一落到「我的考试」的刷题入口
  router.push({ path: '/student/exam', query: item.subjectId ? { subjectId: item.subjectId } : {} })
}
</script>

<style scoped>
.subject-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.subj {
  border: 1px solid var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-md);
  padding: 16px 18px;
  background: var(--zk-portal-card);
  position: relative;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.18s ease;
  /* fill 用 backwards 而非 both：动画结束后释放 transform，hover 上浮不被终帧锁死 */
  animation: rise 0.4s ease backwards;
}
.subj:hover {
  transform: translateY(-2px);
  box-shadow: var(--zk-portal-shadow-m);
}
/* 顶部 4px 学科色带：与考试卡色带同语言 */
.subj::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 4px;
  background: var(--sc);
}

.s-name {
  font-family: var(--zk-font-display);
  font-weight: 900;
  font-size: 16.5px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--zk-portal-ink);
}
.s-name i {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  background: var(--sc);
}

.s-count {
  font-size: 12px;
  color: var(--zk-portal-muted);
  margin: 3px 0 10px;
}
.s-count b {
  color: var(--zk-portal-ink);
  font-size: 13.5px;
  font-family: var(--zk-font-display);
}

.s-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--zk-portal-bg-deep);
  overflow: hidden;
}
.s-bar i {
  display: block;
  height: 100%;
  background: var(--sc);
  border-radius: 3px;
  transform-origin: left;
  animation: grow 0.9s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}

.s-pct {
  position: absolute;
  right: 16px;
  top: 15px;
  font-size: 12px;
  font-weight: 900;
  color: var(--sc);
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
@keyframes grow {
  from {
    transform: scaleX(0);
  }
}

@media (max-width: 860px) {
  .subject-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
