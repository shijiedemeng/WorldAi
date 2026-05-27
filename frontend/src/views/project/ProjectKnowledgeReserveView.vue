<template>
  <div class="admin-page project-knowledge-page">
    <PageHeader title="项目储备库">
      <el-button @click="loadData">刷新</el-button>
      <el-button type="success" @click="openSemanticDialog">语言搜索</el-button>
      <el-button type="primary" @click="openCreateDialog">新增知识</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" :model="filters" label-position="top">
        <el-form-item label="项目">
          <el-select v-model="filters.projectCode" clearable filterable placeholder="全部项目" class="w-full">
            <el-option v-for="project in projects" :key="project.projectCode" :label="project.projectName" :value="project.projectCode">
              <span>{{ project.projectName }}</span>
              <small class="option-code">{{ project.projectCode }}</small>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.knowledgeType" clearable placeholder="全部类型" class="w-full">
            <el-option v-for="opt in projectKnowledgeTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" class="w-full">
            <el-option v-for="opt in projectKnowledgeStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="向量状态">
          <el-select v-model="filters.vectorStatus" clearable placeholder="全部状态" class="w-full">
            <el-option v-for="opt in projectKnowledgeVectorStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="标题 / 描述 / 内容" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>储备知识</strong>
          <span>共 {{ pageData.total }} 条</span>
        </div>
      </div>
      <el-table :data="pageData.items" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="projectCode" label="项目" width="110" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">{{ projectKnowledgeTypeLabelMap[row.knowledgeType] || row.knowledgeType }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="simpleDesc" label="简单描述" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag>{{ projectKnowledgeStatusLabelMap[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="向量" width="120">
          <template #default="{ row }">
            <el-tag :type="vectorStatusTagType(row.vectorStatus)">
              {{ projectKnowledgeVectorStatusLabelMap[row.vectorStatus] || row.vectorStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="切片" width="80">
          <template #default="{ row }">{{ row.vectorChunkCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="来源需求" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceRequirementNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">详情</el-button>
            <el-button link type="warning" :loading="isKnowledgeOrganizing(row) || organizingId === row.id" :disabled="isKnowledgeOrganizing(row)" @click="handleOrganize(row)">
              {{ isKnowledgeOrganizing(row) ? '整理中' : 'AI整理' }}
            </el-button>
            <el-button link type="success" :disabled="isKnowledgeOrganizing(row) || !row.vectorDirtyFlag || !hasVectorizableContent(row)" :loading="confirmingId === row.id" @click="handleConfirm(row)">确认入库</el-button>
            <el-button link type="danger" :disabled="isKnowledgeOrganizing(row)" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.pageSize"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="pageData.total"
          @change="loadData"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增项目储备知识' : `储备知识 #${currentId}`"
      width="980px"
      destroy-on-close
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <el-form :model="form" label-position="top" class="knowledge-form">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="项目" required>
              <el-select v-model="form.projectCode" :disabled="dialogMode === 'edit' || selectedItemOrganizing" filterable placeholder="选择项目" class="w-full">
                <el-option v-for="project in projects" :key="project.projectCode" :label="project.projectName" :value="project.projectCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类型" required>
              <el-select v-model="form.knowledgeType" :disabled="selectedItemOrganizing" class="w-full">
                <el-option v-for="opt in projectKnowledgeTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" :disabled="selectedItemOrganizing" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="AI整理配置">
              <el-select v-model="form.aiSettingKey" :disabled="selectedItemOrganizing" clearable filterable placeholder="选择文本模型" class="w-full">
                <el-option v-for="item in textAiSettings" :key="item.settingKey" :label="aiSettingLabel(item)" :value="item.settingKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="向量配置">
              <el-select v-model="form.embeddingSettingKey" :disabled="selectedItemOrganizing" clearable filterable placeholder="选择 embedding 模型" class="w-full">
                <el-option v-for="item in embeddingAiSettings" :key="item.settingKey" :label="aiSettingLabel(item)" :value="item.settingKey" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="文档标准">
              <el-tree-select
                v-model="form.documentIdList"
                :data="documentTreeSelectOptions"
                :props="documentTreeSelectProps"
                check-on-click-node
                check-strictly
                class="w-full"
                collapse-tags
                collapse-tags-tooltip
                clearable
                default-expand-all
                :disabled="selectedItemOrganizing"
                filterable
                multiple
                node-key="value"
                placeholder="AI整理时可参考"
                show-checkbox
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简单描述">
              <el-input v-model="form.simpleDesc" :disabled="selectedItemOrganizing" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原始详细内容">
              <div class="knowledge-text-editor">
                <div class="knowledge-text-editor__toolbar">
                  <span>{{ detailContentStats }}</span>
                  <div class="knowledge-text-editor__actions">
                    <el-button size="small" text type="primary" :disabled="selectedItemOrganizing" @click="detailEditorVisible = true">全屏编辑</el-button>
                    <el-button size="small" text type="danger" :disabled="selectedItemOrganizing || !form.detailContent" @click="form.detailContent = ''">清空</el-button>
                  </div>
                </div>
                <el-input
                  v-model="form.detailContent"
                  class="knowledge-text-editor__input"
                  type="textarea"
                  :disabled="selectedItemOrganizing"
                  :rows="14"
                  resize="vertical"
                  placeholder="填写原始问题、处理过程、回执内容、结论等纯文本。AI 整理会基于这里的内容处理。"
                />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="AI整理结果 / 确认内容">
              <el-input v-model="form.confirmedContent" :disabled="selectedItemOrganizing" type="textarea" :rows="12" resize="vertical" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert
        v-if="selectedItem"
        class="knowledge-status-alert"
        type="info"
        show-icon
        :closable="false"
        :title="`当前状态：${projectKnowledgeStatusLabelMap[selectedItem.status]} / 向量：${projectKnowledgeVectorStatusLabelMap[selectedItem.vectorStatus]}`"
        :description="selectedItem.errorMessage || 'AI整理可以多次执行；确认入库会生成 embedding 并写入 Milvus Lite。'"
      />

      <div v-if="similarItems.length" class="similar-panel">
        <div class="similar-panel__title">相似知识</div>
        <el-table :data="similarItems" size="small">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="simpleDesc" label="描述" min-width="220" show-overflow-tooltip />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ projectKnowledgeTypeLabelMap[row.knowledgeType] || row.knowledgeType }}</template>
          </el-table-column>
          <el-table-column label="分数" width="100">
            <template #default="{ row }">{{ row.score == null ? '-' : row.score.toFixed(4) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="form.selectedSimilarKnowledgeId = row.id">选中</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="dialogMode === 'edit'" :disabled="selectedItemOrganizing" :loading="similarLoading" @click="handleSimilar">查找相似</el-button>
        <el-button v-if="dialogMode === 'edit'" type="warning" :loading="selectedItemOrganizing || organizingId === currentId" :disabled="selectedItemOrganizing" @click="handleOrganizeSelected">
          {{ selectedItemOrganizing ? 'AI整理中' : 'AI整理' }}
        </el-button>
        <el-button v-if="dialogMode === 'edit'" type="success" :disabled="selectedItemOrganizing || !selectedItem?.vectorDirtyFlag" :loading="confirmingId === currentId" @click="handleConfirmSelected">确认入库</el-button>
        <el-button type="primary" :disabled="selectedItemOrganizing" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailEditorVisible"
      title="编辑原始详细内容"
      width="78vw"
      append-to-body
      destroy-on-close
    >
      <div class="knowledge-full-editor">
        <div class="knowledge-full-editor__meta">{{ detailContentStats }}</div>
        <TextCodeEditor
          v-model="form.detailContent"
          autofocus
          height="64vh"
          min-height="520px"
          placeholder="填写原始详细内容"
          :readonly="selectedItemOrganizing"
        />
      </div>
      <template #footer>
        <el-button @click="detailEditorVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="semanticDialogVisible"
      title="语言搜索"
      width="1120px"
      append-to-body
      destroy-on-close
    >
      <div class="semantic-search">
        <div class="semantic-search__header">
          <strong>向量语义匹配</strong>
          <span>使用自然语言查询向量库，结果按知识条目聚合综合匹配度。</span>
        </div>
        <el-form class="semantic-search__form" label-position="top">
          <el-form-item label="项目" required>
            <el-select v-model="semanticProjectCode" filterable placeholder="选择项目" class="w-full">
              <el-option v-for="project in projects" :key="project.projectCode" :label="project.projectName" :value="project.projectCode">
                <span>{{ project.projectName }}</span>
                <small class="option-code">{{ project.projectCode }}</small>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="semanticKnowledgeType" clearable placeholder="全部类型" class="w-full">
              <el-option v-for="opt in projectKnowledgeTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="向量配置" required>
            <el-select v-model="semanticEmbeddingSettingKey" filterable placeholder="选择向量模型" class="w-full">
              <el-option v-for="item in embeddingAiSettings" :key="item.settingKey" :label="aiSettingLabel(item)" :value="item.settingKey" />
            </el-select>
          </el-form-item>
          <el-form-item label="返回数量">
            <el-input-number v-model="semanticLimit" :min="1" :max="20" class="w-full" />
          </el-form-item>
          <el-form-item label="最低综合匹配值">
            <el-input-number v-model="semanticMinMatchScore" :min="0" :max="100" :step="5" class="w-full" />
          </el-form-item>
          <el-form-item class="semantic-search__query" label="搜索内容" required>
            <el-input
              v-model="semanticQuery"
              clearable
              placeholder="例如：并行工作流暂停后如何恢复执行"
              @keyup.enter="handleSemanticSearch"
            />
          </el-form-item>
        </el-form>
      </div>

      <div class="semantic-result-panel">
        <div class="semantic-result-panel__title">
          <strong>搜索结果</strong>
          <span>{{ semanticResults.length ? `共 ${semanticResults.length} 条` : '暂无结果' }}</span>
        </div>
        <el-table :data="semanticResults" v-loading="semanticLoading" stripe max-height="460">
          <el-table-column label="综合匹配度" width="130">
            <template #default="{ row }">
              <el-tag type="success">{{ semanticScore(row) }}%</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最佳分片" width="110">
            <template #default="{ row }">{{ scoreText(row.bestChunkScore) }}%</template>
          </el-table-column>
          <el-table-column label="命中分片" width="90">
            <template #default="{ row }">{{ row.matchedChunkCount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="simpleDesc" label="描述" min-width="240" show-overflow-tooltip />
          <el-table-column prop="content" label="最佳匹配内容" min-width="320" show-overflow-tooltip />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!row.id" @click="openSemanticResult(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="semanticDialogVisible = false">关闭</el-button>
        <el-button @click="clearSemanticSearch">清空</el-button>
        <el-button type="success" :loading="semanticLoading" @click="handleSemanticSearch">语义搜索</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { fetchMarkdownDocumentTree } from '@/api/markdownDocument'
import {
  confirmProjectKnowledge,
  createProjectKnowledge,
  deleteProjectKnowledge,
  fetchProjectKnowledge,
  fetchProjectKnowledgePage,
  organizeProjectKnowledge,
  searchProjectKnowledge,
  searchSimilarProjectKnowledge,
  updateProjectKnowledge,
} from '@/api/projectKnowledge'
import { fetchProjects } from '@/api/project'
import { fetchAiSettings } from '@/api/system'
import type { MarkdownDocumentTreeNode } from '@/types/markdownDocument'
import type { Project } from '@/types/project'
import type {
  ProjectKnowledge,
  ProjectKnowledgePage,
  ProjectKnowledgeSearchItem,
  ProjectKnowledgeTypeValue,
} from '@/types/projectKnowledge'
import type { AiModelSetting } from '@/types/system'
import {
  projectKnowledgeStatusLabelMap,
  projectKnowledgeStatusOptions,
  projectKnowledgeTypeLabelMap,
  projectKnowledgeTypeOptions,
  projectKnowledgeVectorStatusLabelMap,
  projectKnowledgeVectorStatusOptions,
} from '@/types/options'
import { buildMarkdownDocumentTreeSelectOptions } from '@/utils/markdownDocumentTree'

const TextCodeEditor = defineAsyncComponent(() => import('@/components/common/TextCodeEditor.vue'))

interface KnowledgeForm {
  projectCode: string
  knowledgeType: ProjectKnowledgeTypeValue
  title: string
  simpleDesc: string
  detailContent: string
  confirmedContent: string
  aiSettingKey: string
  embeddingSettingKey: string
  documentIdList: string[]
  selectedSimilarKnowledgeId?: number
}

const route = useRoute()
const projects = ref<Project[]>([])
const aiSettings = ref<AiModelSetting[]>([])
const documentTreeNodes = ref<MarkdownDocumentTreeNode[]>([])
const loading = ref(false)
const saving = ref(false)
const organizingId = ref<number>()
const confirmingId = ref<number>()
const similarLoading = ref(false)
const dialogVisible = ref(false)
const detailEditorVisible = ref(false)
const semanticDialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const currentId = ref<number>()
const selectedItem = ref<ProjectKnowledge>()
const similarItems = ref<ProjectKnowledgeSearchItem[]>([])
const semanticLoading = ref(false)
const semanticProjectCode = ref('')
const semanticKnowledgeType = ref('')
const semanticQuery = ref('')
const semanticEmbeddingSettingKey = ref('')
const semanticLimit = ref(10)
const semanticMinMatchScore = ref<number | undefined>()
const semanticResults = ref<ProjectKnowledgeSearchItem[]>([])
let knowledgePollTimer: number | undefined
const filters = ref({
  projectCode: typeof route.query.projectCode === 'string' ? route.query.projectCode : '',
  knowledgeType: '',
  status: '',
  vectorStatus: '',
  keyword: '',
  page: 1,
  pageSize: 10,
})
const pageData = ref<ProjectKnowledgePage>({
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
  totalPages: 0,
})
const form = ref<KnowledgeForm>(emptyForm())
const documentTreeSelectProps = {
  children: 'children',
  disabled: 'disabled',
  label: 'label',
  value: 'value',
}
const documentTreeSelectOptions = computed(() => buildMarkdownDocumentTreeSelectOptions(documentTreeNodes.value))
const enabledAiSettings = computed(() => aiSettings.value.filter((item) => item.enabledFlag))
const textAiSettings = computed(() => enabledAiSettings.value.filter((item) => modelPurpose(item) === 'LANGUAGE'))
const embeddingAiSettings = computed(() => enabledAiSettings.value.filter((item) => modelPurpose(item) === 'VECTOR'))
const selectedItemOrganizing = computed(() => selectedItem.value?.status === 'AI_ORGANIZING')
const detailContentStats = computed(() => {
  const content = form.value.detailContent || ''
  const lines = content ? content.split(/\r\n|\r|\n/).length : 0
  return `${lines} 行 / ${content.length} 字`
})

onMounted(async () => {
  await Promise.all([loadOptions(), loadData()])
})

onBeforeUnmount(() => {
  stopKnowledgePolling()
})

async function loadOptions() {
  const [projectRows, aiRows, docRows] = await Promise.all([
    fetchProjects(),
    fetchAiSettings({ enabledFlag: true }),
    fetchMarkdownDocumentTree(),
  ])
  projects.value = projectRows
  aiSettings.value = aiRows
  documentTreeNodes.value = docRows
  if (!semanticEmbeddingSettingKey.value) {
    semanticEmbeddingSettingKey.value = defaultEmbeddingSettingKey()
  }
  if (!semanticProjectCode.value) {
    semanticProjectCode.value = filters.value.projectCode || ''
  }
}

async function loadData() {
  loading.value = true
  try {
    pageData.value = await fetchProjectKnowledgePage({
      projectCode: filters.value.projectCode || undefined,
      knowledgeType: filters.value.knowledgeType as ProjectKnowledgeTypeValue || undefined,
      status: filters.value.status as never || undefined,
      vectorStatus: filters.value.vectorStatus as never || undefined,
      keyword: filters.value.keyword || undefined,
      page: filters.value.page,
      pageSize: filters.value.pageSize,
    })
    if (currentId.value) {
      const latestSelected = pageData.value.items.find((item) => item.id === currentId.value)
      if (latestSelected) {
        selectedItem.value = latestSelected
        form.value.confirmedContent = latestSelected.confirmedContent || latestSelected.organizedContent || form.value.confirmedContent
      }
    }
    if (pageData.value.items.some((item) => item.status === 'AI_ORGANIZING')) {
      startKnowledgePolling()
    } else {
      stopKnowledgePolling()
    }
  } finally {
    loading.value = false
  }
}

function startKnowledgePolling() {
  if (knowledgePollTimer) {
    return
  }
  knowledgePollTimer = window.setInterval(() => {
    if (!pageData.value.items.some((item) => item.status === 'AI_ORGANIZING')) {
      stopKnowledgePolling()
      return
    }
    void loadData()
  }, 3000)
}

function stopKnowledgePolling() {
  if (!knowledgePollTimer) {
    return
  }
  window.clearInterval(knowledgePollTimer)
  knowledgePollTimer = undefined
}

function handleSearch() {
  filters.value.page = 1
  loadData()
}

async function handleSemanticSearch() {
  if (!semanticProjectCode.value) {
    ElMessage.warning('请先选择项目，再进行语言搜索')
    return
  }
  if (!semanticQuery.value.trim()) {
    ElMessage.warning('请输入语言搜索内容')
    return
  }
  if (!semanticEmbeddingSettingKey.value) {
    ElMessage.warning('请选择向量配置')
    return
  }
  semanticLoading.value = true
  try {
    const result = await searchProjectKnowledge({
      projectCode: semanticProjectCode.value,
      query: semanticQuery.value.trim(),
      knowledgeType: semanticKnowledgeType.value as ProjectKnowledgeTypeValue || undefined,
      embeddingSettingKey: semanticEmbeddingSettingKey.value,
      limit: semanticLimit.value,
      minMatchScore: semanticMinMatchScore.value || undefined,
    })
    semanticResults.value = result.items
    if (!result.items.length) {
      ElMessage.info('向量库没有匹配到储备知识')
    }
  } finally {
    semanticLoading.value = false
  }
}

function clearSemanticSearch() {
  semanticQuery.value = ''
  semanticResults.value = []
}

async function openSemanticDialog() {
  await refreshAiSettings()
  semanticProjectCode.value = semanticProjectCode.value || filters.value.projectCode || ''
  semanticKnowledgeType.value = semanticKnowledgeType.value || filters.value.knowledgeType || ''
  semanticEmbeddingSettingKey.value = semanticEmbeddingSettingKey.value || defaultEmbeddingSettingKey()
  semanticDialogVisible.value = true
}

function resetFilters() {
  filters.value = {
    projectCode: '',
    knowledgeType: '',
    status: '',
    vectorStatus: '',
    keyword: '',
    page: 1,
    pageSize: 10,
  }
  loadData()
}

async function refreshAiSettings() {
  aiSettings.value = await fetchAiSettings({ enabledFlag: true }).catch(() => aiSettings.value)
}

async function openCreateDialog() {
  await refreshAiSettings()
  dialogMode.value = 'create'
  currentId.value = undefined
  selectedItem.value = undefined
  similarItems.value = []
  detailEditorVisible.value = false
  form.value = emptyForm()
  form.value.projectCode = filters.value.projectCode || ''
  form.value.aiSettingKey = textAiSettings.value[0]?.settingKey || ''
  form.value.embeddingSettingKey = defaultEmbeddingSettingKey()
  dialogVisible.value = true
}

async function openEditDialog(row: ProjectKnowledge) {
  await refreshAiSettings()
  dialogMode.value = 'edit'
  currentId.value = row.id
  selectedItem.value = row
  similarItems.value = []
  detailEditorVisible.value = false
  form.value = {
    projectCode: row.projectCode,
    knowledgeType: row.knowledgeType,
    title: row.title,
    simpleDesc: row.simpleDesc || '',
    detailContent: row.detailContent || '',
    confirmedContent: row.confirmedContent || row.organizedContent || '',
    aiSettingKey: row.aiSettingKey || '',
    embeddingSettingKey: row.embeddingSettingKey || '',
    documentIdList: csvToList(row.documentIds),
    selectedSimilarKnowledgeId: row.selectedSimilarKnowledgeId,
  }
  dialogVisible.value = true
}

async function openSemanticResult(row: ProjectKnowledgeSearchItem) {
  if (!row.id) {
    return
  }
  const detail = await fetchProjectKnowledge(row.id)
  await openEditDialog(detail)
}

async function handleSave() {
  if (selectedItemOrganizing.value) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return null
  }
  if (!form.value.projectCode || !form.value.title) {
    ElMessage.warning('项目和标题不能为空')
    return null
  }
  saving.value = true
  try {
    const payload = buildPayload()
    const saved = dialogMode.value === 'create'
      ? await createProjectKnowledge(payload)
      : await updateProjectKnowledge(currentId.value!, payload)
    selectedItem.value = saved
    currentId.value = saved.id
    dialogMode.value = 'edit'
    ElMessage.success('保存成功')
    await loadData()
    return saved
  } finally {
    saving.value = false
  }
}

async function handleOrganize(row: ProjectKnowledge) {
  if (isKnowledgeOrganizing(row)) {
    return
  }
  if (!await confirmReorganizeIfNeeded(row)) {
    return
  }
  organizingId.value = row.id
  try {
    await organizeProjectKnowledge(row.id, {
      aiSettingKey: row.aiSettingKey,
      documentIds: row.documentIds,
      selectedSimilarKnowledgeId: row.selectedSimilarKnowledgeId,
    })
    ElMessage.success('AI整理任务已提交，完成后会自动刷新')
    await loadData()
    startKnowledgePolling()
  } finally {
    organizingId.value = undefined
  }
}

async function handleOrganizeSelected() {
  if (selectedItemOrganizing.value) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return
  }
  if (!await confirmReorganizeIfNeeded(selectedItem.value, form.value.confirmedContent)) {
    return
  }
  const saved = await handleSave()
  if (!saved) {
    return
  }
  if (!currentId.value) {
    return
  }
  organizingId.value = currentId.value
  try {
    const updated = await organizeProjectKnowledge(currentId.value, {
      aiSettingKey: form.value.aiSettingKey,
      documentIds: listToCsv(form.value.documentIdList),
      selectedSimilarKnowledgeId: form.value.selectedSimilarKnowledgeId,
    })
    selectedItem.value = updated
    if (updated.status === 'AI_ORGANIZING') {
      ElMessage.success('AI整理任务已提交，完成后会自动刷新')
      startKnowledgePolling()
      return
    }
    form.value.confirmedContent = updated.confirmedContent || updated.organizedContent || ''
    ElMessage.success('AI整理完成')
    await loadData()
  } finally {
    organizingId.value = undefined
  }
}

async function confirmReorganizeIfNeeded(item?: ProjectKnowledge | null, fallbackContent?: string) {
  if (!hasOrganizedKnowledge(item, fallbackContent)) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      '当前记录已经有 AI 整理结果，再次整理会覆盖现有整理内容和人工微调内容，确认继续？',
      '确认重新 AI 整理',
      {
        type: 'warning',
        confirmButtonText: '重新整理',
        cancelButtonText: '取消',
      },
    )
    return true
  } catch {
    return false
  }
}

function hasOrganizedKnowledge(item?: ProjectKnowledge | null, fallbackContent?: string) {
  return Boolean(
    item?.status === 'AI_READY'
    || item?.status === 'CONFIRMED'
    || item?.organizedContent?.trim()
    || item?.confirmedContent?.trim()
    || fallbackContent?.trim(),
  )
}

async function handleConfirm(row: ProjectKnowledge) {
  if (isKnowledgeOrganizing(row)) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return
  }
  if (!row.embeddingSettingKey) {
    ElMessage.warning('请先选择向量配置（Embedding 模型）')
    return
  }
  if (!hasVectorizableContent(row)) {
    ElMessage.warning('请先填写或生成 AI 整理结果，再确认入库')
    return
  }
  confirmingId.value = row.id
  try {
    await confirmProjectKnowledge(row.id, {
      confirmedContent: row.confirmedContent || row.organizedContent || row.detailContent,
      embeddingSettingKey: row.embeddingSettingKey,
    })
    ElMessage.success('已生成向量并入库')
    await loadData()
  } finally {
    confirmingId.value = undefined
  }
}

function hasVectorizableContent(item?: ProjectKnowledge | null) {
  return Boolean(
    item?.confirmedContent?.trim()
    || item?.organizedContent?.trim()
    || item?.detailContent?.trim(),
  )
}

async function handleConfirmSelected() {
  if (selectedItemOrganizing.value) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return
  }
  if (!form.value.embeddingSettingKey) {
    ElMessage.warning('请先选择向量配置（Embedding 模型）')
    return
  }
  const saved = await handleSave()
  if (!saved) {
    return
  }
  if (!currentId.value) {
    return
  }
  confirmingId.value = currentId.value
  try {
    const updated = await confirmProjectKnowledge(currentId.value, {
      confirmedContent: form.value.confirmedContent,
      embeddingSettingKey: form.value.embeddingSettingKey,
    })
    selectedItem.value = updated
    ElMessage.success('已生成向量并入库')
    await loadData()
  } finally {
    confirmingId.value = undefined
  }
}

async function handleSimilar() {
  if (selectedItemOrganizing.value) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return
  }
  if (!currentId.value) {
    return
  }
  if (!form.value.embeddingSettingKey) {
    ElMessage.warning('请先选择向量配置（Embedding 模型）')
    return
  }
  similarLoading.value = true
  try {
    const result = await searchSimilarProjectKnowledge(currentId.value, form.value.embeddingSettingKey, 5)
    similarItems.value = result.items
    if (!result.items.length) {
      ElMessage.info('没有找到相似知识')
    }
  } finally {
    similarLoading.value = false
  }
}

async function handleDelete(row: ProjectKnowledge) {
  if (isKnowledgeOrganizing(row)) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除储备知识「${row.title}」吗？`, '删除储备知识', { type: 'warning' })
    await deleteProjectKnowledge(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}

function buildPayload() {
  return {
    projectCode: form.value.projectCode,
    knowledgeType: form.value.knowledgeType,
    title: form.value.title,
    simpleDesc: form.value.simpleDesc,
    detailContent: form.value.detailContent,
    organizedContent: form.value.confirmedContent,
    confirmedContent: form.value.confirmedContent,
    aiSettingKey: form.value.aiSettingKey,
    embeddingSettingKey: form.value.embeddingSettingKey,
    documentIds: listToCsv(form.value.documentIdList),
    selectedSimilarKnowledgeId: form.value.selectedSimilarKnowledgeId,
  }
}

function emptyForm(): KnowledgeForm {
  return {
    projectCode: '',
    knowledgeType: 'COMMON_ISSUE',
    title: '',
    simpleDesc: '',
    detailContent: '',
    confirmedContent: '',
    aiSettingKey: '',
    embeddingSettingKey: '',
    documentIdList: [],
  }
}

function defaultEmbeddingSettingKey() {
  return embeddingAiSettings.value[0]?.settingKey || ''
}

function aiSettingLabel(item: AiModelSetting) {
  return `${item.providerName} / ${item.modelName} (${item.settingKey})`
}

function modelPurpose(item: AiModelSetting) {
  if (item.modelPurpose) {
    return item.modelPurpose
  }
  return item.supportImageFlag ? 'IMAGE' : 'LANGUAGE'
}

function vectorStatusTagType(status: string) {
  if (status === 'READY') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  if (status === 'STALE') {
    return 'warning'
  }
  return 'info'
}

function semanticScore(row: ProjectKnowledgeSearchItem) {
  return scoreText(row.matchScore ?? row.score)
}

function scoreText(value?: number) {
  if (value == null || Number.isNaN(value)) {
    return '0.00'
  }
  return value.toFixed(2)
}

function isKnowledgeOrganizing(item?: ProjectKnowledge) {
  return item?.status === 'AI_ORGANIZING'
}

function csvToList(value?: string) {
  if (!value) {
    return []
  }
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

function listToCsv(value: string[]) {
  return value.filter(Boolean).join(',')
}
</script>

<style scoped>
.option-code {
  color: #94a3b8;
  float: right;
  margin-left: 12px;
}

.semantic-search {
  background: linear-gradient(135deg, #f8fafc 0%, #eef6ff 100%);
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 14px;
}

.semantic-search__header {
  align-items: baseline;
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.semantic-search__header strong {
  color: #0f172a;
  font-size: 15px;
}

.semantic-search__header span {
  color: #64748b;
  font-size: 12px;
}

.semantic-search__form {
  display: grid;
  gap: 12px;
  grid-template-columns: 180px 150px 240px 120px 160px;
}

.semantic-search__form :deep(.el-form-item) {
  margin-bottom: 0;
}

.semantic-search__query {
  grid-column: 1 / -1;
}

.semantic-result-panel {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  margin-top: 14px;
  padding: 12px;
}

.semantic-result-panel__title {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.semantic-result-panel__title strong {
  color: #0f172a;
}

.semantic-result-panel__title span {
  color: #64748b;
  font-size: 12px;
}

.knowledge-form {
  margin-top: 4px;
}

.knowledge-status-alert {
  margin-top: 12px;
}

.knowledge-text-editor {
  background: #f8fafc;
  border: 1px solid #dbe4ef;
  border-radius: 12px;
  padding: 10px;
  width: 100%;
}

.knowledge-text-editor__toolbar {
  align-items: center;
  color: #64748b;
  display: flex;
  font-size: 12px;
  justify-content: space-between;
  margin-bottom: 8px;
}

.knowledge-text-editor__actions {
  align-items: center;
  display: flex;
  gap: 4px;
}

.knowledge-text-editor__input :deep(.el-textarea__inner) {
  background: #ffffff;
  border-color: #cbd5e1;
  color: #0f172a;
  font-family: "SFMono-Regular", "Menlo", "Consolas", monospace;
  line-height: 1.65;
}

.knowledge-text-editor__input :deep(.el-textarea__inner) {
  min-height: 320px !important;
}

.knowledge-full-editor__meta {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 8px;
  text-align: right;
}

.similar-panel {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-top: 14px;
  padding: 12px;
}

.similar-panel__title {
  color: #0f172a;
  font-weight: 700;
  margin-bottom: 10px;
}

@media (max-width: 960px) {
  .semantic-search__form {
    grid-template-columns: 1fr;
  }
}
</style>
