<template>
  <AppPage title="组织概览" description="查看组织成员与教学运行情况。">
    <template #actions>
      <el-button :loading="loading" @click="load">刷新概览</el-button>
      <el-button
        v-if="can('system:user:manage')"
        type="primary"
        @click="router.push('/admin/user')"
      >
        管理成员
      </el-button>
    </template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading">
      <div class="console-metrics">
        <div v-for="m in metrics" :key="m.label" class="console-metric">
          <span>{{ m.label }}</span>
          <strong>{{ m.value ?? '—' }}</strong>
          <small>{{ m.hint }}</small>
        </div>
      </div>
      <div class="console-two-columns">
        <section class="console-panel">
          <header class="console-panel-head">待处理事项</header>
          <div class="console-panel-body">
            <el-empty v-if="!todos.length" description="暂无待处理事项" :image-size="70" />
            <router-link v-for="t in todos" :key="t.title" :to="t.path" class="overview-todo">
              <span class="todo-marker" />
              <div>
                <strong>{{ t.title }}</strong>
                <p>{{ t.description }}</p>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
          </div>
        </section>
        <section class="console-panel">
          <header class="console-panel-head">组织管理</header>
          <div class="console-panel-body">
            <router-link v-for="e in entries" :key="e.path" :to="e.path" class="overview-todo">
              <el-icon><component :is="e.icon" /></el-icon>
              <div>
                <strong>{{ e.title }}</strong>
                <p>{{ e.note }}</p>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
          </div>
        </section>
      </div>
      <section class="console-panel overview-teaching">
        <header class="console-panel-head">教学运行</header>
        <div class="overview-teaching-grid">
          <router-link v-for="m in teaching" :key="m.key" :to="m.path">
            <span>{{ m.label }}</span>
            <strong>
              {{ m.value ?? '—' }}
              <small>{{ m.unit }}</small>
            </strong>
            <p>{{ m.hint }}</p>
          </router-link>
        </div>
      </section>
    </div>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import AppPage from '@/components/AppPage.vue'
import { dashboardOverview } from '@/api/dashboard'
import { pageUsers, listClasses, listSubjects } from '@/api/system'
const user = useUserStore(),
  router = useRouter(),
  loading = ref(false),
  error = ref(''),
  overview = ref({}),
  counts = ref({})
const can = (p) => user.permissions.includes(p)
const metrics = computed(() => [
  { label: '组织成员', value: counts.value.users, hint: '已登记的全部成员' },
  { label: '教师', value: counts.value.teachers, hint: '当前组织教师账号' },
  { label: '班级', value: counts.value.classes, hint: '已建立的教学班级' },
  { label: '科目', value: counts.value.subjects, hint: '已配置的教学科目' },
])
const entries = computed(() =>
  [
    {
      title: '用户管理',
      path: '/admin/user',
      permission: 'system:user:manage',
      icon: 'User',
      note: '维护成员与角色分配',
    },
    {
      title: '班级管理',
      path: '/admin/class',
      permission: 'system:class:manage',
      icon: 'School',
      note: '维护年级与班级目录',
    },
    {
      title: '角色与权限',
      path: '/admin/role',
      permission: 'system:role:view',
      icon: 'Key',
      note: '查看角色可用功能',
    },
  ].filter((e) => can(e.permission)),
)
const pathPermissions = {
  '/teacher/marking': 'marking:do',
  '/teacher/exam': 'exam:manage',
  '/teacher/question': 'question:view',
}
const todos = computed(() =>
  (overview.value.todos || []).filter((t) => t.count > 0 && can(pathPermissions[t.path])),
)
const teaching = computed(() =>
  (overview.value.metrics || [])
    .map((m) => ({
      ...m,
      path:
        m.key === 'question'
          ? '/teacher/question'
          : m.key === 'pending_mark'
            ? '/teacher/marking'
            : '/teacher/exam',
    }))
    .filter((m) => can(pathPermissions[m.path])),
)
async function load() {
  loading.value = true
  error.value = ''
  const jobs = [
    ['overview', dashboardOverview],
    ...(can('system:user:manage')
      ? [
          ['users', () => pageUsers({ pageSize: 1 })],
          ['teachers', () => pageUsers({ pageSize: 1, userType: 2 })],
        ]
      : []),
    ...(can('system:class:manage') ? [['classes', listClasses]] : []),
    ...(can('system:subject:manage') ? [['subjects', listSubjects]] : []),
  ]
  const results = await Promise.allSettled(jobs.map(([, f]) => f()))
  results.forEach((r, i) => {
    const k = jobs[i][0]
    if (r.status === 'fulfilled') {
      if (k === 'overview') overview.value = r.value
      else counts.value[k] = Array.isArray(r.value) ? r.value.length : r.value.total
    } else {
      if (k !== 'overview') counts.value[k] = undefined
      error.value = '部分数据加载失败，请刷新重试。'
    }
  })
  loading.value = false
}
onMounted(load)
</script>
<style scoped>
.overview-todo {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #e6e9ef;
  color: #425168;
}
.overview-todo:last-child {
  border: 0;
}
.overview-todo > div {
  flex: 1;
  min-width: 0;
}
.overview-todo strong {
  font-size: 13px;
  font-weight: 500;
}
.overview-todo p {
  color: #8490a1;
  font-size: 12px;
  margin-top: 5px;
}
.todo-marker {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #bf8835;
}
.overview-teaching {
  margin-top: 20px;
}
.overview-teaching-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.overview-teaching-grid a {
  padding: 24px;
  color: #68768b;
  border-right: 1px solid #e6e9ef;
}
.overview-teaching-grid strong {
  display: block;
  font-size: 25px;
  color: #27364c;
  margin: 8px 0;
}
.overview-teaching-grid small,
.overview-teaching-grid p {
  font-size: 11px;
  font-weight: 400;
}
@media (max-width: 900px) {
  .overview-teaching-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
