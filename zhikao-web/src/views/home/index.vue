<template>
  <StudentHome v-if="user.role === 'student'" />
  <AdminOverview v-else-if="user.role === 'admin'" />
  <Dashboard v-else />
</template>
<script setup>
import { useUserStore } from '@/store/user'
import StudentHome from '@/components/portal/StudentHome.vue'
import AdminOverview from '@/views/admin/Overview.vue'
import Dashboard from '@/views/workflow/Dashboard.vue'
const user = useUserStore()
</script>
