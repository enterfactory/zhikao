<template>
  <div class="data-table">
    <!-- 工具栏：左侧批量操作，右侧刷新等次级操作 -->
    <div v-if="$slots.toolbar" class="data-table__toolbar">
      <slot name="toolbar" :selection="selection" />
    </div>

    <SkeletonBlock v-if="loading && !rows.length && !error" :rows="6" />

    <!-- 错误优先于空态：加载失败时列表为空，若不先判 error，
         用户会看到「暂无数据」并以为自己真的没有数据——这是最误导人的假空态 -->
    <EmptyState
      v-else-if="error"
      variant="error"
      :title="errorTitle"
      :description="error"
      action-text="重试"
      @action="emit('retry')"
    />

    <EmptyState
      v-else-if="!rows.length && !loading"
      :title="emptyTitle"
      :description="emptyDescription"
      :action-text="emptyActionText"
      @action="emit('empty-action')"
    />

    <template v-else>
      <el-table
        v-loading="loading"
        :data="rows"
        :row-key="rowKey"
        @selection-change="handleSelectionChange"
      >
        <!-- 勾选列：由 selectable 控制是否启用批量操作，selectableFn 控制哪些行可勾 -->
        <el-table-column v-if="selectable" type="selection" width="46" :selectable="selectableFn" />
        <slot />
      </el-table>

      <div v-if="showPagination" class="data-table__pager">
        <span class="data-table__total zk-text-secondary zk-text-sm">共 {{ total }} 条</span>
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next, jumper"
          background
          @current-change="emit('page-change', $event)"
          @size-change="emit('size-change', $event)"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import EmptyState from './EmptyState.vue'
import SkeletonBlock from './SkeletonBlock.vue'

/**
 * 数据表格封装：把「加载态 / 空态 / 分页 / 勾选」这四件每个列表页都要重复的事收口。
 *
 * 一期 8 个列表页各自写 el-table + el-pagination + v-loading，
 * 结果是空态有的页面有、有的没有，分页参数各有各的默认值。
 * 统一之后，新页面只要写列定义。
 *
 * 注意：分页上限与后端保持一致（后端 PageQuery 上限 100），
 * 前端 page-sizes 也止步于 100 —— 两边口径一致，才不会出现
 * 「传了 500 却被静默截断成 100」这种让人困惑的行为。
 */
defineProps({
  rows: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  rowKey: { type: [String, Function], default: 'id' },
  selectable: { type: Boolean, default: false },
  /** 哪些行可勾选（如「只能批删自己创建的数据」） */
  selectableFn: { type: Function, default: () => true },
  showPagination: { type: Boolean, default: true },
  /** 加载失败的简述信息；非空即进入 error 态（含「重试」入口），空串/null 视为无错误 */
  error: { type: String, default: '' },
  errorTitle: { type: String, default: '数据加载失败' },
  emptyTitle: { type: String, default: '暂无数据' },
  emptyDescription: { type: String, default: '当前筛选条件下没有匹配的记录' },
  emptyActionText: { type: String, default: '' },
})

const emit = defineEmits([
  'page-change',
  'size-change',
  'selection-change',
  'empty-action',
  'retry',
])

const selection = ref([])

function handleSelectionChange(rows) {
  selection.value = rows
  emit('selection-change', rows)
}
</script>

<style scoped>
.data-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-3);
  margin-bottom: var(--zk-space-3);
  flex-wrap: wrap;
}

.data-table__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zk-space-3);
  margin-top: var(--zk-space-4);
  flex-wrap: wrap;
}

.data-table__total {
  white-space: nowrap;
}
</style>
