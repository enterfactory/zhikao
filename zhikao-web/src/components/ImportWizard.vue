<template>
  <ZkDialog
    v-model="visible"
    :title="kind === 'users' ? '导入组织成员' : '批量导入题目'"
    size="lg"
    :loading="busy"
    :confirm-text="step === 1 ? '下一步：核对文件' : step === 2 ? '确认导入' : '完成'"
    @confirm="next"
    @closed="reset"
  >
    <el-steps :active="step - 1" simple finish-status="success">
      <el-step title="选择文件" />
      <el-step title="核对导入" />
      <el-step title="处理结果" />
    </el-steps>
    <div class="import-step">
      <template v-if="step === 1">
        <p class="console-note">请使用导入模板准备数据，保留模板的列名与列顺序。</p>
        <el-form v-if="kind === 'questions'" label-position="top">
          <el-form-item label="导入到科目" required>
            <el-select v-model="targetSubject" placeholder="请选择科目">
              <el-option v-for="s in subjects" :key="s.id" :label="s.subjectName" :value="s.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".xls,.xlsx"
          :on-change="choose"
          :on-remove="() => (file = null)"
        >
          <el-icon :size="28"><Upload /></el-icon>
          <p>点击选择或拖入 Excel 文件</p>
          <small>支持 .xlsx / .xls，单次导入一个文件</small>
        </el-upload>
        <el-button link type="primary" @click="download">下载字段模板</el-button>
      </template>
      <template v-else-if="step === 2">
        <el-alert :title="file?.name" :closable="false" type="info" show-icon />
        <h3 class="console-section-label" style="margin-top: 24px">导入前核对</h3>
        <el-table :data="fields">
          <el-table-column prop="name" label="模板字段" />
          <el-table-column prop="rule" label="填写要求" min-width="220" />
        </el-table>
        <p class="console-note" style="margin-top: 16px">
          按模板列顺序读取。{{
            kind === 'users'
              ? '确认后创建成员账号，处理结果会列出失败原因。'
              : '下方预校验不会入库，确认后才写入题库。'
          }}
        </p>
        <el-alert
          v-if="preview"
          :title="`预校验：可导入 ${preview.successCount || 0} 条，失败 ${preview.failCount || 0} 条`"
          :type="preview.failCount ? 'warning' : 'success'"
          :closable="false"
        />
        <ul v-if="preview?.previewRows?.length" class="import-preview">
          <li v-for="(row, i) in preview.previewRows" :key="i">{{ row }}</li>
        </ul>
        <ul v-if="preview?.errors?.length" class="import-errors">
          <li v-for="(e, i) in preview.errors" :key="i">{{ e }}</li>
        </ul>
      </template>
      <template v-else>
        <el-result
          :icon="result?.failCount ? 'warning' : 'success'"
          :title="`成功 ${result?.successCount || 0} 条 · 失败 ${result?.failCount || 0} 条`"
          sub-title="处理结果来自实际导入响应"
        />
        <ul class="import-errors">
          <li v-for="(e, i) in result?.errors || []" :key="i">{{ e }}</li>
        </ul>
      </template>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    </div>
  </ZkDialog>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import ZkDialog from './ZkDialog.vue'
import { importUsers, downloadUserTemplate } from '@/api/system'
import { importQuestions, downloadQuestionTemplate } from '@/api/exam'
import { saveBlob } from '@/utils/download'
const props = defineProps({
  modelValue: Boolean,
  kind: { type: String, default: 'users' },
  subjectId: { type: [Number, String], default: null },
  subjects: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'done'])
const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const step = ref(1),
  file = ref(null),
  busy = ref(false),
  error = ref(''),
  result = ref(null),
  preview = ref(null),
  targetSubject = ref(props.subjectId)
watch(
  () => props.modelValue,
  (v) => {
    if (v) targetSubject.value = props.subjectId
  },
)
const fields = computed(() =>
  props.kind === 'users'
    ? [
        { name: '用户名 / 真实姓名', rule: '必填，用户名不可重复' },
        { name: '角色 / 班级', rule: '管理员、教师或学生；学生需填写已有班级名称' },
        { name: '手机号', rule: '可选；账号按系统默认密码创建，首次使用应修改密码' },
      ]
    : [
        { name: '题型 / 题干 / 答案', rule: '必填，保持模板列名' },
        { name: '选项 A–D', rule: '选择题需填写有效选项' },
        { name: '知识点 / 解析 / 分值', rule: '按模板填写' },
      ],
)
function choose(item) {
  if (!/\.(xlsx|xls)$/i.test(item.name) || item.size > 20 * 1024 * 1024) {
    file.value = null
    error.value = '请选择不超过 20 MB 的 .xlsx 或 .xls 文件'
    return
  }
  file.value = item.raw
  error.value = ''
}
function reset() {
  step.value = 1
  file.value = null
  error.value = ''
  preview.value = null
  result.value = null
}
async function download() {
  try {
    saveBlob(
      await (props.kind === 'users' ? downloadUserTemplate() : downloadQuestionTemplate()),
      props.kind === 'users' ? '用户导入模板.xlsx' : '题目导入模板.xlsx',
    )
  } catch (e) {
    error.value = e.message || '模板下载失败'
  }
}
async function next() {
  if (step.value === 3) {
    visible.value = false
    return
  }
  if (!file.value) {
    error.value = '请先选择导入文件'
    return
  }
  if (props.kind === 'questions' && !targetSubject.value) {
    error.value = '请选择科目'
    return
  }
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const data = new FormData()
    data.append('file', file.value)
    if (step.value === 1) {
      if (props.kind === 'questions')
        preview.value = await importQuestions(targetSubject.value, data, true)
      step.value = 2
    } else {
      result.value = await (props.kind === 'users'
        ? importUsers(data)
        : importQuestions(targetSubject.value, data))
      step.value = 3
      emit('done', result.value)
    }
  } catch (e) {
    error.value = e.message || '导入失败，请检查文件后重试'
  } finally {
    busy.value = false
  }
}
</script>
<style scoped>
.import-step {
  margin-top: 24px;
}
.import-step > .console-note {
  margin-bottom: 18px;
}
.import-step :deep(.el-upload) {
  width: 100%;
}
.import-step :deep(.el-upload-dragger) {
  width: 100%;
  padding: 32px 20px;
  border-radius: 5px;
}
.import-step :deep(.el-upload-dragger p) {
  margin: 12px 0 6px;
}
.import-step :deep(.el-upload-dragger small) {
  color: #8792a4;
}
.import-errors {
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: #b44141;
}
.import-preview {
  padding-left: 18px;
  line-height: 1.8;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.import-step > .el-alert {
  margin-top: 18px;
}
</style>
