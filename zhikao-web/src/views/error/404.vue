<template>
  <div class="err-page">
    <div class="err-page__card">
      <EmptyState title="页面不存在" description="地址可能输错了，或这个页面已被移除。">
        <div class="err-page__actions">
          <el-button type="primary" @click="goHome">返回首页</el-button>
          <el-button plain @click="router.back()">回到上一页</el-button>
        </div>
      </EmptyState>
    </div>
  </div>
</template>

<script setup>
/** 404 页（四期新增）：兜底路由从「无痕弹回首页」改为明示未找到 */
import { useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

function goHome() {
  router.replace('/home')
}
</script>

<style scoped>
.err-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zk-bg-page);
  padding: var(--zk-space-5);
}

.err-page__card {
  width: min(480px, 100%);
  background: var(--zk-bg-card);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-lg);
  padding: var(--zk-space-6) var(--zk-space-5);
}

.err-page__actions {
  display: flex;
  gap: var(--zk-space-2);
  justify-content: center;
}
</style>
