<template>
  <div v-if="items.length" class="card side-card">
    <div class="sec-head">
      <h3 class="sec-title"><span class="bar" />公告</h3>
    </div>
    <div class="notice-list">
      <div v-for="item in items" :key="item.id" class="notice-item">
        <span class="n-tag" :class="tagClass(item.tag)">{{ item.tag || '通知' }}</span>
        <div class="n-body">
          <p>{{ item.title }}</p>
          <small v-if="item.timeText">{{ item.timeText }}</small>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 公告卡（右栏）。
 * 首选 /portal/home notices；失败时由 StudentHome 用既有
 * GET /exam/{examId}/announcement（最近一场有公告的考试）降级映射。
 */
defineProps({
  items: {
    type: Array,
    default: () => []
    // [{ id, title, tag, timeText }]
  }
})

function tagClass(tag) {
  if (tag === '置顶' || tag === '紧急') return 'n-tag-hot'
  if (tag === '新卷' || tag === 'AI') return 'n-tag-new'
  return 'n-tag-plain'
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
  background: var(--zk-primary);
}

.notice-item {
  display: flex;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1.5px dashed var(--zk-portal-line);
  font-size: 13.5px;
}
.notice-item:last-child {
  border: none;
}
.n-tag {
  flex: 0 0 auto;
  font-size: 10.5px;
  font-weight: 900;
  padding: 2px 8px;
  border-radius: 6px;
  height: fit-content;
  margin-top: 2px;
}
.n-tag-hot {
  background: var(--zk-danger);
  color: #fff;
}
.n-tag-new {
  background: #f0ebfd;
  color: #7c5ce6;
}
.n-tag-plain {
  background: var(--zk-portal-bg-deep);
  color: var(--zk-portal-ink-soft);
}

.n-body p {
  color: var(--zk-portal-ink-soft);
  line-height: 1.5;
  margin: 0;
}
.n-body small {
  display: block;
  color: var(--zk-portal-muted);
  font-size: 11.5px;
  margin-top: 2px;
}
</style>
