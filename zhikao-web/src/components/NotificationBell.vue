/** * 通知铃铛。 * * 做成挂着小红点的 Popover 而不是直接跳页面，是因为绝大多数时候 *
用户只想看「刚刚那条是什么」，而不是进去维护一个列表。 * 需要翻历史时才点底部「查看全部」进列表页。
* * 轮询只拉一个未读数字，不拉列表：列表有标题、内容、时间，成本比一个 int 高一个量级， *
而红点只需要知道「有没有新的」。 */
<template>
  <el-popover placement="bottom-end" :width="340" trigger="click" @show="onOpen">
    <template #reference>
      <span title="通知">
        <el-button text circle aria-label="通知">
          <el-badge :value="count" :hidden="count === 0" :max="99">
            <el-icon :size="17"><Bell /></el-icon>
          </el-badge>
        </el-button>
      </span>
    </template>

    <div class="nb__head">
      <span class="nb__title">通知</span>
      <el-link v-if="count > 0" type="primary" underline="never" @click="markAll">全部已读</el-link>
    </div>

    <div v-loading="loading" class="nb__body">
      <template v-if="list.length">
        <div
          v-for="item in list"
          :key="item.id"
          class="nb__item"
          :class="{ 'is-unread': item.readFlag === 0 }"
          @click="open(item)"
        >
          <div class="nb__item-main">
            <div class="nb__item-title">{{ item.title }}</div>
            <div v-if="item.content" class="nb__item-content">{{ item.content }}</div>
            <div class="nb__item-time">{{ item.createTime }}</div>
          </div>
          <span v-if="item.readFlag === 0" class="nb__dot" />
        </div>
      </template>
      <el-empty v-else description="暂无通知" :image-size="60" />
    </div>

    <div class="nb__foot">
      <el-link underline="never" @click="router.push('/notification')">查看全部</el-link>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  notificationPage,
  readAllNotification,
  readNotification,
  unreadCount,
} from '@/api/notification'

/** 轮询间隔：30 秒一次足以让「教师刚发布考试」被及时感知，又不至于刷屏请求日志 */
const POLL_INTERVAL = 30_000

const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const count = ref(0)
const loading = ref(false)
let timer = null

async function loadCount() {
  try {
    const data = await unreadCount()
    count.value = data || 0
  } catch {
    // 未读数是最不重要的请求：失败时保持上一次的值，不用打扰用户
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await notificationPage({ pageNum: 1, pageSize: 8 })
    list.value = data.records || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onOpen() {
  await Promise.all([loadList(), loadCount()])
}

async function open(item) {
  if (item.readFlag === 0) {
    try {
      await readNotification(item.id)
      item.readFlag = 1
      count.value = Math.max(0, count.value - 1)
    } catch {
      // 标记失败不该挡住查看详情
    }
  }
  // 只认 'exam' 这一种业务类型去跳转；未知类型不做任何跳转动作，
  // 避免「加了一种通知类型但忘了配路由」时点一下直接掉到 404。
  if (item.bizType === 'exam' && item.bizId) {
    router.push(userStore.role === 'student' ? '/student/exam' : '/teacher/exam')
  }
}

async function markAll() {
  try {
    await readAllNotification()
    list.value.forEach((it) => (it.readFlag = 1))
    count.value = 0
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

/**  polling 的清理放在卸载钩子里：忘记清理的定时器会在离开布局后继续打接口 */
onMounted(() => {
  loadCount()
  timer = window.setInterval(loadCount, POLL_INTERVAL)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
/* 四期回填：--el-* 统一换成 --zk-* token，px 对齐间距 / 字号刻度；不改功能 */
.nb__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--zk-space-2);
  border-bottom: 1px solid var(--zk-border-light);
}

.nb__title {
  font-weight: 600;
}

.nb__body {
  max-height: 320px;
  overflow: auto;
}

.nb__item {
  display: flex;
  align-items: flex-start;
  gap: var(--zk-space-2);
  padding: var(--zk-space-2) var(--zk-space-1);
  border-bottom: 1px solid var(--zk-border-light);
  cursor: pointer;
}

.nb__item:hover {
  background: var(--zk-bg-hover);
}

.nb__item-main {
  flex: 1;
  min-width: 0;
}

.nb__item-title {
  font-size: var(--zk-font-sm);
  line-height: 1.5;
  color: var(--zk-text-primary);
}

.nb__item.is-unread .nb__item-title {
  font-weight: 600;
}

.nb__item-content {
  margin-top: var(--zk-space-1);
  font-size: var(--zk-font-xs);
  line-height: 1.6;
  color: var(--zk-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.nb__item-time {
  margin-top: var(--zk-space-1);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.nb__dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--zk-danger);
}

.nb__foot {
  padding-top: var(--zk-space-2);
  text-align: center;
  border-top: 1px solid var(--zk-border-light);
}
</style>
