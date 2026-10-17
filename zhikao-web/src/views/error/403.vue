<template>
  <div class="err-page">
    <div class="err-page__card">
      <EmptyState
        title="无访问权限"
        :description="`你想要访问的页面需要「${needPermText}」权限，当前账号不具备。`"
      >
        <div class="err-page__actions">
          <el-button type="primary" @click="goHome">返回我的首页</el-button>
          <el-button plain @click="router.back()">回到上一页</el-button>
        </div>
      </EmptyState>
      <p v-if="extraRoles.length" class="err-page__hint">
        需要此功能？可在个人设置中查看当前角色（{{ roleNames }}），或联系管理员为你分配相应角色。
      </p>
    </div>
  </div>
</template>

<script setup>
/**
 * 403 页（四期新增）。
 * 三期之前越权访问被守卫静默弹回首页——用户视角是「点了没反应」，
 * 猜测系统坏了。把「你没有权限、需要什么权限、能做什么」一次说清，
 * 是权限系统可信度的最低成本表达。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 守卫里拿不到「缺哪个权限」，从路由表回查同一份 meta.perms 口径
const needPermText = computed(() => {
  const fromQuery = route.query.perms
  const perms = fromQuery ? String(fromQuery).split(',') : []
  return perms.length ? perms.join(' / ') : '更高的角色权限'
})

const extraRoles = computed(() => userStore.roleCodes || [])
const roleNames = computed(() => extraRoles.value.join('、') || '未分配角色')

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

.err-page__hint {
  margin-top: var(--zk-space-2);
  text-align: center;
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
  line-height: 1.6;
}
</style>
