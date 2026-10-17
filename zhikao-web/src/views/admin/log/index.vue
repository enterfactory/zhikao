<template>
  <AppPage title="操作日志" description="追踪成员操作、请求结果和失败原因。">
    <template #actions><el-button :icon="Refresh" @click="list.load()">刷新</el-button></template>
    <template #filter>
      <FilterBar :loading="list.loading" @search="list.search()" @reset="list.reset()">
        <FilterField label="模块">
          <el-select
            v-model="list.query.module"
            placeholder="全部模块"
            clearable
            @change="list.search()"
          >
            <el-option v-for="item in modules.data || []" :key="item" :label="item" :value="item" />
          </el-select>
        </FilterField>
        <FilterField label="操作人">
          <el-input
            v-model="list.query.operName"
            placeholder="账号，支持模糊"
            clearable
            @clear="list.search()"
          />
        </FilterField>
        <FilterField label="结果">
          <el-select
            v-model="list.query.resultStatus"
            placeholder="成功与失败"
            clearable
            @change="list.search()"
          >
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </FilterField>
        <FilterField label="时间范围">
          <el-date-picker
            v-model="list.query.range"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            @change="list.search()"
          />
        </FilterField>
      </FilterBar>
    </template>
    <DataTable
      :rows="list.rows"
      :total="list.total"
      :loading="list.loading"
      :error="list.error"
      :page-num="list.pageNum"
      :page-size="list.pageSize"
      @page-change="list.onPageChange"
      @size-change="list.onSizeChange"
      @retry="list.load()"
    >
      <el-table-column label="操作时间" width="175">
        <template #default="{ row }">{{ formatDateTime(row.operTime) }}</template>
      </el-table-column>
      <el-table-column label="操作 / 模块" min-width="200">
        <template #default="{ row }">
          <strong class="console-cell-title">{{ row.action }}</strong>
          <small class="console-cell-note">{{ row.module }}</small>
        </template>
      </el-table-column>
      <el-table-column label="操作人" min-width="150">
        <template #default="{ row }">
          {{ row.operName }}
          <small class="console-cell-note">{{ row.operRoleName || '—' }}</small>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.resultStatus === 1 ? 'success' : 'danger'">
            {{ row.resultStatus === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="110">
        <template #default="{ row }">{{ row.costMs }} ms</template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">请求详情</el-button>
        </template>
      </el-table-column>
    </DataTable>
    <ZkDrawer
      v-model="detailVisible"
      title="操作详情"
      description="查看本次请求、结果与参数记录"
      size="lg"
    >
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模块">{{ detail.module || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作">{{ detail.action || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ detail.operName || '-' }}（{{ detail.operRoleName || '未知角色' }}）
          </el-descriptions-item>
          <el-descriptions-item label="时间">
            {{ formatDateTime(detail.operTime, { withSeconds: true }) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源 IP">{{ detail.operIp || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">
            <span :class="`log-cost is-${costTone(detail.costMs)}`">{{ detail.costMs }} ms</span>
          </el-descriptions-item>
          <el-descriptions-item label="请求" :span="2">
            <span class="log-method" :class="methodTone(detail.method)">{{ detail.method }}</span>
            <span class="log-uri">{{ detail.requestUri }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.resultStatus !== 1" label="失败原因" :span="2">
            <span class="log-error">{{ detail.errorMsg || '未记录' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <section class="log-params">
          <header class="log-params__head">
            <h4 class="log-params__title">请求参数</h4>
            <el-button text size="small" :icon="CopyDocument" @click="copyParams">复制</el-button>
          </header>
          <pre class="log-params__body">{{ detail.params || '（未记录）' }}</pre>
        </section>
      </template>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { CopyDocument, Refresh } from '@element-plus/icons-vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import FilterBar from '@/components/FilterBar.vue'
import FilterField from '@/components/FilterField.vue'
import RowActions from '@/components/RowActions.vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import { useAsync, usePagedQuery } from '@/composables/useAsync'
import { costTone, formatDateTime } from '@/utils/format'
import { notifyError, notifySuccess } from '@/utils/feedback'
import { logModules, logPage } from '@/api/log'

/**
 * 操作日志页。
 *
 * 这一页是后台里信息密度最高的地方，所以它同时也是「设计缺陷最容易暴露」的地方：
 * 筛选控件宽度、时间格式、慢查询是否看得出来、详情放不放得下，全都一目了然。
 * 现在这些全部走共享层（FilterBar / DataTable 的 error 分支 / ZkDrawer），
 * 本页因此可以当作其余列表页的改造模板。
 */
// reactive 包一层：composable 返回的是 ref 集合，模板里就能直接写 list.rows
// 而不是 list.rows.value（对象字面量里的 ref 不会被自动解包）
const list = reactive(
  usePagedQuery(
    (params) =>
      logPage({
        module: params.module || undefined,
        operName: params.operName || undefined,
        resultStatus: params.resultStatus ?? undefined,
        startTime: params.range?.[0],
        endTime: params.range?.[1],
        pageNum: params.pageNum,
        pageSize: params.pageSize,
      }),
    {
      defaultQuery: () => ({ module: null, operName: '', resultStatus: null, range: null }),
      errorText: '操作日志读取失败，请检查后端服务是否可用',
    },
  ),
)

const modules = reactive(
  useAsync(async () => (await logModules()) || [], {
    errorText: '模块列表加载失败',
  }),
)

const detailVisible = ref(false)
const detail = ref(null)

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

/** 只给动词上色：POST/PUT/DELETE 是写操作，扫一眼就能区分读与写 */
function methodTone(method) {
  const value = String(method || '').toUpperCase()
  if (value === 'DELETE') return 'is-delete'
  if (value === 'POST' || value === 'PUT') return 'is-write'
  return 'is-read'
}

async function copyParams() {
  const text = detail.value?.params || ''
  if (!text) {
    notifyError({ message: '这条记录没有参数可复制' })
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    notifySuccess('参数已复制到剪贴板')
  } catch (e) {
    // http 环境下 clipboard API 不可用，这时给出手动选择的指引而不是静默失败
    notifyError(e, '浏览器拒绝了剪贴板写入，请在详情里手动选中复制')
  }
}
</script>
<style scoped>
.log-time,
.log-cost {
  font-variant-numeric: tabular-nums;
}

.log-role {
  margin-left: var(--zk-space-1);
  font-size: var(--zk-font-xs);
  color: var(--zk-text-placeholder);
}

.log-method {
  margin-right: var(--zk-space-2);
  padding: 0 4px;
  font-size: var(--zk-font-xs);
  font-weight: 600;
  border-radius: var(--zk-radius-sm);
}

.log-method.is-read {
  color: var(--zk-text-secondary);
  background: var(--zk-bg-hover);
}

.log-method.is-write {
  color: var(--zk-primary);
  background: var(--zk-primary-light);
}

.log-method.is-delete {
  color: var(--zk-danger);
  background: var(--zk-danger-light);
}

.log-uri {
  font-size: var(--zk-font-sm);
  color: var(--zk-text-regular);
  word-break: break-all;
}

.log-cost {
  color: var(--zk-text-secondary);
}

.log-cost.is-warning {
  color: var(--zk-warning);
  font-weight: 600;
}

.log-cost.is-danger {
  color: var(--zk-danger);
  font-weight: 600;
}

.log-error {
  color: var(--zk-danger);
  word-break: break-all;
}

.log-params {
  margin-top: var(--zk-space-4);
}

.log-params__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zk-space-2);
}

.log-params__title {
  margin: 0;
  font-size: var(--zk-font-sm);
  font-weight: 600;
  color: var(--zk-text-primary);
}

.log-params__body {
  margin: 0;
  padding: var(--zk-space-3);
  max-height: 320px;
  overflow: auto;
  font-size: var(--zk-font-xs);
  line-height: 1.7;
  color: var(--zk-text-regular);
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--zk-bg-hover);
  border: 1px solid var(--zk-border-light);
  border-radius: var(--zk-radius-md);
}
</style>
