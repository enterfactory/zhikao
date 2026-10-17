<template>
  <div class="study-app">
    <template v-if="!route.meta.bare">
      <header class="study-header">
        <div class="study-header-inner">
          <router-link to="/home" class="product-brand">
            <span class="brand-symbol">Z</span>
            <strong>智考</strong>
            <span class="brand-divider"></span>
            <span class="brand-context">学习空间</span>
          </router-link>
          <nav class="study-nav" aria-label="学习导航">
            <router-link
              v-for="n in nav"
              :key="n.path"
              :to="n.path"
              :class="{ active: route.path === n.path }"
            >
              {{ n.label }}
            </router-link>
          </nav>
          <div class="account-tools">
            <NotificationBell />
            <el-dropdown trigger="click">
              <button class="account-button">
                <span class="user-avatar">{{ name.slice(0, 1) }}</span>
                <span class="account-name">{{ name }}</span>
                <span>⌄</span>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/profile')">个人设置</el-dropdown-item>
                  <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </header>
    </template>
    <main :class="route.meta.bare ? 'exam-fullscreen' : 'study-content'"><router-view /></main>
    <footer v-if="!route.meta.bare" class="study-footer">
      <span>智考 · 让每一次学习有据可循</span>
      <span>学习空间</span>
    </footer>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import NotificationBell from '@/components/NotificationBell.vue'
const route = useRoute(),
  router = useRouter(),
  user = useUserStore()
const name = computed(() => user.userInfo?.realName || user.userInfo?.username || '同学')
const nav = [
  { path: '/home', label: '我的学习' },
  { path: '/student/exam', label: '考试安排' },
  { path: '/student/wrong', label: '练习与错题' },
  { path: '/student/grade', label: '学习记录' },
]
async function logout() {
  await user.logout()
  router.replace('/login')
}
</script>
