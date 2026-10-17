<template>
  <AppPage title="通知中心" description="考试安排、处理结果与账号消息。">
    <template #actions>
      <el-button :disabled="!total || markingAll" :loading="markingAll" @click="markAll">
        全部标记已读
      </el-button>
    </template>
    <section class="console-panel">
      <div class="console-workspace-tabs">
        <button
          :class="{ active: filter === 0 }"
          @click="
            () => {
              filter = 0
              reload()
            }
          "
        >
          全部通知
        </button>
        <button
          :class="{ active: filter === 1 }"
          @click="
            () => {
              filter = 1
              reload()
            }
          "
        >
          未读
        </button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div v-loading="loading">
        <el-empty v-if="!rows.length && !loading && !error" description="暂时没有通知" />
        <article
          v-for="n in rows"
          :key="n.id"
          class="notification-item"
          :class="{ unread: n.readFlag === 0 }"
        >
          <div class="notification-icon">
            <el-icon><Bell /></el-icon>
          </div>
          <div class="notification-main">
            <div class="console-summary-row">
              <button class="notification-title" @click="openNotification(n)">
                {{ n.title }}
                <span v-if="n.readFlag === 0" class="notification-dot" />
              </button>
              <time>{{ n.createTime }}</time>
            </div>
            <p>{{ n.content }}</p>
            <div class="notification-bottom">
              <el-tag size="small" type="info">{{ n.typeLabel || '系统通知' }}</el-tag>
              <el-button v-if="n.readFlag === 0" text type="primary" @click="readOne(n)">
                标记已读
              </el-button>
              <el-button text @click="openNotification(n)">查看详情</el-button>
            </div>
          </div>
        </article>
      </div>
      <div class="console-panel-body">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="changePage"
        />
      </div>
    </section>
    <ZkDrawer v-model="detailVisible" title="通知详情">
      <template v-if="detail">
        <h3>{{ detail.title }}</h3>
        <p class="console-cell-note">{{ detail.typeLabel }} · {{ detail.createTime }}</p>
        <p class="notification-detail">{{ detail.content }}</p>
      </template>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import AppPage from '@/components/AppPage.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { notificationPage, readAllNotification, readNotification } from '@/api/notification'

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filter = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 响应拦截器已经把 R 结构拆到 data 层，这里再解一层 { data } 会得到 undefined，
    // 结果是通知列表恒为空且静默失败——列表页一律直接用返回值本身
    const page = await notificationPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      unreadOnly: filter.value,
    })
    rows.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    error.value = e?.msg || e?.message || '通知加载失败'
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  load()
}

function changePage(page) {
  pageNum.value = page
  load()
}

async function readOne(item) {
  try {
    await readNotification(item.id)
    item.readFlag = 1
    if (filter.value === 1) await load()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function markAll() {
  markingAll.value = true
  try {
    await readAllNotification()
    ElMessage.success('已全部标记为已读')
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    markingAll.value = false
  }
}

onMounted(load)

const error = ref(''),
  markingAll = ref(false),
  detailVisible = ref(false),
  detail = ref(null)
function openNotification(n) {
  detail.value = n
  detailVisible.value = true
  if (n.readFlag === 0) readOne(n)
}
</script>
<style scoped>
.notification-item {
  display: flex;
  gap: 18px;
  padding: 24px;
  border-bottom: 1px solid #e6e9ef;
}
.notification-icon {
  width: 34px;
  height: 34px;
  border: 1px solid #e6e9ef;
  border-radius: 6px;
  display: grid;
  place-items: center;
  color: #7c8a9f;
  flex-shrink: 0;
}
.notification-main {
  min-width: 0;
  flex: 1;
}
.notification-main > .console-summary-row {
  padding: 0;
  border: 0;
  align-items: start;
}
.notification-title {
  border: 0;
  background: transparent;
  color: #425168;
  cursor: pointer;
  text-align: left;
  font-size: 14px;
}
.unread .notification-title {
  font-weight: 600;
  color: #26364e;
}
.notification-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #315ce7;
  margin-left: 8px;
}
.notification-main time {
  color: #8b96a6;
  font-size: 11px;
  white-space: nowrap;
}
.notification-main p {
  margin: 12px 0;
  line-height: 1.8;
  font-size: 13px;
  color: #68768b;
  white-space: pre-wrap;
}
.notification-bottom {
  display: flex;
  align-items: center;
  gap: 12px;
}
.notification-detail {
  white-space: pre-wrap;
  line-height: 1.9;
  margin-top: 24px;
}
@media (max-width: 700px) {
  .notification-item {
    padding: 16px;
    gap: 10px;
  }
  .notification-main > .console-summary-row {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
