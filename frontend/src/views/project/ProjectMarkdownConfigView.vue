<template>
  <div>
    <PageHeader
      :title="`${projectName || projectCode} / MD 管理`"
    >
      <el-button @click="leaveToProjectList">返回列表</el-button>
    </PageHeader>

    <el-card v-loading="loading" class="project-md__card">
      <template #header>
        <div class="project-md__toolbar">
          <span>同步策略</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存全部</el-button>
        </div>
      </template>

      <div class="project-md__sync-grid">
        <div class="project-md__sync-item">
          <span class="project-md__sync-label">基础 MD 同步</span>
          <el-radio-group v-model="form.baseMarkdownSyncMode">
            <el-radio-button
              v-for="opt in projectMarkdownBaseSyncModeOptions"
              :key="opt.value"
              :label="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="project-md__sync-item">
          <span class="project-md__sync-label">客户端高版本上传</span>
          <el-switch
            v-model="form.baseMarkdownAllowClientUpload"
            :disabled="form.baseMarkdownSyncMode !== 'AUTO_UPDATE'"
            active-text="允许"
            inactive-text="禁止"
          />
        </div>
        <div class="project-md__sync-item">
          <span class="project-md__sync-label">额外 MD 冲突策略</span>
          <el-radio-group v-model="form.markdownSyncMode">
            <el-radio-button
              v-for="opt in projectMarkdownSyncModeOptions"
              :key="opt.value"
              :label="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="project-md__hint">
        基础 MD 只在“自动更新”时会在客户端启动和创建 ACP 会话前同步；允许上传后，本地版本号高于服务端时会回传并保留服务端旧版本。
      </div>
    </el-card>

    <el-card class="project-md__card">
      <template #header>
        <div class="project-md__toolbar">
          <span>文档库关联</span>
          <el-button text type="primary" @click="$router.push({ name: 'markdown-documents' })">打开文档库</el-button>
        </div>
      </template>

      <el-table :data="documentUsageRows" class="project-md__link-table" stripe>
        <el-table-column prop="label" label="用途" width="160" />
        <el-table-column prop="description" label="说明" min-width="280" show-overflow-tooltip />
        <el-table-column label="已选文档" min-width="320">
          <template #default="{ row }">
            <div v-if="selectedDocumentNames(row.value).length" class="project-md__doc-tags">
              <el-tag
                v-for="name in selectedDocumentNames(row.value).slice(0, 4)"
                :key="name"
                size="small"
                effect="plain"
              >
                {{ name }}
              </el-tag>
              <el-tag v-if="selectedDocumentNames(row.value).length > 4" size="small" type="info" effect="plain">
                +{{ selectedDocumentNames(row.value).length - 4 }}
              </el-tag>
            </div>
            <span v-else class="project-md__empty">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDocumentLinkDialog(row)">配置</el-button>
            <el-button
              link
              type="danger"
              :disabled="selectedDocumentNames(row.value).length === 0"
              @click="clearDocumentLinks(row)"
            >
              清空
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="project-md__hint">
        可以选择目录或文档；选择目录时，使用方会自动加载该目录下全部子文档。需求/缺陷用于 AI 分析；Agent 会话初始化会加载“通用”以及当前 Agent 角色对应的文档。
      </div>
    </el-card>

    <el-card class="project-md__card">
      <template #header>
        <div class="project-md__toolbar">
          <span>角色文件列表</span>
          <el-button type="primary" plain @click="startCreate">新增 Markdown</el-button>
        </div>
      </template>

      <div class="project-md__filters">
        <el-select v-model="roleFilter" style="width: 180px">
          <el-option label="全部角色" value="ALL" />
          <el-option
            v-for="opt in projectMarkdownRoleOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="按路径、角色、内容关键字筛选"
          clearable
          style="max-width: 320px"
        />
      </div>

      <el-table :data="pagedFiles" stripe empty-text="暂无 Markdown 文件">
        <el-table-column prop="filePath" label="文件路径" min-width="260" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            {{ projectMarkdownRoleLabelMap[toRoleScope(row.agentRole)] }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ row.fileType === 'BASE' ? '基础文件' : '额外文件' }}
          </template>
        </el-table-column>
        <el-table-column label="文件标识" width="170">
          <template #default="{ row }">
            {{ row.baseKey ? projectMarkdownBaseKeyLabels[row.baseKey] : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            v{{ row.versionNo || 1 }}
          </template>
        </el-table-column>
        <el-table-column label="内容预览" min-width="260">
          <template #default="{ row }">
            {{ previewContent(row.content) }}
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="openViewer(row)">查看</el-button>
            <el-button link type="warning" @click="openEditor(row)">编辑</el-button>
            <el-button v-if="row.fileType === 'BASE'" link type="primary" @click="openHistory(row)">历史</el-button>
            <el-button link type="danger" @click="removeFile(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="project-md__pagination">
        <el-pagination
          background
          layout="prev, pager, next, sizes, total"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[5, 10, 20]"
          :total="filteredFiles.length"
          @current-change="currentPage = $event"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px">
      <el-form label-width="120px">
        <el-form-item label="适用角色">
          <el-select v-model="editor.roleScope" :disabled="isViewMode">
            <el-option
              v-for="opt in projectMarkdownRoleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="文件类型">
          <el-radio-group v-model="editor.fileType" :disabled="isViewMode">
            <el-radio-button label="BASE">基础文件</el-radio-button>
            <el-radio-button label="EXTRA">额外文件</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="editor.fileType === 'BASE'" label="基础文件">
          <el-select v-model="editor.baseKey" :disabled="isViewMode">
            <el-option
              v-for="(label, key) in projectMarkdownBaseKeyLabels"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-else label="文件路径">
          <el-input v-model="editor.filePath" :disabled="isViewMode" />
        </el-form-item>

        <el-form-item label="落库路径">
          <el-input :model-value="resolvedPreviewPath" disabled />
        </el-form-item>

        <el-form-item label="内容">
          <el-input
            v-model="editor.content"
            type="textarea"
            :rows="16"
            :readonly="isViewMode"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="editorMode === 'view'" type="primary" @click="editorMode = 'edit'">进入编辑</el-button>
        <el-button v-else type="primary" @click="confirmEditor">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="documentLinkDialogVisible"
      :title="activeDocumentUsage ? `配置${activeDocumentUsage.label}关联文档` : '配置文档关联'"
      width="760px"
    >
      <el-tree-select
        v-model="documentLinkDraft"
        :data="documentTreeSelectOptions"
        :props="documentTreeSelectProps"
        check-on-click-node
        check-strictly
        class="w-full"
        collapse-tags
        collapse-tags-tooltip
        clearable
        default-expand-all
        filterable
        multiple
        node-key="value"
        placeholder="选择文档或目录"
        show-checkbox
      />
      <div class="project-md__dialog-hint">
        选择目录时，服务端使用时会自动展开目录下全部子文档；保存全部后配置才会落库。
      </div>
      <template #footer>
        <el-button @click="documentLinkDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDocumentLinks">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="historyVisible"
      :title="historyFile ? `基础 MD 历史 - ${historyFile.filePath}` : '基础 MD 历史'"
      width="980px"
    >
      <el-table v-loading="historyLoading" :data="historyList" stripe empty-text="暂无历史版本">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            {{ projectMarkdownChangeSourceLabelMap[row.changeSource] || row.changeSource }}
          </template>
        </el-table-column>
        <el-table-column prop="changeDesc" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="记录时间" width="180" />
        <el-table-column label="内容预览" min-width="240">
          <template #default="{ row }">
            {{ previewContent(row.content) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openHistoryContent(row)">查看</el-button>
            <el-button link type="warning" @click="rollbackHistory(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { fetchMarkdownDocumentTree } from '@/api/markdownDocument'
import {
  fetchProject,
  fetchProjectMarkdownConfig,
  fetchProjectMarkdownHistory,
  rollbackProjectMarkdownFile,
  saveProjectMarkdownConfig,
} from '@/api/project'
import {
  projectMarkdownBaseSyncModeOptions,
  projectMarkdownBaseKeyLabels,
  projectMarkdownChangeSourceLabelMap,
  projectDocumentUsageOptions,
  projectMarkdownRoleLabelMap,
  projectMarkdownRoleOptions,
  projectMarkdownSyncModeOptions,
} from '@/types/options'
import type { MarkdownDocumentTreeNode } from '@/types/markdownDocument'
import type {
  ProjectDocumentUsage,
  ProjectMarkdownBaseKey,
  ProjectMarkdownConfig,
  ProjectMarkdownFile,
  ProjectMarkdownFileHistory,
  ProjectMarkdownRoleScope,
  SaveProjectMarkdownDocumentLinkPayload,
  SaveProjectMarkdownConfigPayload,
} from '@/types/project'
import { buildMarkdownDocumentTreeSelectOptions } from '@/utils/markdownDocumentTree'
import { useAppStore } from '@/stores/app'

type FilterRole = ProjectMarkdownRoleScope | 'ALL'
type EditorMode = 'create' | 'edit' | 'view'

interface MarkdownEditorState {
  roleScope: ProjectMarkdownRoleScope
  fileType: ProjectMarkdownFile['fileType']
  baseKey?: ProjectMarkdownBaseKey
  filePath: string
  content: string
}

interface DocumentUsageRow {
  label: string
  value: ProjectDocumentUsage
  description: string
}

const props = defineProps<{
  projectCode: string
}>()

const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const saving = ref(false)
const projectName = ref('')
const roleFilter = ref<FilterRole>('ALL')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const dialogVisible = ref(false)
const historyVisible = ref(false)
const historyLoading = ref(false)
const editorMode = ref<EditorMode>('create')
const editingIndex = ref(-1)
const historyFile = ref<ProjectMarkdownFile | null>(null)
const historyList = ref<ProjectMarkdownFileHistory[]>([])
const documentTreeNodes = ref<MarkdownDocumentTreeNode[]>([])
const documentUsageSelections = ref(emptyDocumentUsageSelections())
const documentLinkDialogVisible = ref(false)
const documentLinkDraft = ref<string[]>([])
const activeDocumentUsage = ref<DocumentUsageRow | null>(null)
const documentTreeSelectProps = {
  children: 'children',
  disabled: 'disabled',
  label: 'label',
  value: 'value',
}

const form = ref<SaveProjectMarkdownConfigPayload>({
  markdownSyncMode: 'CANCEL',
  baseMarkdownSyncMode: 'INDEPENDENT',
  baseMarkdownAllowClientUpload: false,
  markdownFiles: [],
  documentLinks: [],
})

const editor = ref<MarkdownEditorState>({
  roleScope: 'COMMON',
  fileType: 'EXTRA',
  filePath: '',
  content: '',
})

const filteredFiles = computed(() =>
  form.value.markdownFiles.filter((file) => {
    if (roleFilter.value !== 'ALL' && toRoleScope(file.agentRole) !== roleFilter.value) {
      return false
    }
    if (!keyword.value.trim()) {
      return true
    }
    const text = [
      file.filePath,
      file.content,
      file.baseKey,
      toRoleScope(file.agentRole),
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return text.includes(keyword.value.trim().toLowerCase())
  }),
)

const pagedFiles = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredFiles.value.slice(start, start + pageSize.value)
})
const documentTreeSelectOptions = computed(() => buildMarkdownDocumentTreeSelectOptions(documentTreeNodes.value))
const documentTitleMap = computed(() => {
  const map = new Map<string, string>()
  collectDocumentTitles(documentTreeNodes.value, map)
  return map
})
const documentUsageRows = computed<DocumentUsageRow[]>(() =>
  projectDocumentUsageOptions.map((option) => ({
    label: option.label,
    value: option.value as ProjectDocumentUsage,
    description: documentUsageDescription(option.value as ProjectDocumentUsage),
  })),
)

const isViewMode = computed(() => editorMode.value === 'view')
const dialogTitle = computed(() => {
  if (editorMode.value === 'create') {
    return '新增 Markdown'
  }
  if (editorMode.value === 'edit') {
    return '编辑 Markdown'
  }
  return '查看 Markdown 内容'
})

const resolvedPreviewPath = computed(() => {
  if (editor.value.fileType === 'BASE' && editor.value.baseKey) {
    return resolveBasePath(editor.value.baseKey, editor.value.roleScope)
  }
  return editor.value.filePath || '-'
})

watch(filteredFiles, (value) => {
  const maxPage = Math.max(1, Math.ceil(value.length / pageSize.value))
  if (currentPage.value > maxPage) {
    currentPage.value = maxPage
  }
})

watch(() => form.value.baseMarkdownSyncMode, (mode) => {
  if (mode !== 'AUTO_UPDATE') {
    form.value.baseMarkdownAllowClientUpload = false
  }
})

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const [project, config] = await Promise.all([
      fetchProject(props.projectCode),
      fetchProjectMarkdownConfig(props.projectCode),
      loadDocumentTreeOptions(),
    ])
    projectName.value = project.projectName
    applyConfig(config)
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function applyConfig(config: ProjectMarkdownConfig) {
  form.value = {
    markdownSyncMode: config.markdownSyncMode,
    baseMarkdownSyncMode: config.baseMarkdownSyncMode || 'INDEPENDENT',
    baseMarkdownAllowClientUpload: Boolean(config.baseMarkdownAllowClientUpload),
    markdownFiles: config.markdownFiles.map((file) => ({ ...file, agentRole: file.agentRole || null })),
    documentLinks: config.documentLinks?.map((link) => ({
      usageType: link.usageType,
      documentId: link.documentId,
    })) || [],
  }
  const selections = emptyDocumentUsageSelections()
  ;(config.documentLinks || [])
    .slice()
    .sort((left, right) => left.sortNo - right.sortNo)
    .forEach((link) => {
      if (!selections[link.usageType]) {
        return
      }
      selections[link.usageType].push(link.documentId)
    })
  documentUsageSelections.value = selections
}

async function loadDocumentTreeOptions() {
  documentTreeNodes.value = await fetchMarkdownDocumentTree()
}

function startCreate() {
  editorMode.value = 'create'
  editingIndex.value = -1
  editor.value = {
    roleScope: roleFilter.value === 'ALL' ? 'COMMON' : roleFilter.value,
    fileType: 'EXTRA',
    filePath: '',
    content: '',
  }
  dialogVisible.value = true
}

function openViewer(file: ProjectMarkdownFile) {
  editingIndex.value = form.value.markdownFiles.indexOf(file)
  editorMode.value = 'view'
  editor.value = toEditorState(file)
  dialogVisible.value = true
}

function openEditor(file: ProjectMarkdownFile) {
  editingIndex.value = form.value.markdownFiles.indexOf(file)
  editorMode.value = 'edit'
  editor.value = toEditorState(file)
  dialogVisible.value = true
}

async function removeFile(file: ProjectMarkdownFile) {
  try {
    await ElMessageBox.confirm(`确认删除 ${file.filePath} 吗？`, '删除确认', {
      type: 'warning',
    })
    form.value.markdownFiles = form.value.markdownFiles.filter((item) => item !== file)
    ElMessage.success('已从待保存列表中移除')
  } catch {
    // ignore cancel
  }
}

async function openHistory(file: ProjectMarkdownFile) {
  if (!file.id) {
    ElMessage.warning('请先保存后再查看历史')
    return
  }
  historyFile.value = file
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyList.value = await fetchProjectMarkdownHistory(props.projectCode, file.id)
  } catch (e: any) {
    ElMessage.error(e.message || '历史加载失败')
  } finally {
    historyLoading.value = false
  }
}

function openHistoryContent(history: ProjectMarkdownFileHistory) {
  editorMode.value = 'view'
  editingIndex.value = -1
  editor.value = {
    roleScope: toRoleScope(history.agentRole),
    fileType: 'BASE',
    baseKey: history.baseKey,
    filePath: history.filePath,
    content: history.content,
  }
  dialogVisible.value = true
}

async function rollbackHistory(history: ProjectMarkdownFileHistory) {
  if (!historyFile.value?.id) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认回滚到 v${history.versionNo} 吗？回滚后会生成一个新的版本号。`, '回滚基础 MD', {
      type: 'warning',
    })
    const file = await rollbackProjectMarkdownFile(props.projectCode, historyFile.value.id, history.id)
    replaceMarkdownFile(file)
    historyFile.value = file
    historyList.value = await fetchProjectMarkdownHistory(props.projectCode, file.id!)
    ElMessage.success('回滚成功')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}

function confirmEditor() {
  if (editor.value.fileType === 'BASE' && !editor.value.baseKey) {
    ElMessage.warning('请选择基础文件')
    return
  }
  if (editor.value.fileType === 'EXTRA' && !editor.value.filePath.trim()) {
    ElMessage.warning('请输入文件路径')
    return
  }
  if (!editor.value.content.trim()) {
    ElMessage.warning('请输入 Markdown 内容')
    return
  }

  const nextFile: ProjectMarkdownFile = {
    agentRole: editor.value.roleScope === 'COMMON' ? null : editor.value.roleScope,
    fileType: editor.value.fileType,
    baseKey: editor.value.fileType === 'BASE' ? editor.value.baseKey : undefined,
    filePath:
      editor.value.fileType === 'BASE' && editor.value.baseKey
        ? resolveBasePath(editor.value.baseKey, editor.value.roleScope)
        : editor.value.filePath.trim(),
    content: editor.value.content.trim(),
  }

  if (editingIndex.value >= 0) {
    form.value.markdownFiles.splice(editingIndex.value, 1, {
      ...form.value.markdownFiles[editingIndex.value],
      ...nextFile,
    })
  } else {
    form.value.markdownFiles.push(nextFile)
  }

  dialogVisible.value = false
}

async function handleSave() {
  saving.value = true
  try {
    const config = await saveProjectMarkdownConfig(props.projectCode, {
      markdownSyncMode: form.value.markdownSyncMode,
      baseMarkdownSyncMode: form.value.baseMarkdownSyncMode,
      baseMarkdownAllowClientUpload: form.value.baseMarkdownAllowClientUpload,
      markdownFiles: form.value.markdownFiles.map((file) => ({
        agentRole: file.agentRole === 'COMMON' ? null : file.agentRole,
        fileType: file.fileType,
        baseKey: file.baseKey,
        filePath: file.filePath,
        content: file.content,
      })),
      documentLinks: buildDocumentLinks(),
    })
    applyConfig(config)
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function leaveToProjectList() {
  appStore.closeActiveTabAndOpen({ name: 'projects' }, router)
}

function buildDocumentLinks(): SaveProjectMarkdownDocumentLinkPayload[] {
  return projectDocumentUsageOptions.flatMap((option) =>
    toDocumentLinks(option.value as ProjectDocumentUsage, documentUsageSelections.value[option.value as ProjectDocumentUsage] || []),
  )
}

function toDocumentLinks(usageType: ProjectDocumentUsage, documentIds: string[]) {
  return Array.from(new Set(documentIds))
    .filter(Boolean)
    .map((documentId) => ({ usageType, documentId }))
}

function openDocumentLinkDialog(row: DocumentUsageRow) {
  activeDocumentUsage.value = row
  documentLinkDraft.value = [...(documentUsageSelections.value[row.value] || [])]
  documentLinkDialogVisible.value = true
}

function confirmDocumentLinks() {
  if (!activeDocumentUsage.value) {
    documentLinkDialogVisible.value = false
    return
  }
  documentUsageSelections.value[activeDocumentUsage.value.value] = Array.from(new Set(documentLinkDraft.value.filter(Boolean)))
  documentLinkDialogVisible.value = false
}

function clearDocumentLinks(row: DocumentUsageRow) {
  documentUsageSelections.value[row.value] = []
}

function selectedDocumentNames(usageType: ProjectDocumentUsage) {
  return (documentUsageSelections.value[usageType] || []).map((documentId) => documentTitleMap.value.get(documentId) || documentId)
}

function collectDocumentTitles(nodes: MarkdownDocumentTreeNode[], map: Map<string, string>) {
  nodes.forEach((node) => {
    map.set(node.documentId, node.title || node.documentId)
    collectDocumentTitles(node.children || [], map)
  })
}

function documentUsageDescription(usageType: ProjectDocumentUsage) {
  const descriptions: Record<ProjectDocumentUsage, string> = {
    REQUIREMENT_AI_ANALYSIS: '需求 AI 分析时附带给模型的业务文档。',
    DEFECT_AI_ANALYSIS: '缺陷 AI 分析时附带给模型的排查文档。',
    AGENT_COMMON: '所有 Agent 初始化会话时都会加载的通用规范。',
    AGENT_MAIN: '主控 Agent 初始化会话时额外加载的规范。',
    AGENT_DEVELOPER: '开发 Agent 初始化会话时额外加载的规范。',
    AGENT_TESTER: '测试 Agent 初始化会话时额外加载的规范。',
    AGENT_OPS: '运维 Agent 初始化会话时额外加载的规范。',
    AGENT_REVIEWER: '评审 Agent 初始化会话时额外加载的规范。',
  }
  return descriptions[usageType] || '-'
}

function emptyDocumentUsageSelections(): Record<ProjectDocumentUsage, string[]> {
  return projectDocumentUsageOptions.reduce((result, option) => {
    result[option.value as ProjectDocumentUsage] = []
    return result
  }, {} as Record<ProjectDocumentUsage, string[]>)
}

function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
}

function toEditorState(file: ProjectMarkdownFile): MarkdownEditorState {
  return {
    roleScope: toRoleScope(file.agentRole),
    fileType: file.fileType,
    baseKey: file.baseKey,
    filePath: file.filePath,
    content: file.content,
  }
}

function replaceMarkdownFile(file: ProjectMarkdownFile) {
  const index = form.value.markdownFiles.findIndex((item) => item.id === file.id)
  const nextFile = { ...file, agentRole: file.agentRole || null }
  if (index >= 0) {
    form.value.markdownFiles.splice(index, 1, nextFile)
    return
  }
  form.value.markdownFiles.push(nextFile)
}

function toRoleScope(role?: ProjectMarkdownFile['agentRole']) {
  return role || 'COMMON'
}

function previewContent(content: string) {
  const compact = content.replace(/\s+/g, ' ').trim()
  return compact.length > 60 ? `${compact.slice(0, 60)}...` : compact || '-'
}

function resolveBasePath(baseKey: ProjectMarkdownBaseKey, roleScope: ProjectMarkdownRoleScope) {
  const basePath = projectMarkdownBaseKeyLabels[baseKey]
  if (roleScope === 'COMMON') {
    return basePath
  }
  return `roles/${roleScope.toLowerCase()}/${basePath}`
}
</script>

<style scoped>
.project-md__card {
  margin-bottom: 16px;
}

.project-md__toolbar {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.project-md__filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.project-md__sync-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.project-md__sync-item {
  align-items: flex-start;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-md__sync-label {
  color: var(--admin-muted);
  font-size: 13px;
}

.project-md__link-table {
  width: 100%;
}

.project-md__doc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.project-md__empty {
  color: var(--el-text-color-placeholder);
}

.project-md__hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 12px;
}

.project-md__dialog-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin-top: 12px;
}

.project-md__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .project-md__link-grid {
    grid-template-columns: 1fr;
  }

  .project-md__sync-grid {
    grid-template-columns: 1fr;
  }
}
</style>
