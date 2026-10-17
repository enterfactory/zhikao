<template>
  <div class="rail">
    <div
      v-for="(paper, i) in items"
      :key="paper.id ?? `paper-${i}`"
      class="paper-card"
      :style="{ ...subjectVars(paper.subjectName), animationDelay: `${i * 50}ms` }"
      role="button"
      tabindex="0"
      @click="open()"
      @keydown.enter="open()"
    >
      <div class="pc-cover">
        <span class="tag">{{ paper.subjectName || '综合' }} · {{ paper.tag || '模考' }}</span>
        <h4>{{ paper.title }}</h4>
      </div>
      <div class="pc-body">
        <span class="pc-meta">{{ paper.meta }}</span>
        <span class="pc-go">去挑战</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { subjectVars } from '@/utils/subjectColors'

/**
 * 热门模考横滑轨道。
 * /portal/home 契约暂无模考字段，数据由 StudentHome 从 GET /exam/mine
 * 的未开始 / 进行中场次映射而来（真实存在、可点击进入的考试，而非编造的运营位）。
 */
defineProps({
  items: { type: Array, default: () => [] }
})

const router = useRouter()

function open() {
  router.push('/student/exam')
}
</script>

<style scoped>
.rail {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding: 4px 2px 14px;
  scroll-snap-type: x mandatory;
  scrollbar-width: thin;
}
.rail::-webkit-scrollbar {
  height: 6px;
}
.rail::-webkit-scrollbar-thumb {
  background: var(--zk-portal-line);
  border-radius: 3px;
}

.paper-card {
  flex: 0 0 268px;
  scroll-snap-align: start;
  border: 1px solid var(--zk-portal-line);
  border-radius: var(--zk-portal-radius-md);
  overflow: hidden;
  background: var(--zk-portal-card);
  transition: all 0.18s ease;
  cursor: pointer;
  animation: rise 0.45s cubic-bezier(0.2, 0.7, 0.3, 1) backwards;
}
.paper-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--zk-portal-shadow-m);
}

.pc-cover {
  height: 88px;
  position: relative;
  display: flex;
  align-items: flex-end;
  padding: 12px 16px;
  /* 学科色底 + 压暗渐变，保证白字对比度（同视觉稿双段渐变的做法） */
  background-color: var(--sc);
  background-image: linear-gradient(130deg, rgba(0, 0, 0, 0.38), rgba(0, 0, 0, 0.08));
}
.pc-cover .tag {
  position: absolute;
  top: 10px;
  left: 12px;
  font-size: 10.5px;
  font-weight: 900;
  color: #fff;
  background: rgba(0, 0, 0, 0.28);
  padding: 2.5px 9px;
  border-radius: 999px;
  letter-spacing: 1px;
  backdrop-filter: blur(3px);
}
.pc-cover h4 {
  font-family: var(--zk-font-display);
  color: #fff;
  font-size: 16.5px;
  font-weight: 900;
  text-shadow: 0 1px 8px rgba(0, 0, 0, 0.25);
  position: relative;
  z-index: 1;
  margin: 0;
  line-height: 1.3;
}

.pc-body {
  padding: 12px 16px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pc-meta {
  font-size: 12px;
  color: var(--zk-portal-muted);
}
.pc-go {
  font-size: 12.5px;
  font-weight: 900;
  color: var(--zk-primary);
  background: var(--zk-primary-light);
  padding: 6px 13px;
  border-radius: 999px;
  white-space: nowrap;
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
</style>
