<template>
  <AppPage title="采集与识别" description="维护资料来源，跟踪识别任务与解析结果。">
    <template #actions>
      <el-button @click="refreshAll">刷新</el-button>
      <el-dropdown @command="sourceAction">
        <el-button>
          导入方式
          <el-icon><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="upload">上传文档</el-dropdown-item>
            <el-dropdown-item command="directory">本地目录</el-dropdown-item>
            <el-dropdown-item command="run">运行来源采集</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button type="primary" @click="openSourceForm()">新建来源</el-button>
    </template>
    <section class="console-panel crawler-workspace">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="来源管理" name="sources">
          <DataTable
            :rows="sourceRows"
            :loading="sourceLoading"
            :error="sourceErr"
            :show-pagination="false"
            empty-title="暂无采集来源"
            empty-description="点右上角「上传文档」直接把试卷丢进来；文档已在服务器目录里时，用「新建来源」登记目录再采集"
            @retry="loadSources"
          >
            <template #toolbar>
              <span class="zk-text-secondary zk-text-sm">
                来源就是一个文档目录；停用中的来源不会被「重新采集」选中
              </span>
              <el-button text size="small" :icon="Refresh" @click="loadSources">刷新</el-button>
            </template>

            <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.name || '-' }}</template>
            </el-table-column>
            <el-table-column label="文档目录" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.baseUrl || '-' }}</template>
            </el-table-column>
            <el-table-column label="授权说明" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.licenseNote || '-' }}</template>
            </el-table-column>
            <el-table-column label="默认科目" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ subjectNameOf(row.defaultSubjectId) }}</template>
            </el-table-column>
            <el-table-column label="启用" width="90">
              <template #default="{ row }">
                <el-switch
                  :model-value="isEnabled(row.enabled)"
                  size="small"
                  :loading="togglingId === row.id"
                  @change="(val) => handleToggle(row, val)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  size="small"
                  :icon="Edit"
                  @click="openSourceForm(row)"
                >
                  编辑
                </el-button>
              </template>
            </el-table-column>
          </DataTable>
        </el-tab-pane>
        <el-tab-pane label="采集任务" name="tasks">
          <DataTable
            :rows="taskRows"
            :total="taskTotal"
            :loading="taskLoading"
            :page-num="taskQuery.pageNum"
            :page-size="taskQuery.pageSize"
            :error="taskErr"
            empty-title="暂无采集任务"
            empty-description="点右上角「上传文档」或「本地目录」发起一次采集"
            @page-change="onTaskPage"
            @size-change="onTaskSize"
            @retry="loadTasks"
          >
            <template #toolbar>
              <span class="zk-text-secondary zk-text-sm">
                {{
                  hasActiveTask
                    ? '有任务正在执行，列表每 10 秒自动刷新'
                    : '存在进行中任务时才会自动轮询'
                }}
              </span>
              <el-button text size="small" :icon="Refresh" @click="loadTasks">刷新</el-button>
            </template>

            <el-table-column prop="id" label="任务号" width="90" />
            <el-table-column label="来源" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.sourceName || sourceNameOf(row.sourceId) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <span class="task-status">
                  <!-- RUNNING 行给一个转圈的图标：轮询间隔 10s，没有它就看不出「任务在动」 -->
                  <el-icon v-if="isActiveTask(row)" class="task-status__spin"><Loading /></el-icon>
                  <el-tag :type="taskStatusType(row.status)" size="small" effect="plain">
                    {{ taskStatusLabel(row.status) }}
                  </el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="新文档" width="90">
              <template #default="{ row }">{{ countOf(row.fetched) }}</template>
            </el-table-column>
            <el-table-column label="候选题" width="80">
              <template #default="{ row }">{{ countOf(row.cleaned) }}</template>
            </el-table-column>
            <el-table-column label="待审" width="80">
              <template #default="{ row }">{{ countOf(row.staged) }}</template>
            </el-table-column>
            <el-table-column label="结果说明" min-width="180">
              <template #default="{ row }">
                <!-- error 字段在七期还承载「本次没有新文档」这类良性说明，
                   只有真失败才标红，否则满屏红字反而误导 -->
                <el-tooltip v-if="row.error" :content="row.error" placement="top" :show-after="150">
                  <span
                    :class="
                      row.status === 'FAILED'
                        ? 'zk-text-danger zk-ellipsis'
                        : 'zk-text-secondary zk-ellipsis'
                    "
                  >
                    {{ row.error }}
                  </span>
                </el-tooltip>
                <span v-else class="zk-text-placeholder">-</span>
              </template>
            </el-table-column>
            <el-table-column label="开始 / 结束" width="190">
              <template #default="{ row }">
                <div class="zk-text-sm">
                  <div>{{ row.startedAt || '-' }}</div>
                  <div class="zk-text-secondary">{{ row.finishedAt || '' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <!-- 任务只有聚合计数，看不出「哪一份文档、为什么没出题」，所以给一个下钻入口 -->
                <el-button text type="primary" size="small" @click="goToDocuments(row.id)">
                  解析日志
                </el-button>
              </template>
            </el-table-column>
          </DataTable>
        </el-tab-pane>
        <el-tab-pane label="解析日志" name="documents">
          <DataTable
            :rows="documentRows"
            :total="documentTotal"
            :loading="documentLoading"
            :page-num="documentQuery.pageNum"
            :page-size="documentQuery.pageSize"
            :error="documentErr"
            empty-title="暂无文档解析记录"
            empty-description="每摄取一份文档就会留下一行：取了多少字、走没走 OCR、哪个解析器认领、认出几道题、没进待审的原因"
            @page-change="onDocumentPage"
            @size-change="onDocumentSize"
            @retry="loadDocuments"
          >
            <template #toolbar>
              <div class="zk-flex zk-gap-2 doc-filter">
                <el-input
                  v-model="documentQuery.keyword"
                  class="doc-filter__keyword"
                  placeholder="按文档名筛选"
                  clearable
                  :prefix-icon="Search"
                  @keyup.enter="searchDocuments"
                  @clear="searchDocuments"
                />
                <!-- 未出题的文档才是需要人看的：默认只列出「进待审 0 道」的记录，勾掉可看全部 -->
                <el-checkbox v-model="documentQuery.unresolvedOnly" @change="searchDocuments">
                  只看出题失败
                </el-checkbox>
                <el-tag
                  v-if="documentQuery.taskId"
                  closable
                  type="info"
                  size="small"
                  @close="clearDocumentTask"
                >
                  任务 {{ documentQuery.taskId }}
                </el-tag>
              </div>
              <el-button text size="small" :icon="Refresh" @click="loadDocuments">刷新</el-button>
            </template>

            <el-table-column label="文档" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="doc-cell">
                  <div class="doc-cell__name">{{ row.docName || '（未知文档）' }}</div>
                  <div class="doc-cell__task">任务 {{ row.taskId ?? '-' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="取字方式" width="132">
              <template #default="{ row }">
                <el-tag
                  :type="row.viaOcr === 1 ? 'warning' : 'success'"
                  size="small"
                  effect="plain"
                >
                  {{ viaOcrLabel(row.viaOcr) }}
                </el-tag>
                <!-- 通道链：哪几层 OCR 真为这份文档动过手（S2 落库列，老数据为 null 就不硬编） -->
                <div v-if="channelChips(row).length" class="doc-cell__task">
                  {{ channelChips(row).join(' → ') }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="字数" width="90">
              <template #default="{ row }">{{ numOrDash(row.charCount) }}</template>
            </el-table-column>
            <el-table-column label="解析器" width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ hasTrace(row) ? row.parserName || '未认领' : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="解析出题" width="100">
              <template #default="{ row }">{{ numOrDash(row.parsedCount) }}</template>
            </el-table-column>
            <el-table-column label="进入待审" width="100">
              <template #default="{ row }">{{ numOrDash(row.stagedCount) }}</template>
            </el-table-column>
            <!-- 质量分是自动算的（入审率/空槽嫌疑/答案率/选项齐全/置信度加权），悬停看构成 -->
            <el-table-column label="质量分" width="96">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.qualityScore !== null && row.qualityScore !== undefined"
                  :content="row.qualityDetail || '无构成说明'"
                  placement="top"
                  :show-after="120"
                >
                  <el-tag :type="qualityTone(row.qualityScore)" size="small" effect="plain">
                    {{ row.qualityScore }}
                  </el-tag>
                </el-tooltip>
                <span v-else class="zk-text-placeholder">-</span>
              </template>
            </el-table-column>
            <!-- 公式回插（S2）：插回数/候选数。没候选与没留痕都显示 -，两者的区别在详情里说清 -->
            <el-table-column label="公式回插" width="104">
              <template #default="{ row }">
                <el-tooltip
                  v-if="Number(row.formulaCandidates) > 0"
                  :content="formulaTooltip(row)"
                  placement="top"
                  :show-after="120"
                >
                  <span class="formula-cell">
                    {{ Number(row.formulaRecovered) || 0 }}/{{ row.formulaCandidates }}
                  </span>
                </el-tooltip>
                <span v-else class="zk-text-placeholder">-</span>
              </template>
            </el-table-column>
            <el-table-column label="结果说明" min-width="240">
              <template #default="{ row }">
                <!-- 七期上线前的老数据这几列全为 null，与其显示空白，不如说明「当时没有逐文档留痕」 -->
                <span v-if="!hasTrace(row)" class="zk-text-secondary">
                  采集于解析日志上线前，无逐文档留痕
                </span>
                <el-tooltip
                  v-else-if="row.parseNote"
                  :content="row.parseNote"
                  placement="top"
                  :show-after="150"
                >
                  <span
                    :class="
                      stagedOf(row) ? 'zk-text-secondary zk-ellipsis' : 'zk-text-danger zk-ellipsis'
                    "
                  >
                    {{ row.parseNote }}
                  </span>
                </el-tooltip>
                <span v-else class="zk-text-placeholder">-</span>
              </template>
            </el-table-column>
            <el-table-column label="采集时间" width="170">
              <template #default="{ row }">
                <span class="zk-text-sm">{{ row.fetchTime || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openDocument(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </DataTable>
        </el-tab-pane>
        <el-tab-pane label="识别状态" name="status">
          <section class="stage-stats">
            <div
              v-for="item in PIPELINE"
              :key="item.key"
              class="stage-stats__card"
              :class="{ 'stage-stats__card--link': item.key === 'RAW' }"
              :title="item.key === 'RAW' ? '点击查看每份文档的解析日志' : ''"
              @click="item.key === 'RAW' && goToDocuments(null)"
            >
              <el-statistic :title="item.label" :value="statValue(item.key)" />
            </div>
            <p v-if="statsError" class="stage-stats__hint zk-text-sm">
              {{ statsError }}
              <el-button text size="small" @click="loadStats">重试</el-button>
            </p>
          </section>

          <!-- OCR 通道：扫描卷与照片全靠它取字，不可用时必须提前告诉使用者 -->
          <p class="ocr-status zk-text-sm">
            <span class="ocr-status__label">文字识别</span>
            <el-tag
              v-for="channel in ocrChannels"
              :key="channel.key"
              :type="channel.ok ? 'success' : 'info'"
              :effect="channel.ok ? 'light' : 'plain'"
              size="small"
            >
              {{ channel.label }}{{ channel.ok ? '可用' : '不可用' }}
            </el-tag>
            <span class="zk-text-secondary">{{ ocrHint }}</span>
          </p>
        </el-tab-pane>
        <el-tab-pane label="识别与成本" name="analytics" lazy>
          <OcrCostPanel ref="costPanelRef" @inspect="openDocument" />
        </el-tab-pane>
        <el-tab-pane label="候选题目" name="questions">
          <div class="console-toolbar">
            <el-form inline @submit.prevent>
              <el-form-item label="清洗阶段">
                <el-select
                  v-model="questionQuery.stage"
                  class="filter-control"
                  placeholder="全部"
                  clearable
                  filterable
                  @change="searchQuestions"
                >
                  <el-option
                    v-for="item in stageOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="科目">
                <el-select
                  v-model="questionQuery.subjectId"
                  class="filter-control"
                  placeholder="全部"
                  clearable
                  filterable
                  @change="searchQuestions"
                >
                  <el-option
                    v-for="item in subjectList"
                    :key="item.id"
                    :label="item.name || item.subjectName"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="学段">
                <!-- allow-create：后端 grade 的编码（JUNIOR / 初中 / 七年级…）未最终敲定，
               这里允许直接输入任意值，页面不会被枚举卡死 -->
                <el-select
                  v-model="questionQuery.grade"
                  class="filter-control"
                  placeholder="如 JUNIOR / 初中"
                  clearable
                  filterable
                  allow-create
                  default-first-option
                  @change="searchQuestions"
                >
                  <el-option
                    v-for="item in gradeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Search" @click="searchQuestions">查询</el-button>
                <el-button :icon="RefreshRight" @click="resetQuestions">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
          <DataTable
            :rows="questionRows"
            :total="questionTotal"
            :loading="questionLoading"
            :page-num="questionQuery.pageNum"
            :page-size="questionQuery.pageSize"
            :error="questionErr"
            selectable
            empty-title="暂无采集题目"
            empty-description="点右上角「上传文档」或「本地目录」发起采集，识别与清洗在后台进行"
            @page-change="onQuestionPage"
            @size-change="onQuestionSize"
            @retry="loadQuestions"
          >
            <template #toolbar="{ selection }">
              <div class="zk-flex zk-gap-2">
                <el-button
                  type="primary"
                  size="small"
                  :icon="Select"
                  :disabled="!selection.length"
                  :loading="reviewing"
                  @click="openReview(selection, 'PROMOTE')"
                >
                  选中入库
                </el-button>
                <el-button
                  type="warning"
                  size="small"
                  :icon="CloseBold"
                  :disabled="!selection.length"
                  :loading="reviewing"
                  @click="openReview(selection, 'REJECT')"
                >
                  退回
                </el-button>
                <span class="zk-text-secondary zk-text-sm">
                  已选 {{ selection.length }} 条{{ selection.length ? '，入库后写入正式题库' : '' }}
                </span>
              </div>
              <div class="zk-flex zk-gap-2">
                <!-- 从解析日志下钻过来的「只看这一份文档」：不显示出来，用户会以为列表坏了 -->
                <el-tag
                  v-if="questionQuery.rawId"
                  closable
                  type="info"
                  size="small"
                  @close="clearDocumentFilter"
                >
                  仅文档 {{ documentFilterLabel }}
                </el-tag>
                <el-button text size="small" :icon="Refresh" @click="loadQuestions">刷新</el-button>
              </div>
            </template>

            <el-table-column label="题干" min-width="300">
              <template #default="{ row }">
                <!-- 题干在采集侧常是带标签的 HTML，列表里只给纯文本一行；
                   完整题干/选项/答案/解析放在 popover 里，避免表格被撑高 -->
                <el-popover placement="right" :width="520" trigger="click">
                  <template #reference>
                    <span class="content-cell">
                      {{ plainText(row.content) || '（题干为空，见详情）' }}
                    </span>
                  </template>
                  <div class="detail">
                    <div class="detail__row">
                      <span class="detail__label">题干</span>
                      <div class="detail__body">
                        <RichContent v-if="row.content" :html="row.content" />
                        <span v-else class="zk-text-placeholder">-</span>
                      </div>
                    </div>
                    <div v-if="optionsOf(row.options).length" class="detail__row">
                      <span class="detail__label">选项</span>
                      <div class="detail__body">
                        <p
                          v-for="opt in optionsOf(row.options)"
                          :key="opt.key"
                          class="detail__option"
                        >
                          <strong>{{ opt.key }}</strong>
                          .
                          <RichContent :html="opt.text" inline />
                        </p>
                      </div>
                    </div>
                    <div class="detail__row">
                      <span class="detail__label">答案</span>
                      <div class="detail__body">
                        <RichContent v-if="row.answer" :html="row.answer" />
                        <span v-else class="zk-text-placeholder">-</span>
                      </div>
                    </div>
                    <div class="detail__row">
                      <span class="detail__label">解析</span>
                      <div class="detail__body">
                        <RichContent v-if="row.analysis" :html="row.analysis" />
                        <span v-else class="zk-text-placeholder">（无解析）</span>
                      </div>
                    </div>
                    <div class="detail__row">
                      <span class="detail__label">标签</span>
                      <div class="detail__body zk-flex zk-gap-1">
                        <el-tag
                          v-for="tag in toArray(row.knowledgeTags)"
                          :key="tag"
                          size="small"
                          effect="plain"
                        >
                          {{ tag }}
                        </el-tag>
                        <span v-if="!toArray(row.knowledgeTags).length" class="zk-text-placeholder">
                          无
                        </span>
                      </div>
                    </div>
                    <div class="detail__row">
                      <span class="detail__label">原始 ID</span>
                      <p class="detail__body zk-text-secondary">{{ row.rawId ?? row.id ?? '-' }}</p>
                    </div>
                  </div>
                </el-popover>
              </template>
            </el-table-column>

            <el-table-column label="学科" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.subjectName || row.subjectId || '-' }}</template>
            </el-table-column>
            <el-table-column label="学段" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ gradeText(row) }}</template>
            </el-table-column>
            <el-table-column label="题型" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">
                  {{ questionTypeLabel(row.questionType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="难度" width="90">
              <template #default="{ row }">
                <el-tag :type="difficultyType(row.difficulty)" size="small">
                  {{ difficultyLabel(row.difficulty) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="阶段" width="100">
              <template #default="{ row }">
                <el-tag :type="stageType(row.stage)" size="small" effect="dark">
                  {{ stageLabel(row.stage) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="质量标记" min-width="170">
              <template #default="{ row }">
                <div class="zk-flex zk-gap-1">
                  <el-tag
                    v-for="flag in toArray(row.qualityFlags)"
                    :key="flag"
                    :type="flagType(flag)"
                    size="small"
                    effect="plain"
                  >
                    {{ flagLabel(flag) }}
                  </el-tag>
                  <span
                    v-if="!toArray(row.qualityFlags).length"
                    class="zk-text-placeholder zk-text-sm"
                  >
                    无
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="采集时间" width="165" show-overflow-tooltip>
              <template #default="{ row }">{{ row.createTime || '-' }}</template>
            </el-table-column>
          </DataTable>
        </el-tab-pane>
      </el-tabs>
    </section>
    <ZkDrawer v-model="sourceDialog" :title="sourceForm.id ? '编辑来源' : '新建来源'" size="lg">
      <el-form ref="sourceFormRef" :model="sourceForm" :rules="sourceRules" label-width="110px">
        <el-form-item label="来源名称" prop="name">
          <el-input v-model="sourceForm.name" placeholder="如：五年级数学试卷库" />
        </el-form-item>
        <el-form-item label="文档目录" prop="baseUrl">
          <el-input
            v-model="sourceForm.baseUrl"
            placeholder="服务器上的绝对路径，如 D:/题目资源/五年级"
          />
          <span class="zk-text-secondary zk-text-sm control-hint">
            目录里放 Word / PDF / 图片 / Markdown 等文档，采集时逐个取字；路径须在服务端白名单根之内
          </span>
        </el-form-item>
        <el-form-item label="授权说明">
          <el-input
            v-model="sourceForm.licenseNote"
            placeholder="这批文档的来源与授权，便于日后追溯"
          />
        </el-form-item>
        <el-form-item label="默认科目">
          <el-select
            v-model="sourceForm.defaultSubjectId"
            class="control-full"
            placeholder="可留空"
            clearable
            filterable
          >
            <el-option
              v-for="item in subjectList"
              :key="item.id"
              :label="item.name || item.subjectName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="sourceForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="sourceForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sourceDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSource">保存</el-button>
      </template>
    </ZkDrawer>

    <!-- ===== 重新采集 ===== -->
    <el-dialog v-model="runDialog" title="重新采集" width="460px">
      <el-form label-width="90px">
        <el-form-item label="采集来源">
          <el-select
            v-model="runSourceId"
            class="control-full"
            placeholder="请选择启用中的目录来源"
            filterable
          >
            <el-option
              v-for="item in enabledSources"
              :key="item.id"
              :label="`${item.name || '未命名'}（${item.baseUrl || '未配置目录'}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <p class="dialog-hint zk-text-secondary zk-text-sm">
          已经采过的文档不会重复取字（按文件路径去重），新增进目录的文件会被补上。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="runDialog = false">取消</el-button>
        <el-button type="primary" :loading="running" :disabled="!runSourceId" @click="submitRun">
          发起采集
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 本地目录导入 ===== -->
    <el-dialog v-model="importDialog" title="本地目录采集" width="520px">
      <el-form ref="importFormRef" :model="importForm" :rules="importRules" label-width="90px">
        <el-form-item label="目录路径" prop="dirPath">
          <el-input
            v-model="importForm.dirPath"
            placeholder="服务器上的绝对路径，如 D:/题目资源/五年级"
          />
        </el-form-item>
      </el-form>
      <p class="dialog-hint zk-text-secondary zk-text-sm">
        目录需为后端可访问的绝对路径，且在 zhikao.crawler.allowed-roots 白名单之内；
        导入只发起一条采集任务，识别与清洗在后台进行。
      </p>
      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">开始采集</el-button>
      </template>
    </el-dialog>

    <!-- ===== 文档上传 ===== -->
    <el-dialog v-model="uploadDialog" title="上传文档" width="600px">
      <el-upload
        ref="uploadRef"
        drag
        multiple
        :auto-upload="false"
        :accept="UPLOAD_ACCEPT"
        :on-change="onFileChange"
        :on-remove="onFileChange"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          把 Word / PDF / 试卷照片拖到这里，或
          <em>点击选择</em>
        </div>
        <template #tip>
          <div class="zk-text-secondary zk-text-sm dialog-hint">
            支持 docx / doc / pdf / png / jpg / jpeg / bmp / gif / webp / md / txt / json / jsonl /
            csv， 单次最多 {{ UPLOAD_MAX_FILES }} 份、单份最多 {{ UPLOAD_MAX_MB }}MB、合计最多
            {{ UPLOAD_MAX_TOTAL_MB }}MB。 扫描版 PDF 与图片会走 OCR，页数多时后台会跑几十秒。
          </div>
        </template>
      </el-upload>

      <!-- 拒绝原因逐条列出：批量上传里「1 份格式不支持」不该让整次操作变成一个错误号 -->
      <ul v-if="uploadRejected.length" class="reason-list">
        <li v-for="(reason, index) in uploadRejected" :key="index" class="zk-text-sm">
          {{ reason }}
        </li>
      </ul>

      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!uploadFiles.length"
          @click="submitUpload"
        >
          上传并采集{{ uploadFiles.length ? `（${uploadFiles.length} 份）` : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 批量审核（入库 / 退回） ===== -->
    <el-dialog
      v-model="reviewDialog"
      :title="isPromoteReview ? '题目入库' : '题目退回'"
      width="520px"
    >
      <!-- 有失败明细时切换成结果视图：逐条原因比一个「操作失败」有用得多 -->
      <div v-if="reviewResult" class="review-result">
        <p class="review-result__summary">
          成功
          <strong class="zk-text-success">{{ reviewSuccess }}</strong>
          道，未处理
          <strong class="zk-text-danger">{{ reviewFail }}</strong>
          道。
        </p>
        <ul class="reason-list">
          <li v-for="(reason, index) in reviewErrors" :key="index" class="zk-text-sm">
            {{ reason }}
          </li>
        </ul>
        <p class="zk-text-secondary zk-text-sm dialog-hint">
          未入库的题目仍留在「待审核」，原因已写进该题的审核备注；补齐科目后重新入库即可。
        </p>
      </div>

      <template v-else>
        <p class="dialog-hint">
          {{
            isPromoteReview
              ? `确认把选中的 ${reviewIds.length} 道题写入正式题库？入库后学生端即可搜到。`
              : `确认退回选中的 ${reviewIds.length} 道题？退回题目不会进入正式题库。`
          }}
        </p>

        <el-form label-position="top">
          <el-form-item v-if="isPromoteReview">
            <template #label>
              科目兜底
              <span class="zk-text-secondary zk-text-sm">（只补没识别出科目的题）</span>
            </template>
            <el-select
              v-model="reviewSubjectId"
              clearable
              filterable
              placeholder="按各题已识别的科目"
              class="control-full"
            >
              <el-option
                v-for="item in subjectList"
                :key="item.id"
                :label="item.name || item.subjectName"
                :value="item.id"
              />
            </el-select>
            <p class="zk-text-secondary zk-text-sm control-hint">
              文档 OCR 通常认不出题目属于哪门科目，而正式题库要求科目必填。这里选的科目只填补空缺，
              已经带科目的题目不受影响。
            </p>
          </el-form-item>

          <el-form-item label="审核备注">
            <el-input
              v-model="reviewNote"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="可留空，会记录在该题的审核备注里"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="reviewDialog = false">{{ reviewResult ? '知道了' : '取消' }}</el-button>
        <el-button v-if="!reviewResult" type="primary" :loading="reviewing" @click="submitReview">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 文档解析详情 ===== -->
    <ZkDrawer
      v-model="documentDialog"
      :title="documentDetailRow?.docName || '文档解析详情'"
      size="lg"
    >
      <div v-loading="documentDetailLoading" class="doc-detail">
        <div class="doc-detail__grid">
          <div class="doc-detail__field">
            <span class="doc-detail__label">取字方式</span>
            <span>{{ viaOcrLabel(documentDetailRow?.viaOcr) }}</span>
          </div>
          <div class="doc-detail__field">
            <span class="doc-detail__label">正文字数</span>
            <span>{{ numOrDash(documentDetailRow?.charCount) }}</span>
          </div>
          <div class="doc-detail__field">
            <span class="doc-detail__label">认领解析器</span>
            <span>
              {{
                hasTrace(documentDetailRow) ? documentDetailRow?.parserName || '未认领' : '未留痕'
              }}
            </span>
          </div>
          <div class="doc-detail__field">
            <span class="doc-detail__label">解析出题 / 进入待审</span>
            <strong>
              {{ numOrDash(documentDetailRow?.parsedCount) }} /
              {{ numOrDash(documentDetailRow?.stagedCount) }}
            </strong>
          </div>
          <div class="doc-detail__field">
            <span class="doc-detail__label">识别质量分</span>
            <el-tag
              v-if="
                documentDetailRow?.qualityScore !== null &&
                documentDetailRow?.qualityScore !== undefined
              "
              :type="qualityTone(documentDetailRow.qualityScore)"
              size="small"
              effect="plain"
            >
              {{ documentDetailRow.qualityScore }}
            </el-tag>
            <span v-else class="zk-text-placeholder">未留痕</span>
          </div>
          <div class="doc-detail__field">
            <span class="doc-detail__label">OCR 置信度</span>
            <span>{{ ocrConfidenceLabel(documentDetailRow) }}</span>
          </div>
          <div class="doc-detail__field">
            <span class="doc-detail__label">识别通道</span>
            <span>{{ channelChainLabel(documentDetailRow) }}</span>
          </div>
          <div class="doc-detail__field">
            <span class="doc-detail__label">公式片段逐层</span>
            <span>{{ formulaLayerText(documentDetailRow) }}</span>
          </div>
        </div>

        <p v-if="documentDetailRow?.qualityDetail" class="doc-detail__quality">
          <span class="doc-detail__label">质量构成</span>
          {{ documentDetailRow.qualityDetail }}
        </p>

        <p class="doc-detail__path">{{ documentDetailRow?.sourceUrl || '-' }}</p>

        <p v-if="documentDetailRow?.parseNote" class="doc-detail__note">
          {{ documentDetailRow.parseNote }}
        </p>

        <section class="doc-detail__section">
          <h4 class="doc-detail__title">
            识别出的正文
            <span v-if="documentDetailRow?.contentTruncated" class="zk-text-secondary zk-text-sm">
              （预览前 {{ (documentDetailRow?.contentPreview || '').length }} 字，共
              {{ countOf(documentDetailRow?.contentLength) }} 字）
            </span>
          </h4>
          <pre class="doc-detail__content">{{
            documentDetailRow?.contentPreview || '（没有取到文字）'
          }}</pre>
        </section>

        <section class="doc-detail__section">
          <h4 class="doc-detail__title">
            该文档产出的候选题
            <span v-if="documentDetailRow?.questionsTruncated" class="zk-text-secondary zk-text-sm">
              （仅显示前
              {{ (documentDetailRow?.questions || []).length }} 道，完整列表见「题目预览」）
            </span>
          </h4>
          <ul v-if="(documentDetailRow?.questions || []).length" class="doc-detail__questions">
            <li v-for="q in documentDetailRow.questions" :key="q.id" class="doc-detail__question">
              <span class="doc-detail__question-meta">
                #{{ q.id }} · {{ q.typeName || questionTypeLabel(q.questionType) }} ·
                {{ stageLabel(q.stage) }}
              </span>
              <span class="doc-detail__question-body">
                {{ plainText(q.content) || '（题干为空）' }}
              </span>
              <el-tag v-if="!q.answer" type="warning" size="small" effect="plain">无答案</el-tag>
            </li>
          </ul>
          <p v-else class="zk-text-secondary zk-text-sm">这份文档没有产出候选题。</p>
        </section>
      </div>

      <template #footer>
        <el-button
          v-if="documentDetailRow?.stagedCount"
          text
          type="primary"
          @click="goToDocumentQuestions"
        >
          到题目预览看这些题
        </el-button>
        <el-button type="primary" @click="documentDialog = false">关闭</el-button>
      </template>
    </ZkDrawer>
  </AppPage>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  CloseBold,
  Edit,
  FolderOpened,
  Loading,
  Plus,
  Promotion,
  Refresh,
  RefreshRight,
  Search,
  Select,
  Upload,
  UploadFilled,
} from '@element-plus/icons-vue'
import ZkDrawer from '@/components/ZkDrawer.vue'
import AppPage from '@/components/AppPage.vue'
import DataTable from '@/components/DataTable.vue'
import OcrCostPanel from '@/components/crawler/OcrCostPanel.vue'
import RichContent from '@/components/RichContent.vue'
import {
  crawlerDocumentDetail,
  crawlerDocumentPage,
  crawlerImportLocal,
  crawlerOcrStatus,
  crawlerQuestionPage,
  crawlerQuestionReview,
  crawlerRunTask,
  crawlerSourceCreate,
  crawlerSourceList,
  crawlerSourceToggle,
  crawlerSourceUpdate,
  crawlerStats,
  crawlerTaskPage,
  crawlerUpload,
} from '@/api/crawler'
import { listSubjects } from '@/api/system'

/**
 * 题库采集管理页（Console 壳，管理员）。
 *
 * 四条设计边界：
 * 1. **后端采集模块并行开发**：每个数据源都按「可能拿不到」处理——列表失败进
 *    DataTable 的错误态（含重试），辅助数据（科目、来源、OCR 状态）失败静默退化为空值，
 *    轮询类请求全部 silent，避免每 10~30 秒弹一条红条。
 * 2. **字段编码不确定**：stage / questionType / difficulty / grade 可能是数字也可能是
 *    字符串，所有展示都走 map + 原值兜底（取不到映射就显示原值），绝不出现空白单元格。
 * 3. **入库是唯一的写库动作**：PROMOTE 才会进正式题库，因此批量操作必须有确认弹窗 + 备注，
 *    且按钮只在有勾选时可用。
 * 4. **OCR 状态提前告知**：扫描卷/照片取不到字是配置问题而不是页面问题，
 *    所以把通道可用性直接摆在统计条下面，别让使用者跑到任务失败才反应过来。
 */
defineOptions({ name: 'CrawlerManage' })

// 清洗流水线：既是统计卡的口径，也是「阶段」筛选与标签文案的来源
const PIPELINE = [
  { key: 'RAW', label: '原始文档' },
  { key: 'PARSED', label: '已解析' },
  { key: 'CLEANED', label: '已清洗' },
  { key: 'TAGGED', label: '已打标' },
  { key: 'REVIEWED', label: '已复核' },
  { key: 'PROMOTED', label: '已入库' },
  { key: 'REJECTED', label: '已退回' },
]

const STAGE_LABELS = PIPELINE.reduce((acc, item) => ({ ...acc, [item.key]: item.label }), {})
const STAGE_TAG_TYPE = {
  RAW: 'info',
  PARSED: 'info',
  CLEANED: 'primary',
  TAGGED: 'primary',
  REVIEWED: 'warning',
  PROMOTED: 'success',
  REJECTED: 'danger',
}

const TYPE_LABELS = {
  1: '单选题',
  2: '多选题',
  3: '判断题',
  4: '简答题',
  5: '填空题',
  SINGLE: '单选题',
  SINGLE_CHOICE: '单选题',
  MULTIPLE: '多选题',
  MULTIPLE_CHOICE: '多选题',
  JUDGE: '判断题',
  TRUE_FALSE: '判断题',
  ESSAY: '简答题',
  SUBJECTIVE: '简答题',
  FILL_BLANK: '填空题',
}

const DIFF_LABELS = {
  1: '很易',
  2: '较易',
  3: '中等',
  4: '较难',
  5: '很难',
  EASY: '较易',
  MEDIUM: '中等',
  HARD: '较难',
}

const TASK_LABELS = { PENDING: '排队中', RUNNING: '进行中', SUCCESS: '已完成', FAILED: '失败' }
const TASK_TAG_TYPE = { PENDING: 'info', RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger' }

// 质量标记分色：这些标记都是「要人看一眼」的提醒，不是错误——
// 空白卷整卷无答案属于正常来源，标红会让人以为采集失败了
const FLAG_RULES = [
  [/dup|duplicate|repeat|重复/i, 'danger'],
  [/no[_\s-]?answer|missing|缺|无答案/i, 'warning'],
  [/html|tag|格式|symbol|乱码/i, 'primary'],
  [/length|short|truncat|截断|过短/i, 'info'],
]

// Cleaner 落的是英文码，页面上直接给中文说法；后端加新码时原样显示（见 flagLabel）
const FLAG_LABELS = {
  DUPLICATE: '重复题',
  SHORT_CONTENT: '内容过短',
  OPTIONS_MISMATCH: '选项与题型不符',
  IMG_MIRROR_FAILED: '图片未镜像',
  OCR_TEXT: 'OCR 取字',
  NO_ANSWER: '无答案',
}

const activeTab = ref('sources')

// 「识别与成本」面板：懒挂载（页签第一次点开才渲染），再次点开时刷新
const costPanelRef = ref(null)

// ===== 统计 =====
const stats = ref({})
const statsError = ref('')

function statValue(key) {
  // 「原始文档」读的是文档数（rawTotal）而不是 crawl_question.stage=RAW：
  // 题目进待审时已经是 CLEANED/TAGGED，RAW 阶段的题目行根本不存在，
  // 挂在 stage 计数上这张卡永远是 0，看起来像「一份文档都没采到」
  if (key === 'RAW') {
    const docs = Number(stats.value?.rawTotal)
    return Number.isFinite(docs) ? docs : 0
  }
  const v = stats.value?.[key]
  const num = Number(v)
  return Number.isFinite(num) ? num : 0
}

async function loadStats() {
  try {
    const data = await crawlerStats()
    // 后端字段大小写/命名可能有出入：先按原键取，再试大写键
    stats.value = data && typeof data === 'object' ? data : {}
    statsError.value = ''
  } catch (e) {
    statsError.value = e?.message || '阶段统计暂不可用'
  }
}

// ===== 科目（辅助数据，失败即空数组）=====
const subjectList = ref([])

async function loadSubjects() {
  try {
    const data = await listSubjects()
    subjectList.value = Array.isArray(data) ? data : data?.records || []
  } catch (e) {
    subjectList.value = []
  }
}

function subjectNameOf(id) {
  if (id === null || id === undefined || id === '') return '-'
  const hit = subjectList.value.find((item) => String(item.id) === String(id))
  return hit?.name || hit?.subjectName || String(id)
}

// ===== 题目预览 =====
// rawId：从「解析日志」的某份文档跳过来时带上，只看这份文档认出的题
const questionQuery = reactive({
  pageNum: 1,
  pageSize: 20,
  stage: null,
  subjectId: null,
  grade: null,
  rawId: null,
})
const questionRows = ref([])
const questionTotal = ref(0)
const questionLoading = ref(false)
const questionErr = ref('')
const reviewing = ref(false)

// 批量审核弹窗：入库不只是「确认一下」——还要能指定科目兜底、能看到逐条失败原因
const reviewDialog = ref(false)
const reviewAction = ref('PROMOTE')
const reviewIds = ref([])
const reviewSubjectId = ref(null)
const reviewNote = ref('')
const reviewResult = ref(null)

// 选项列表 = 内置编码 ∪ 当前页出现过的值：后端换编码时筛选条件不会失焦于「只剩内置值」
const stageOptions = computed(() =>
  uniqueOptions(
    PIPELINE.map((i) => ({ label: i.label, value: i.key })),
    questionRows.value.map((r) => r.stage),
    stageLabel,
  ),
)
const gradeOptions = computed(() =>
  uniqueOptions(
    [
      { label: '小学', value: 'PRIMARY' },
      { label: '初中', value: 'JUNIOR' },
      { label: '高中', value: 'SENIOR' },
    ],
    questionRows.value.flatMap((r) => [r.grade, r.gradeLevel]),
    (v) => v,
  ),
)

function uniqueOptions(known, seenValues, labelFn) {
  const map = new Map()
  known.forEach((item) => map.set(String(item.value), item))
  seenValues
    .filter((v) => v !== null && v !== undefined && v !== '')
    .forEach((v) => {
      if (!map.has(String(v))) map.set(String(v), { label: labelFn(v), value: v })
    })
  return [...map.values()]
}

async function loadQuestions() {
  questionLoading.value = true
  try {
    const data = await crawlerQuestionPage({
      pageNum: questionQuery.pageNum,
      pageSize: questionQuery.pageSize,
      stage: questionQuery.stage ?? undefined,
      subjectId: questionQuery.subjectId ?? undefined,
      grade: questionQuery.grade || undefined,
      rawId: questionQuery.rawId ?? undefined,
    })
    questionRows.value = data?.records || []
    questionTotal.value = data?.total || 0
    questionErr.value = ''
  } catch (e) {
    questionRows.value = []
    questionTotal.value = 0
    questionErr.value = e?.message || '采集题目加载失败，请稍后重试'
  } finally {
    questionLoading.value = false
  }
}

function searchQuestions() {
  questionQuery.pageNum = 1
  loadQuestions()
}

function resetQuestions() {
  questionQuery.pageNum = 1
  questionQuery.stage = null
  questionQuery.subjectId = null
  questionQuery.grade = null
  questionQuery.rawId = null
  loadQuestions()
}

/** 关掉「只看这一份文档」的过滤，回到常规待审列表 */
function clearDocumentFilter() {
  questionQuery.rawId = null
  questionQuery.pageNum = 1
  loadQuestions()
}

const documentFilterLabel = computed(() => {
  const name = documentDetailRow.value?.docName
  const id = questionQuery.rawId
  if (name && documentDetailRow.value?.id === id) return name
  return id == null ? '' : `#${id}`
})

function onQuestionPage(page) {
  questionQuery.pageNum = page
  loadQuestions()
}

function onQuestionSize(size) {
  questionQuery.pageSize = size
  questionQuery.pageNum = 1
  loadQuestions()
}

function openReview(selection, action) {
  const ids = selection.map((row) => row.id).filter((id) => id !== null && id !== undefined)
  if (!ids.length) {
    ElMessage.warning('请先勾选题目')
    return
  }
  reviewAction.value = action
  reviewIds.value = ids
  // 列表筛选已经选了科目时，兜底科目默认跟上——「筛数学 → 选一批 → 入库」是最常见的动线
  reviewSubjectId.value = questionQuery.subjectId ?? null
  reviewNote.value = ''
  reviewResult.value = null
  reviewDialog.value = true
}

const isPromoteReview = computed(() => reviewAction.value === 'PROMOTE')
const reviewErrors = computed(() => toArray(reviewResult.value?.errors))
const reviewSuccess = computed(() => Number(reviewResult.value?.successCount) || 0)
const reviewFail = computed(() => Number(reviewResult.value?.failCount) || 0)

async function submitReview() {
  reviewing.value = true
  try {
    const data = await crawlerQuestionReview({
      ids: reviewIds.value,
      action: reviewAction.value,
      note: reviewNote.value.trim() || null,
      subjectId: isPromoteReview.value ? (reviewSubjectId.value ?? null) : null,
    })
    const fail = Number(data?.failCount) || 0
    const ok = Number(data?.successCount) || 0
    if (fail > 0) {
      // 停在结果视图逐条展示原因：整批里「3 道成功 1 道缺科目」只弹一个成功号会误导
      reviewResult.value = data || {
        successCount: ok,
        failCount: fail,
        errors: ['未返回明细，请刷新列表查看'],
      }
      ElMessage.warning(`成功 ${ok} 道，未处理 ${fail} 道`)
    } else {
      ElMessage.success(isPromoteReview.value ? `已入库 ${ok} 道题` : `已退回 ${ok} 道题`)
      reviewDialog.value = false
    }
    await Promise.all([loadQuestions(), loadStats()])
  } catch (e) {
    ElMessage.error(e?.message || '审核提交失败，请稍后重试')
  } finally {
    reviewing.value = false
  }
}

// ===== 采集任务 =====
const taskQuery = reactive({ pageNum: 1, pageSize: 10 })
const taskRows = ref([])
const taskTotal = ref(0)
const taskLoading = ref(false)
const taskErr = ref('')

const isActiveTask = (row) =>
  ['RUNNING', 'PENDING'].includes(String(row?.status || '').toUpperCase())
const hasActiveTask = computed(() => taskRows.value.some(isActiveTask))

async function loadTasks({ silent = false } = {}) {
  if (!silent) taskLoading.value = true
  try {
    const data = await crawlerTaskPage({
      pageNum: taskQuery.pageNum,
      pageSize: taskQuery.pageSize,
      silent,
    })
    taskRows.value = data?.records || []
    taskTotal.value = data?.total || 0
    taskErr.value = ''
  } catch (e) {
    if (!silent) {
      taskRows.value = []
      taskTotal.value = 0
      taskErr.value = e?.message || '采集任务加载失败，请稍后重试'
    }
  } finally {
    if (!silent) taskLoading.value = false
  }
}

function onTaskPage(page) {
  taskQuery.pageNum = page
  loadTasks()
}

function onTaskSize(size) {
  taskQuery.pageSize = size
  taskQuery.pageNum = 1
  loadTasks()
}

// ===== 文档解析日志 =====
// 这页存在的理由：上传后「新文档 1 份、候选题 0 道」这种数字看不出问题出在哪一环。
// 逐文档留痕把「取字 → 解析 → 清洗」的断点直接摊开，不需要登服务器看日志。
const documentQuery = reactive({
  pageNum: 1,
  pageSize: 20,
  taskId: null,
  keyword: '',
  unresolvedOnly: true,
})
const documentRows = ref([])
const documentTotal = ref(0)
const documentLoading = ref(false)
const documentErr = ref('')

const documentDialog = ref(false)
const documentDetailLoading = ref(false)
const documentDetailRow = ref(null)

/** 刚发起采集、还想看它逐文档留痕的那个任务号；轮询到它结束为止 */
const pendingDocumentTask = ref(null)

async function loadDocuments({ silent = false } = {}) {
  if (!silent) documentLoading.value = true
  try {
    const data = await crawlerDocumentPage({
      pageNum: documentQuery.pageNum,
      pageSize: documentQuery.pageSize,
      taskId: documentQuery.taskId,
      keyword: documentQuery.keyword || undefined,
      unresolvedOnly: documentQuery.unresolvedOnly || undefined,
      silent,
    })
    documentRows.value = data?.records || []
    documentTotal.value = data?.total || 0
    documentErr.value = ''
  } catch (e) {
    if (!silent) {
      documentRows.value = []
      documentTotal.value = 0
      documentErr.value = e?.message || '解析日志加载失败，请稍后重试'
    }
  } finally {
    if (!silent) documentLoading.value = false
  }
}

function searchDocuments() {
  documentQuery.pageNum = 1
  pendingDocumentTask.value = null
  loadDocuments()
}

function onDocumentPage(page) {
  documentQuery.pageNum = page
  loadDocuments()
}

function onDocumentSize(size) {
  documentQuery.pageSize = size
  documentQuery.pageNum = 1
  loadDocuments()
}

/**
 * 从任务行「解析日志」或上传完成时下钻：带着 taskId 过滤，只看这一批文档。
 * taskId 非空时把轮询目标也指过去——文档是任务跑起来才一行行出现的，
 * 不跟着刷的话页面会停在「暂无文档解析记录」，看起来像没生效。
 */
function goToDocuments(taskId) {
  documentQuery.taskId = taskId ?? null
  documentQuery.pageNum = 1
  pendingDocumentTask.value = taskId ?? null
  activeTab.value = 'documents'
  loadDocuments()
  if (taskId != null) loadTasks({ silent: true })
}

function clearDocumentTask() {
  documentQuery.taskId = null
  pendingDocumentTask.value = null
  searchDocuments()
}

// 老数据（解析日志上线前采的）流水线没写过这几列，列表要区分「没留痕」和「留痕显示 0」。
// 以 parsedCount 为准：它是流水线每次必写的一个，charCount 早期被回填过、不可靠。
function hasTrace(row) {
  return row?.parsedCount !== null && row?.parsedCount !== undefined
}

/** 计数列：null 表示「当时没记录」显示为 -，0 才是真的 0（countOf 会把 null 当 0，这里不能用它） */
function numOrDash(val) {
  return val === null || val === undefined ? '-' : val
}

// 质量分档位：85 以上才算「可以直接用」——一份公式丢了的卷子落在 80 分上下，
// 那种情况必须显黄让人去看；60 以下基本得重采或换更强的识别通道
function qualityTone(score) {
  if (score >= 85) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

/**
 * OCR 置信度文案。
 * null 有两种完全不同的含义——文本层取字（本来就没有置信度）与 OCR 引擎没给分，
 * 都显示「-」会让人以为识别出了岔，所以分开说。
 */
function ocrConfidenceLabel(row) {
  if (!row) return '-'
  if (row.viaOcr !== 1) return '文本层取字，无置信度'
  if (row.ocrConfidence === null || row.ocrConfidence === undefined) return '引擎未返回'
  return `${Math.round(Number(row.ocrConfidence) * 1000) / 10}%`
}

/**
 * 通道名 → 人话。落库存引擎名（可与配置对照、可 group by），页面上说「本地/云端」，
 * 因为审核人关心的是「这次有没有花钱」，不是引擎型号。
 */
const CHANNEL_LABELS = { rapidocr: '本地', 'qwen-vl': '云端' }

function channelChips(row) {
  const raw = row?.ocrChannels
  if (!raw) return []
  return String(raw)
    .split(',')
    .map((name) => name.trim())
    .filter(Boolean)
    .map((name) => CHANNEL_LABELS[name] || name)
}

function channelChainLabel(row) {
  const chips = channelChips(row)
  const base = viaOcrLabel(row?.viaOcr)
  if (!chips.length) {
    return row?.viaOcr === 1 ? base : `${base}，未触发 OCR`
  }
  return `${base} → ${chips.join(' → ')}`
}

function formulaTooltip(row) {
  const candidates = Number(row?.formulaCandidates) || 0
  const recovered = Number(row?.formulaRecovered) || 0
  const local = Number(row?.formulaLocal) || 0
  const cloud = Number(row?.formulaCloud) || 0
  return `候选 ${candidates} 处 · 本地认出 ${local} · 云端认出 ${cloud} · 未插回 ${candidates - recovered}（认不出或找不到插入位置）`
}

/**
 * 详情里的逐层文案。
 * 「没留痕」「无候选」「有候选全没认出来」是三件事，合并显示会让人以为解析坏了。
 */
function formulaLayerText(row) {
  const candidates = row?.formulaCandidates
  if (candidates === null || candidates === undefined) return '未留痕（该列上线前采集）'
  if (Number(candidates) === 0) return '未发现内联公式片段'
  const recovered = Number(row?.formulaRecovered) || 0
  const local = Number(row?.formulaLocal) || 0
  const cloud = Number(row?.formulaCloud) || 0
  const cloudCalls = Number(row?.formulaCloudCalls) || 0
  const spent = cloudCalls ? ` · 云端调用 ${cloudCalls} 次` : ''
  return `候选 ${candidates} · 插回 ${recovered}（本地 ${local} · 云端 ${cloud}） · 未插回 ${candidates - recovered}${spent}`
}

const stagedOf = (row) => Number(row?.stagedCount) || 0

function viaOcrLabel(val) {
  if (val === 1 || val === '1' || val === true) return 'OCR 识别'
  if (val === 0 || val === '0' || val === false) return '文本层'
  return '未记录'
}

async function openDocument(row) {
  documentDetailRow.value = { ...row }
  documentDialog.value = true
  documentDetailLoading.value = true
  try {
    const data = await crawlerDocumentDetail(row.id)
    documentDetailRow.value = data || documentDetailRow.value
  } catch (e) {
    ElMessage.error(e?.message || '文档详情加载失败，请稍后重试')
  } finally {
    documentDetailLoading.value = false
  }
}

/** 详情里看到有题，就按该文档的 rawId 跳到题目预览——从「为什么没题」接着问「题长什么样」 */
function goToDocumentQuestions() {
  questionQuery.rawId = documentDetailRow.value?.id ?? null
  questionQuery.pageNum = 1
  documentDialog.value = false
  activeTab.value = 'questions'
  loadQuestions()
}

// ===== 来源管理 =====
const sourceRows = ref([])
const sourceLoading = ref(false)
const sourceErr = ref('')
const togglingId = ref(null)

const enabledSources = computed(() => sourceRows.value.filter((item) => isEnabled(item.enabled)))

async function loadSources() {
  sourceLoading.value = true
  try {
    const data = await crawlerSourceList()
    sourceRows.value = Array.isArray(data) ? data : data?.records || []
    sourceErr.value = ''
  } catch (e) {
    sourceRows.value = []
    sourceErr.value = e?.message || '采集来源加载失败，请稍后重试'
  } finally {
    sourceLoading.value = false
  }
}

function sourceNameOf(sourceId) {
  const hit = sourceRows.value.find((item) => String(item.id) === String(sourceId))
  return hit?.name || (sourceId == null ? '-' : `来源 #${sourceId}`)
}

// enabled 可能是 1/0、true/false 或 'true'：统一判真
function isEnabled(val) {
  return val === 1 || val === true || String(val) === 'true'
}

async function handleToggle(row, next) {
  togglingId.value = row.id
  try {
    const data = await crawlerSourceToggle(row.id)
    // 后端若回传新状态则以其为准，否则本地按操作结果翻转（避免整表刷新打断手感和勾选）
    row.enabled = typeof data === 'boolean' || typeof data === 'number' ? data : next
    ElMessage.success(isEnabled(row.enabled) ? '已启用' : '已停用')
  } catch (e) {
    ElMessage.error(e?.message || '启停失败，请稍后重试')
    loadSources()
  } finally {
    togglingId.value = null
  }
}

// ===== 来源表单 =====
const sourceDialog = ref(false)
const sourceFormRef = ref(null)
const saving = ref(false)
// 七期起来源只有「文档目录」一种：sourceType 由页面固定填 LOCAL_DIR，
// 后端也会拒绝其它值，因此表单里不再出现类型下拉
const defaultSourceForm = () => ({
  id: null,
  name: '',
  baseUrl: '',
  sourceType: 'LOCAL_DIR',
  licenseNote: '',
  defaultSubjectId: null,
  enabled: true,
  remark: '',
})
const sourceForm = ref(defaultSourceForm())

const sourceRules = {
  name: [{ required: true, message: '请输入来源名称', trigger: 'blur' }],
  baseUrl: [
    { required: true, message: '请输入文档目录的绝对路径', trigger: 'blur' },
    {
      pattern: /^([a-zA-Z]:[\\/]|\/).+$/,
      message: '请填写绝对路径，如 D:/题目资源/五年级 或 /data/docs/五年级',
      trigger: 'blur',
    },
  ],
}

function openSourceForm(row) {
  sourceForm.value = row
    ? {
        id: row.id,
        name: row.name ?? '',
        baseUrl: row.baseUrl ?? '',
        sourceType: 'LOCAL_DIR',
        licenseNote: row.licenseNote ?? '',
        defaultSubjectId: row.defaultSubjectId ?? null,
        enabled: isEnabled(row.enabled),
        remark: row.remark ?? '',
      }
    : defaultSourceForm()
  sourceFormRef.value?.clearValidate()
  sourceDialog.value = true
}

async function submitSource() {
  if (!(await sourceFormRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    const payload = { ...sourceForm.value }
    if (payload.id) {
      await crawlerSourceUpdate(payload.id, payload)
    } else {
      await crawlerSourceCreate(payload)
    }
    ElMessage.success('保存成功')
    sourceDialog.value = false
    await loadSources()
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

// ===== 重新采集（对已登记的目录再跑一次） =====
const runDialog = ref(false)
const runSourceId = ref(null)
const running = ref(false)

function openRunDialog() {
  if (!sourceRows.value.length) {
    loadSources()
  }
  // 只有一个启用来源时直接用它，省一次点击
  runSourceId.value = enabledSources.value.length === 1 ? enabledSources.value[0].id : null
  runDialog.value = true
}

function taskIdOf(data) {
  if (data === null || data === undefined) return ''
  if (typeof data === 'number' || typeof data === 'string') return String(data)
  return String(data.taskId ?? data.id ?? '')
}

async function submitRun() {
  running.value = true
  try {
    const data = await crawlerRunTask(runSourceId.value)
    const taskId = taskIdOf(data)
    ElMessage.success(taskId ? `采集任务已发起（任务号 ${taskId}）` : '采集任务已发起')
    runDialog.value = false
    goToTasks()
  } catch (e) {
    ElMessage.error(e?.message || '发起采集失败，请检查来源是否可用')
  } finally {
    running.value = false
  }
}

// ===== 本地目录导入 =====
const importDialog = ref(false)
const importFormRef = ref(null)
const importing = ref(false)
const importForm = reactive({ dirPath: '' })
const importRules = {
  dirPath: [
    { required: true, message: '请输入目录绝对路径', trigger: 'blur' },
    {
      pattern: /^([a-zA-Z]:[\\/]|\/).+$/,
      message: '请填写绝对路径，如 D:/crawler/xxx 或 /data/crawler/xxx',
      trigger: 'blur',
    },
  ],
}

function openImportDialog() {
  importForm.dirPath = ''
  importFormRef.value?.clearValidate()
  importDialog.value = true
}

async function submitImport() {
  if (!(await importFormRef.value.validate().catch(() => false))) return
  importing.value = true
  try {
    const data = await crawlerImportLocal(importForm.dirPath.trim())
    const tip = typeof data === 'string' && data ? data : '目录已加入采集队列'
    ElMessage.success(`${tip}，可在「采集任务」查看进度`)
    importDialog.value = false
    goToTasks()
  } catch (e) {
    ElMessage.error(e?.message || '导入失败，请确认目录存在且后端可访问')
  } finally {
    importing.value = false
  }
}

function goToTasks() {
  activeTab.value = 'tasks'
  taskQuery.pageNum = 1
  loadTasks()
  loadStats()
}

// ===== 文档上传 =====
// accept 与后端 ExtractorRegistry 支持的扩展名保持一致；后端仍会逐个校验，
// 这里只是让文件选择框默认只显示能用的类型，少一次「选了 .psd 再被拒」
const UPLOAD_ACCEPT = '.docx,.doc,.pdf,.png,.jpg,.jpeg,.bmp,.gif,.webp,.md,.txt,.json,.jsonl,.csv'
const UPLOAD_MAX_FILES = 20
const UPLOAD_MAX_MB = 50
// 与后端 spring.servlet.multipart.max-request-size 对齐（300MB）：
// 单份都合规、加起来超了，会被容器直接拒掉，页面只能拿到一个没有细节的 HTTP 错误
const UPLOAD_MAX_TOTAL_MB = 300

const uploadDialog = ref(false)
const uploadRef = ref(null)
const uploadFiles = ref([])
const uploadRejected = ref([])
const uploading = ref(false)

function openUploadDialog() {
  uploadFiles.value = []
  uploadRejected.value = []
  uploadRef.value?.clearFiles()
  uploadDialog.value = true
}

// el-upload 自己维护选中的文件列表（不绑 :file-list，避免受控/非受控两套状态互相回灌），
// 这里只把 on-change 回传的最新列表镜像一份，供按钮文案与提交使用
function onFileChange(_file, fileList) {
  uploadFiles.value = fileList
  uploadRejected.value = []
}

async function submitUpload() {
  const files = uploadFiles.value.map((item) => item.raw).filter(Boolean)
  if (!files.length) return
  if (files.length > UPLOAD_MAX_FILES) {
    uploadRejected.value = [`单次最多上传 ${UPLOAD_MAX_FILES} 份，当前选了 ${files.length} 份`]
    return
  }
  const tooBig = files.filter((file) => file.size > UPLOAD_MAX_MB * 1024 * 1024)
  if (tooBig.length) {
    // 先在本地拦：把 50MB 传上去再被后端拒掉，浪费的是使用者的等待时间
    uploadRejected.value = [
      `单份最大 ${UPLOAD_MAX_MB}MB，以下文档超限：${tooBig.map((f) => f.name).join('、')}`,
    ]
    return
  }
  const totalMB = files.reduce((sum, file) => sum + file.size, 0) / 1024 / 1024
  if (totalMB > UPLOAD_MAX_TOTAL_MB) {
    uploadRejected.value = [
      `单次合计最大 ${UPLOAD_MAX_TOTAL_MB}MB，当前约 ${totalMB.toFixed(1)}MB，请分批发起采集`,
    ]
    return
  }
  uploading.value = true
  try {
    const data = await crawlerUpload(files)
    const stored = Number(data?.stored) || 0
    const rejected = toArray(data?.rejected)
    const taskId = taskIdOf(data)
    const done = `${stored} 份文档已进入采集${taskId ? `（任务 ${taskId}）` : ''}`
    // 部分被拒时用 warning 而不是 success：只报「成功」会藏掉「有文档没进来」这件事
    if (rejected.length) {
      ElMessage.warning(
        `${done}；${rejected.length} 份被拒：${rejected[0]}${rejected.length > 1 ? ' 等' : ''}`,
      )
    } else {
      ElMessage.success(done)
    }
    uploadDialog.value = false
    uploadFiles.value = []
    uploadRef.value?.clearFiles()
    // 上传完落在「解析日志」而不是任务列表：任务行只有 1/0/0 三个数字，
    // 使用者真正要看的是这一批文档各自的取字与解析结果
    if (taskId != null) goToDocuments(taskId)
    else loadDocuments()
  } catch (e) {
    // 上传接口是 silent 的：拒绝原因逐条列在弹窗里比一条全局红条有用，
    // 尤其是「20 份里有 1 份格式不支持」这种部分失败
    uploadRejected.value = [e?.message || '上传失败，请稍后重试']
  } finally {
    uploading.value = false
  }
}

// ===== OCR 通道状态 =====
const OCR_MODES = { auto: '自动（离线优先）', rapid: '仅离线', qwen: '仅云端' }
const OCR_CHANNELS = [
  { key: 'rapidocr', label: '离线 OCR' },
  { key: 'qwen-vl', label: '云端 OCR' },
]

const ocrStatus = ref({})

const ocrChannels = computed(() =>
  OCR_CHANNELS.map((channel) => ({ ...channel, ok: ocrStatus.value[channel.key] === true })),
)

const ocrHint = computed(() => {
  const status = ocrStatus.value
  if (!status || !Object.keys(status).length) return '正在检测文字识别通道'
  const rapid = status.rapidocr === true
  const qwen = status['qwen-vl'] === true
  const mode = OCR_MODES[String(status.mode || 'auto').toLowerCase()] || status.mode
  if (!rapid && !qwen) {
    return '两个通道都不可用：扫描版 PDF 与试卷照片将无法取字。离线引擎需原生库可加载，云端需配置 ZHIKAO_QWEN_API_KEY'
  }
  if (String(status.mode) === 'qwen') return `已指定${mode}，走通义 Qwen-VL`
  if (String(status.mode) === 'rapid') return `已指定${mode}，离线引擎不可用时采集会直接失败`
  return rapid
    ? `${mode}：当前走本地离线识别，无需 API Key`
    : `${mode}：离线引擎不可用，改用云端 Qwen-VL`
})

async function loadOcrStatus() {
  try {
    ocrStatus.value = (await crawlerOcrStatus()) || {}
  } catch (e) {
    // silent：状态条只是提示，拉不到就当未知，不打断页面
    ocrStatus.value = {}
  }
}

// ===== 展示层兜底函数：所有字段都可能在后端换实现时缺失或改编码 =====
function pickLabel(map, val, fallbackFn) {
  if (val === null || val === undefined || val === '') return '-'
  const hit = map[String(val).toUpperCase()] ?? map[String(val)]
  if (hit) return hit
  return fallbackFn ? fallbackFn(val) : String(val)
}

const stageLabel = (val) => pickLabel(STAGE_LABELS, val)
const stageType = (val) => STAGE_TAG_TYPE[String(val ?? '').toUpperCase()] || 'info'
const questionTypeLabel = (val) => pickLabel(TYPE_LABELS, val)
const difficultyLabel = (val) => pickLabel(DIFF_LABELS, val)
const taskStatusLabel = (val) => pickLabel(TASK_LABELS, val)
const taskStatusType = (val) => TASK_TAG_TYPE[String(val ?? '').toUpperCase()] || 'info'

function difficultyType(val) {
  const key = String(val ?? '').toUpperCase()
  if (['1', '2', 'EASY', 'VERY_EASY'].includes(key)) return 'success'
  if (['4', '5', 'HARD', 'VERY_HARD'].includes(key)) return 'danger'
  return 'warning'
}

function gradeText(row) {
  return (
    [row.gradeLevel, row.grade]
      .filter((v) => v !== null && v !== undefined && v !== '')
      .join(' · ') || '-'
  )
}

function countOf(val) {
  const num = Number(val)
  return Number.isFinite(num) ? num : '-'
}

function flagType(flag) {
  const hit = FLAG_RULES.find(([reg]) => reg.test(String(flag)))
  return hit ? hit[1] : 'info'
}

/** 质量标记显示名：认得的后端码给中文，认不出的原样显示，避免新标记在页面上变成空白 */
function flagLabel(flag) {
  const code = String(flag ?? '').trim()
  return FLAG_LABELS[code.toUpperCase()] || code
}

// 后端可能给数组，也可能给 JSON 串或逗号分隔串
function toArray(val) {
  if (val === null || val === undefined || val === '') return []
  if (Array.isArray(val))
    return val.map((item) => (typeof item === 'object' ? JSON.stringify(item) : String(item)))
  const parsed = tryJson(val)
  if (Array.isArray(parsed)) return parsed.map((item) => String(item))
  return String(val)
    .split(/[,;|]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function tryJson(val) {
  if (typeof val !== 'string') return undefined
  const text = val.trim()
  if (!/^[[{]/.test(text)) return undefined
  try {
    return JSON.parse(text)
  } catch (e) {
    return undefined
  }
}

// 采集来的题干普遍带 HTML：列表只展示一行纯文本
function plainText(val) {
  if (val === null || val === undefined) return ''
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;|&#39;/g, '"')
    .replace(/\s+/g, ' ')
    .trim()
}

// 详情弹层用：原样保留 HTML / 公式，渲染与净化由 RichContent 负责
function rawText(val) {
  if (val === null || val === undefined) return ''
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function optionsOf(raw) {
  const out = []
  if (raw === null || raw === undefined || raw === '') return out
  const val = tryJson(raw) ?? raw
  const letter = (i) => String.fromCharCode(65 + i)
  if (Array.isArray(val)) {
    val.forEach((item, i) => {
      if (item && typeof item === 'object') {
        out.push({
          key: String(item.key ?? item.label ?? item.option ?? letter(i)),
          // 选项文本交给 RichContent 渲染，这里保留原 HTML（含表格 / /api/file 图 / $公式$）
          text: rawText(item.content ?? item.text ?? item.value ?? ''),
        })
      } else {
        const text = String(item)
        const m = /^([A-H])[\.\、:：)）\s]+(.*)$/.exec(text.trim())
        out.push(m ? { key: m[1], text: rawText(m[2]) } : { key: letter(i), text: rawText(text) })
      }
    })
    return out
  }
  if (val && typeof val === 'object') {
    Object.entries(val).forEach(([k, v]) => out.push({ key: k, text: rawText(v) }))
    return out
  }
  String(val)
    .split(/\r?\n|;/)
    .map((s) => s.trim())
    .filter(Boolean)
    .forEach((line, i) => {
      const m = /^([A-H])[\.\、:：)）\s]+(.*)$/.exec(line)
      out.push(m ? { key: m[1], text: rawText(m[2]) } : { key: letter(i), text: rawText(line) })
    })
  return out
}

// ===== 轮询 =====
let statsTimer = null
let taskTimer = null

function startTaskPolling() {
  stopTaskPolling()
  // 10s：只在「任务」页签且有进行中任务时轮询，静默失败（不打断用户）
  taskTimer = setInterval(() => {
    if (activeTab.value === 'tasks' && hasActiveTask.value) {
      loadTasks({ silent: true })
      return
    }
    // 刚上传完停在「解析日志」时，逐文档留痕是任务跑起来才陆续出现的：
    // 跟着轮询到该任务结束为止，然后拉一次最终结果并停手，不做无休止的刷新
    if (activeTab.value === 'documents' && pendingDocumentTask.value) {
      loadTasks({ silent: true })
      const stillRunning = taskRows.value.some(
        (task) => String(task.id) === String(pendingDocumentTask.value) && isActiveTask(task),
      )
      if (stillRunning) {
        loadDocuments({ silent: true })
      } else {
        pendingDocumentTask.value = null
        loadDocuments({ silent: true })
        loadStats()
      }
    }
  }, 10000)
}

function stopTaskPolling() {
  if (taskTimer) {
    clearInterval(taskTimer)
    taskTimer = null
  }
}

onMounted(() => {
  loadStats()
  loadSubjects()
  loadSources()
  loadQuestions()
  loadOcrStatus()
  statsTimer = setInterval(loadStats, 30000)
  startTaskPolling()
})

// 卸载清定时器：定时器挂在 window 上，路由切走不清会持续打后端
onBeforeUnmount(() => {
  if (statsTimer) {
    clearInterval(statsTimer)
    statsTimer = null
  }
  stopTaskPolling()
})

// 切页签按需加载：来源列表已有数据就不重复拉
watch(activeTab, (tab) => {
  if (tab === 'tasks') {
    if (!taskRows.value.length) loadTasks()
  } else if (tab === 'analytics') {
    // 面板首次激活自己会拉（lazy 挂载时的 onMounted）；再次点回来要刷新，
    // 否则「刚采完一批文档」与「上一批的比率」会对不上
    costPanelRef.value?.reload()
  } else if (tab === 'documents') {
    // 解析日志由下钻入口自己触发，这里只兜住「直接点页签」的情况
    if (!documentRows.value.length) loadDocuments()
  } else if (tab === 'sources' && !sourceRows.value.length) {
    loadSources()
  }
})

function refreshAll() {
  loadStats()
  loadOcrStatus()
  if (activeTab.value === 'questions') loadQuestions()
  else if (activeTab.value === 'tasks') loadTasks()
  else if (activeTab.value === 'analytics') costPanelRef.value?.reload()
  else if (activeTab.value === 'documents') loadDocuments()
  else loadSources()
}

function sourceAction(c) {
  if (c === 'upload') openUploadDialog()
  if (c === 'directory') openImportDialog()
  if (c === 'run') openRunDialog()
}
</script>
<style scoped>
.crawler-workspace {
  padding: 0 20px 20px;
}
.crawler-workspace :deep(.el-tabs__header) {
  padding-top: 6px;
}
.crawler-workspace :deep(.data-table) {
  border: 0;
}
.stage-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 16px;
  padding: 20px 0;
}
.stage-stats__card {
  padding: 20px;
  border: 1px solid #e6e9ef;
  border-radius: 5px;
}
.ocr-status {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 20px 0;
  font-size: 12px;
}
.control-full {
  width: 100%;
}
.filter-control {
  width: 160px;
}
.control-hint {
  display: block;
  line-height: 1.8;
  margin-top: 8px;
}
.doc-error,
.pre-wrap {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.candidate-question {
  line-height: 1.8;
}
@media (max-width: 650px) {
  .crawler-workspace {
    padding: 0 12px 12px;
  }
}
</style>
