<template>
  <div v-if="items.length" class="card side-card">
    <div class="sec-head">
      <h3 class="sec-title"><span class="bar" />学习榜 · 本周积分</h3>
    </div>
    <div class="rank-list">
      <div
        v-for="row in items"
        :key="row.userId ?? row.rank"
        class="rank-item"
        :class="{ 'rank-me': isMe(row) }"
      >
        <span class="rank-no" :class="medalClass(row.rank)">{{ row.rank }}</span>
        <span class="rank-av" :style="avatarStyle(row)">{{ (row.name || '?').slice(0, 1) }}</span>
        <span class="rname">{{ isMe(row) ? '我 · ' : '' }}{{ row.name }}</span>
        <span class="rpts">{{ formatScore(row.score) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useUserStore } from '@/store/user'
import { subjectColor } from '@/utils/subjectColors'

/**
 * 学习排行榜卡（右栏）。无 leaderboard 数据时由父级隐藏。
 * 前三名的奖牌渐变、本人行的高亮分隔线均对齐视觉稿。
 */
const props = defineProps({
  items: {
    type: Array,
    default: () => []
    // [{ userId, name, score, rank }]
  }
})

const userStore = useUserStore()

function isMe(row) {
  const myId = userStore.userInfo?.id
  return myId != null && row.userId != null && String(row.userId) === String(myId)
}

function medalClass(rank) {
  if (rank === 1) return 'medal-1'
  if (rank === 2) return 'medal-2'
  if (rank === 3) return 'medal-3'
  return ''
}

/** 头像色：姓名 → 学科色同一哈希，保证同名同人稳定着色 */
function avatarStyle(row) {
  const { main, soft } = subjectColor(row.name || String(row.userId || ''))
  return { background: soft, color: main }
}

function formatScore(score) {
  const n = Number(score || 0)
  return n.toLocaleString('zh-CN')
}
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

.sec-head {
  margin-bottom: 6px;
}
.sec-title {
  font-family: var(--zk-font-display);
  font-size: 17px;
  font-weight: 900;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--zk-portal-ink);
  margin: 0;
}
.sec-title .bar {
  width: 5px;
  height: 17px;
  border-radius: 3px;
  background: var(--zk-warning);
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 8px 0;
  font-size: 13.5px;
  color: var(--zk-portal-ink);
}

.rank-no {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-weight: 900;
  font-size: 12.5px;
  background: var(--zk-portal-bg-deep);
  color: var(--zk-portal-ink-soft);
  flex: 0 0 auto;
}
.medal-1 {
  background: linear-gradient(135deg, #ffd479, #f5a623);
  color: #7a4d00;
}
.medal-2 {
  background: linear-gradient(135deg, #e8e8ee, #b9bcc9);
  color: #4c4f5c;
}
.medal-3 {
  background: linear-gradient(135deg, #f0c9a8, #c98b55);
  color: #6b4014;
}

.rank-av {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 900;
  font-size: 12.5px;
  flex: 0 0 auto;
}

.rname {
  flex: 1;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rpts {
  font-weight: 900;
  font-family: var(--zk-font-display);
  color: var(--zk-success);
  font-variant-numeric: tabular-nums;
}

/* 本人行：虚线分隔强调「我在哪」 */
.rank-me {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1.5px dashed var(--zk-portal-line);
}
</style>
