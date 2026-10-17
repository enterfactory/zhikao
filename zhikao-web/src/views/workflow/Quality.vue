<template>
  <AppPage title="资料质量评测" description="建立标准答案，独立复核，持续记录资料识别的实际表现。">
    <template #actions><el-button :loading="loading" @click="load">刷新数据</el-button></template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="quality-alert" />
    <div class="quality-metrics">
      <section v-for="m in metrics" :key="m.label" class="console-panel"><span>{{ m.label }}</span><strong>{{ m.value }}</strong><small>{{ m.note }}</small></section>
    </div>
    <section class="console-panel quality-section">
      <div class="quality-heading"><div><h2>人工标注与签核</h2><p>标准题目需要对照原文填写，由两个不同账号分别确认。编辑后需重新签核。</p></div></div>
      <div class="quality-toolbar">
        <el-select v-model="rawId" filterable placeholder="选择一份已导入资料" style="width: 350px">
          <el-option v-for="doc in documents" :key="doc.id" :value="doc.id" :label="`${doc.docName} · #${doc.id}`" />
        </el-select>
        <el-button type="primary" :disabled="!rawId" @click="openAnnotation()">标注标准题目</el-button>
        <router-link to="/resources/imports">导入更多资料</router-link>
      </div>
      <el-table :data="annotations" v-loading="loading" empty-text="尚未建立标准标注。选择资料并对照原文开始填写。">
        <el-table-column label="资料" min-width="190"><template #default="{ row }">{{ documentName(row.rawId) }}</template></el-table-column>
        <el-table-column prop="family" label="来源家族" min-width="120" />
        <el-table-column label="集合" width="90"><template #default="{ row }">{{ row.split === 'holdout' ? '保留集' : '开发集' }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'VERIFIED' ? 'success' : 'info'">{{ states[row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="复核人" min-width="145"><template #default="{ row }">{{ [row.firstReviewer, row.secondReviewer].filter(Boolean).join(' / ') || '待签核' }}</template></el-table-column>
        <el-table-column label="操作" width="160"><template #default="{ row }"><el-button link type="primary" @click="openAnnotation(row)">编辑</el-button><el-button v-if="row.status !== 'VERIFIED'" link type="primary" @click="sign(row)">签核</el-button></template></el-table-column>
      </el-table>
    </section>
    <section class="console-panel quality-section">
      <div class="quality-heading"><div><h2>评测报告</h2><p>只对已完成双人签核的资料计分。没有标准标注的集合会显示“等待标注”。</p></div><div class="quality-toolbar"><el-select v-model="split" style="width:110px"><el-option label="开发集" value="development" /><el-option label="保留集" value="holdout" /></el-select><el-button type="primary" :loading="running" @click="evaluate">运行评测</el-button></div></div>
      <div class="quality-toolbar"><el-select v-model="selectedRun" placeholder="选择历史报告" style="width:420px" @change="showRun"><el-option v-for="run in runs" :key="run.id" :value="run.id" :label="`${run.name} · ${run.createdAt}`" /></el-select><el-button :disabled="!report" @click="exportReport">导出报告</el-button></div>
      <template v-if="report">
        <el-alert v-if="report.status === 'INCOMPLETE_PREDICTIONS'" title="部分历史资料缺少识别快照，未纳入计分。请查看每份资料的说明；当前报告不代表完整基准结果。" type="warning" :closable="false" />
        <el-alert :title="report.verifiedDocumentCount ? `本次纳入 ${report.verifiedDocumentCount} 份已签核资料；请结合样本范围判断结果` : '等待人工标注与双人签核，当前没有可报告的真实识别准确率'" :type="report.verifiedDocumentCount ? 'info' : 'warning'" :closable="false" />
        <el-table :data="report.documents || []" class="quality-report-table">
          <el-table-column prop="rawId" label="资料编号" width="100" /><el-table-column prop="format" label="格式" width="80" />
          <el-table-column prop="expectedQuestions" label="标准题数" width="100" /><el-table-column prop="predictedQuestions" label="识别题数" width="100" />
          <el-table-column label="分题 F1"><template #default="{ row }">{{ percent(row.metrics?.f1) }}</template></el-table-column>
          <el-table-column label="答案准确率"><template #default="{ row }">{{ percent(row.metrics?.answerAccuracy) }}</template></el-table-column>
          <el-table-column label="公式准确率"><template #default="{ row }">{{ percent(row.metrics?.formulaAccuracy) }}</template></el-table-column>
          <el-table-column label="标注字段全对率"><template #default="{ row }">{{ percent(row.metrics?.usableRate) }}</template></el-table-column>
          <el-table-column label="文本错误率"><template #default="{ row }">{{ percent(row.metrics?.cer) }}</template></el-table-column>
          <el-table-column label="说明" min-width="200"><template #default="{ row }">{{ row.reason || row.metrics?.cerReason || '—' }}</template></el-table-column>
        </el-table>
        <h3>分组与样本范围</h3>
        <el-table :data="report.aggregates || []">
          <el-table-column prop="group" label="分组" min-width="160" />
          <el-table-column prop="measuredDocuments" label="计分资料" width="90" />
          <el-table-column prop="excludedDocuments" label="缺证据资料" width="100" />
          <el-table-column prop="expectedQuestions" label="标准题数" width="90" />
          <el-table-column label="分题 F1"><template #default="{ row }">{{ percent(row.f1) }}</template></el-table-column>
          <el-table-column label="字段全对率 / 95%区间" min-width="180"><template #default="{ row }">{{ percent(row.usableRate) }}<small v-if="row.usableRateWilson95">（{{ row.usableRateWilson95.map(percent).join('–') }}）</small></template></el-table-column>
          <el-table-column label="识别 P50 / P95" min-width="145"><template #default="{ row }">{{ millis(row.extractionP50Millis) }} / {{ millis(row.extractionP95Millis) }}</template></el-table-column>
        </el-table>
        <el-collapse class="quality-report-table"><el-collapse-item title="定位错误样例与漏题" name="errors"><template v-for="doc in report.documents || []" :key="doc.rawId"><div v-for="(item, index) in doc.errors || []" :key="index" class="quality-note">资料 #{{ doc.rawId }} · 原题号 {{ item.questionNumber }}：{{ item.fields.map(field => fieldNames[field] || field).join('、') }}<router-link :to="`/resources/review?document=${doc.rawId}`"> 前往核对 →</router-link></div></template></el-collapse-item></el-collapse>
        <p class="quality-note">识别耗时、版本、公式分母、复核经过时长及评分规则包含在导出报告中。未取得实际费用时成本为空；空值表示缺少依据。</p>
      </template>
      <el-empty v-else description="运行一次评测或选择历史报告" />
    </section>
    <el-drawer v-model="editing" title="标准资料标注" size="min(960px, 96vw)" :close-on-click-modal="false">
      <el-alert title="请独立对照原文填写标准内容。保存为草稿后，由两位复核人分别登录签核。" type="info" :closable="false" />
      <div class="quality-toolbar" style="margin-top:16px"><strong>{{ documentName(form.rawId) }}</strong><el-button @click="downloadSource">下载原文核对</el-button><router-link :to="`/resources/review?document=${form.rawId}`">打开原文对照工作台</router-link></div>
      <el-form label-position="top">
        <el-alert v-if="form.datasetFrozen" title="该资料已签核过，来源家族与集合归属已冻结。修订标准答案后仍需重新双人签核。" type="info" :closable="false" />
        <div class="quality-form-grid"><el-form-item label="来源家族"><el-input v-model="form.family" :disabled="!!form.datasetFrozen" placeholder="例如：某课程2026期末卷；同一来源保持一致" maxlength="255" /></el-form-item><el-form-item label="评测集合"><el-select v-model="form.split" :disabled="!!form.datasetFrozen"><el-option label="开发集" value="development" /><el-option label="保留集" value="holdout" /></el-select></el-form-item></div>
        <el-form-item label="资料许可与脱敏说明"><el-input v-model="form.licenseNote" type="textarea" :rows="2" placeholder="记录使用许可、来源及个人信息处理情况" /></el-form-item>
        <el-form-item label="标准全文（可选，用于计算字符错误率）"><el-input v-model="form.referenceText" type="textarea" :rows="3" placeholder="对照原文校正后的全文；留空时不报告字符错误率" /></el-form-item>
        <section v-for="(q, index) in form.questions" :key="index" class="quality-question">
          <div class="quality-heading"><h3>标准题目 {{ index + 1 }}</h3><el-button link type="danger" @click="form.questions.splice(index, 1)">移除</el-button></div>
          <div class="quality-form-grid"><el-form-item label="原文题号"><el-input v-model="q.question_number" /></el-form-item><el-form-item label="原文页码（无固定页码可留空）"><el-input-number v-model="q.page" :min="1" /></el-form-item></div>
          <el-form-item label="题型"><el-select v-model="q.question_type"><el-option v-for="(name, type) in questionTypes" :key="type" :label="name" :value="Number(type)" /></el-select></el-form-item>
          <el-form-item label="标准题干"><el-input v-model="q.content" type="textarea" :rows="3" /></el-form-item>
          <div v-if="[1,2].includes(q.question_type)" class="quality-form-grid"><el-form-item v-for="key in ['A','B','C','D']" :key="key" :label="`选项 ${key}`"><el-input v-model="q.options[key]" /></el-form-item></div>
          <el-form-item label="标准答案"><el-input v-model="q.answer" /></el-form-item>
          <el-form-item label="关键公式 LaTeX（每行一条，不含美元定界符）"><el-input v-model="q.formulaLines" type="textarea" :rows="2" /></el-form-item>
        </section>
        <el-button @click="addQuestion">添加标准题目</el-button>
      </el-form>
      <template #footer><el-button @click="editing=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存标注草稿</el-button></template>
    </el-drawer>
  </AppPage>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppPage from '@/components/AppPage.vue'
import request from '@/utils/request'
import { crawlerDocumentPage } from '@/api/crawler'
import { documentSource } from '@/api/workflow'
const loading=ref(false),running=ref(false),saving=ref(false),error=ref(''),summary=ref({}),documents=ref([]),annotations=ref([]),runs=ref([]),rawId=ref(),selectedRun=ref(),report=ref(null),split=ref('development'),editing=ref(false),form=ref({questions:[]})
const states={DRAFT:'标注草稿',FIRST_REVIEW:'等待二审',VERIFIED:'双人签核'}
const questionTypes={1:'单选题',2:'多选题',3:'判断题',4:'填空题',5:'简答题'}
const fieldNames={missingQuestion:'漏题',content:'题干',answer:'答案',type:'题型',options:'选项',page:'页码',formula:'公式'}
function millis(value){return value==null?'—':`${(value/1000).toFixed(2)}秒`}
const metrics=computed(()=>[{label:'已导入资料',value:summary.value.documents??'—',note:'可建立标准标注的资料'}, {label:'已建标注',value:summary.value.annotated??'—',note:'草稿与已签核资料'}, {label:'双人签核',value:summary.value.verified??'—',note:'只有这些资料进入质量评测'}, {label:'评测状态',value:summary.value.benchmarkReady?'已有标准样本':'等待签核',note:'样本范围决定结论适用范围'}])
function documentName(id){return documents.value.find(d=>d.id===id)?.docName || `资料 #${id}`}
function percent(value){return value==null?'—':`${(value*100).toFixed(1)}%`}
async function load(){loading.value=true;error.value='';try{const data=await Promise.all([request.get('/v2/quality/summary'),crawlerDocumentPage({pageNum:1,pageSize:100}),request.get('/v2/quality/annotations'),request.get('/v2/quality/runs')]);[summary.value,annotations.value,runs.value]=[data[0],data[2],data[3]];documents.value=data[1].records||[];if(selectedRun.value)showRun()}catch(e){error.value=e.message||'加载失败'}finally{loading.value=false}}
function addQuestion(){form.value.questions.push({question_number:String(form.value.questions.length+1),page:null,question_type:1,content:'',options:{A:'',B:'',C:'',D:''},answer:'',formulaLines:''})}
async function openAnnotation(row){try{if(row){form.value={...row,questions:JSON.parse(row.questionsJson).map(q=>({...q,options:q.options||{},formulaLines:(q.formulas||[]).map(f=>f.latex).join('\n')}))}}else{const existing=annotations.value.find(a=>a.rawId===rawId.value);if(existing)return openAnnotation(existing);form.value=await request.get(`/v2/quality/documents/${rawId.value}/template`);form.value.questions=[];addQuestion()}editing.value=true}catch(e){error.value=e.message||'标注加载失败'}}
async function save(){saving.value=true;try{const questions=form.value.questions.map(q=>({...q,options:Object.fromEntries(Object.entries(q.options||{}).filter(([,value])=>String(value).trim())),formulas:(q.formulaLines||'').split('\n').filter(s=>s.trim()).map((latex,i)=>({id:String(i+1),latex:latex.trim()}))}));await request.put(`/v2/quality/documents/${form.value.rawId}/annotation`,{...form.value,questions});editing.value=false;ElMessage.success('标注草稿已保存，需要重新签核');await load()}catch(e){ElMessage.error(e.message||'保存失败')}finally{saving.value=false}}
async function sign(row){try{await ElMessageBox.confirm('确认已独立对照原文核对全部标准题目、答案及公式？','签核标准标注',{confirmButtonText:'确认签核',cancelButtonText:'取消'});await request.post(`/v2/quality/annotations/${row.id}/sign`,{expectedVersion:row.revision});ElMessage.success('签核已记录');await load()}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message||'签核失败'}}
async function evaluate(){running.value=true;try{const result=await request.post('/v2/quality/runs',{split:split.value});selectedRun.value=result.id;await load()}catch(e){error.value=e.message||'评测失败'}finally{running.value=false}}
function showRun(){const row=runs.value.find(r=>r.id===selectedRun.value);report.value=row?JSON.parse(row.summaryJson):null}
function exportReport(){const row=runs.value.find(r=>r.id===selectedRun.value);download(new Blob([JSON.stringify({...row,report:report.value},null,2)],{type:'application/json'}),`资料评测-${selectedRun.value}.json`)}
function download(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
async function downloadSource(){try{download(await documentSource(form.value.rawId),documentName(form.value.rawId))}catch(e){error.value=e.message||'下载失败'}}
onMounted(load)
</script>
<style scoped>
.quality-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin-bottom:24px}.quality-metrics section{padding:22px}.quality-metrics span,.quality-metrics small{display:block;color:#697586}.quality-metrics strong{display:block;font-size:28px;font-weight:600;color:#172b4d;margin:12px 0}.quality-metrics small{font-size:12px;line-height:1.6}.quality-section{padding:24px;margin-bottom:24px}.quality-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:18px}.quality-heading h2{font-size:18px;margin:0 0 8px}.quality-heading h3{margin:0;font-size:16px}.quality-heading p,.quality-note{font-size:13px;color:#697586;line-height:1.8;margin:0}.quality-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:18px}.quality-form-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.quality-question{padding:22px;background:#f8fafc;border:1px solid #e5eaf1;border-radius:8px;margin:20px 0}.quality-alert{margin-bottom:20px}.quality-report-table{margin:18px 0}.quality-note{margin-top:14px}@media(max-width:900px){.quality-metrics{grid-template-columns:1fr 1fr}.quality-form-grid{grid-template-columns:1fr}.quality-heading{align-items:flex-start;flex-direction:column}}
</style>
