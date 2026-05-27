<template>
  <div class="markdown-library-page">
    <PageHeader title="Markdown 文档库">
      <el-space wrap>
        <el-checkbox v-model="uploadOverwrite" :disabled="saving">覆盖同 ID</el-checkbox>
        <el-upload
          action="#"
          accept=".md,text/markdown"
          :auto-upload="false"
          :show-file-list="false"
          :disabled="saving"
          :on-change="handleUploadChange"
        >
          <el-button type="success" :loading="saving">上传 Markdown</el-button>
        </el-upload>
        <el-button @click="loadAll">刷新</el-button>
        <el-button type="primary" @click="openCreate()">新增文档</el-button>
        <el-button type="warning" @click="openCreateFolder()">新增目录</el-button>
      </el-space>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" label-position="top">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="文档 ID、标题、正文或引用" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" clearable filterable placeholder="选择类型" @change="handleSearch">
            <el-option v-for="item in typeOptions" :key="item" :label="typeLabel(item)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable filterable placeholder="选择状态" @change="handleSearch">
            <el-option v-for="item in statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" :loading="loadingList" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="markdown-library-page__grid">
      <el-card shadow="never" class="tree-card">
        <template #header>
          <div class="card-header">
            <span>目录树</span>
            <el-tag type="info">{{ folderCount }} 个目录</el-tag>
          </div>
        </template>
        <el-tree
          v-loading="loadingTree"
          :data="folderTreeNodesWithRoot"
          node-key="documentId"
          default-expand-all
          highlight-current
          :current-node-key="selectedFolderId"
          :props="{ label: 'title', children: 'children' }"
          empty-text="暂无目录"
          @node-click="handleTreeClick"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="tree-node__title">{{ data.title }}</span>
              <span class="tree-node__actions">
                <el-button link type="primary" title="新增子目录" @click.stop="openCreateFolder(data.documentId)">+</el-button>
                <el-button
                  v-if="!isVirtualRoot(data)"
                  link
                  type="danger"
                  title="删除目录"
                  @click.stop="removeTreeFolder(data)"
                >
                  -
                </el-button>
                <el-button
                  v-if="!isVirtualRoot(data)"
                  link
                  type="info"
                  title="详情/修改"
                  @click.stop="openFolderDetail(data)"
                >
                  …
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </el-card>

      <el-card shadow="never" class="list-card">
        <template #header>
          <div class="card-header">
            <span>文档列表：{{ selectedFolderTitle }}</span>
          </div>
        </template>
        <el-table :data="documents" stripe v-loading="loadingList" @row-dblclick="selectDocument">
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="parentId" label="父级" width="160">
            <template #default="{ row }">{{ row.parentId || '-' }}</template>
          </el-table-column>
          <el-table-column label="引用" width="90">
            <template #default="{ row }">{{ row.refs.length }}</template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="selectDocument(row)">查看</el-button>
              <el-button link type="warning" @click.stop="openEdit(row)">编辑</el-button>
              <el-button link type="info" @click.stop="duplicateDocument(row)">复制新建</el-button>
              <el-button link type="success" @click.stop="downloadDocument(row)">下载</el-button>
              <el-button link type="danger" @click.stop="removeDocument(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            layout="total, sizes, prev, pager, next, jumper"
            :page-sizes="[10, 20, 50]"
            :total="total"
            @current-change="loadList"
            @size-change="handlePageSizeChange"
          />
        </div>
      </el-card>
    </div>

    <el-card shadow="never" class="detail-card">
      <template #header>
        <div class="card-header">
          <span>文档详情</span>
          <el-space v-if="selectedDocument" wrap>
            <el-button size="small" @click="openEdit(selectedDocument)">编辑</el-button>
            <el-button v-if="selectedDocument.nodeType !== 'FOLDER'" size="small" type="primary" @click="duplicateDocument(selectedDocument)">复制新建</el-button>
            <el-button v-if="selectedDocument.nodeType !== 'FOLDER'" size="small" type="info" @click="copyDocument(selectedDocument)">复制全文</el-button>
            <el-button v-if="selectedDocument.nodeType !== 'FOLDER'" size="small" type="success" @click="downloadDocument(selectedDocument)">下载</el-button>
          </el-space>
        </div>
      </template>

      <template v-if="selectedDocument">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="标题">{{ selectedDocument.title }}</el-descriptions-item>
          <el-descriptions-item label="节点">{{ nodeTypeLabel(selectedDocument.nodeType) }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(selectedDocument.type) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(selectedDocument.status) }}</el-descriptions-item>
          <el-descriptions-item label="父级">{{ selectedDocument.parentId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedDocument.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ selectedDocument.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <span class="detail-section__label">路径</span>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in selectedPath" :key="item.documentId">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div v-if="selectedDocument.nodeType !== 'FOLDER'" class="detail-section">
          <span class="detail-section__label">引用关系</span>
          <el-space wrap>
            <el-tag v-for="ref in selectedRefs?.refs || []" :key="ref" :type="missingRefSet.has(ref) ? 'danger' : 'info'">
              {{ ref }}
            </el-tag>
            <span v-if="!selectedRefs?.refs.length">无引用</span>
          </el-space>
        </div>

        <div v-if="selectedDocument.nodeType !== 'FOLDER'" class="markdown-library-page__refs">
          <div>
            <strong>引用当前文档</strong>
            <el-tag v-for="doc in selectedRefs?.incomingDocuments || []" :key="doc.documentId" class="ref-tag" @click="selectDocument(doc)">
              {{ doc.title }}
            </el-tag>
            <span v-if="!selectedRefs?.incomingDocuments.length">无</span>
          </div>
          <div>
            <strong>当前引用文档</strong>
            <el-tag v-for="doc in selectedRefs?.outgoingDocuments || []" :key="doc.documentId" class="ref-tag" @click="selectDocument(doc)">
              {{ doc.title }}
            </el-tag>
            <span v-if="!selectedRefs?.outgoingDocuments.length">无</span>
          </div>
        </div>

        <pre v-if="selectedDocument.nodeType !== 'FOLDER'" class="markdown-preview">{{ selectedDocument.content }}</pre>
        <EmptyBlock v-else description="这是目录节点，仅用于整理文档，没有 Markdown 正文。" />
      </template>
      <EmptyBlock v-else description="请选择一篇 Markdown 文档" />
    </el-card>

    <el-dialog v-model="editorVisible" :title="editorDialogTitle" width="900px">
      <el-alert
        type="info"
        :closable="false"
        title="目录节点只用于整理和收集；文档节点保存时会自动生成 FrontMatter。"
      />
      <el-form
        ref="editorFormRef"
        class="document-editor-form"
        :model="editorForm"
        :rules="editorRules"
        label-position="top"
      >
        <div class="document-editor-form__grid">
          <el-form-item label="节点类型" prop="nodeType">
            <el-radio-group v-model="editorForm.nodeType" :disabled="editorMode === 'edit'">
              <el-radio-button label="DOCUMENT">文档</el-radio-button>
              <el-radio-button label="FOLDER">目录</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="标题" prop="title">
            <el-input v-model="editorForm.title" placeholder="请输入文档标题" />
          </el-form-item>
          <el-form-item label="类型" prop="type">
            <el-select
              v-model="editorForm.type"
              allow-create
              default-first-option
              filterable
              placeholder="选择或输入类型"
            >
              <el-option v-for="item in typeOptions" :key="item" :label="typeLabel(item)" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select
              v-model="editorForm.status"
              allow-create
              default-first-option
              filterable
              placeholder="选择或输入状态"
            >
              <el-option v-for="item in statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="父级文档" prop="parentId">
            <el-tree-select
              v-model="editorForm.parentId"
              :data="parentDocumentTreeOptions"
              :props="documentTreeSelectProps"
              check-strictly
              clearable
              default-expand-all
              filterable
              node-key="value"
              placeholder="不选择则为根文档"
            />
          </el-form-item>
          <el-form-item v-if="editorForm.nodeType !== 'FOLDER'" label="引用文档" prop="refs">
            <el-tree-select
              v-model="editorForm.refs"
              :data="referenceDocumentTreeOptions"
              :props="documentTreeSelectProps"
              check-on-click-node
              check-strictly
              clearable
              collapse-tags
              collapse-tags-tooltip
              default-expand-all
              filterable
              multiple
              node-key="value"
              placeholder="勾选需要引用的 Markdown 文档"
              show-checkbox
            />
          </el-form-item>
          <el-form-item v-if="editorForm.nodeType !== 'FOLDER'" class="document-editor-form__wide editor-body-item" label="Markdown 正文">
            <el-input v-model="editorForm.body" type="textarea" :rows="18" placeholder="请输入 Markdown 正文" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEditor">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyBlock from '@/components/common/EmptyBlock.vue'
import {
  createMarkdownDocument,
  deleteMarkdownDocument,
  fetchMarkdownDocument,
  fetchMarkdownDocumentPath,
  fetchMarkdownDocumentRefs,
  fetchMarkdownDocuments,
  fetchMarkdownDocumentTree,
  markdownDocumentDownloadUrl,
  uploadMarkdownDocument,
  updateMarkdownDocument,
} from '@/api/markdownDocument'
import type { MarkdownDocument, MarkdownDocumentNodeType, MarkdownDocumentRefs, MarkdownDocumentTreeNode, SaveMarkdownDocumentPayload } from '@/types/markdownDocument'
import { buildMarkdownDocumentTreeSelectOptions } from '@/utils/markdownDocumentTree'

type EditorMode = 'create' | 'edit'

interface DocumentOption {
  documentId: string
  title: string
  nodeType: MarkdownDocumentNodeType
  type: string
  status: string
}

interface EditorForm {
  documentId: string
  title: string
  nodeType: MarkdownDocumentNodeType
  type: string
  status: string
  parentId: string
  refs: string[]
  body: string
}

const DOCUMENT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/
const ROOT_PARENT_ID = '__ROOT__'
const DEFAULT_TYPE_OPTIONS = ['folder', 'note', 'requirement', 'api', 'design', 'manual']
const DEFAULT_STATUS_OPTIONS = ['draft', 'active', 'archived']
const TYPE_LABELS: Record<string, string> = {
  folder: '目录',
  note: '笔记',
  requirement: '需求',
  api: '接口',
  design: '设计',
  manual: '手册',
}
const STATUS_LABELS: Record<string, string> = {
  draft: '草稿',
  active: '启用',
  archived: '归档',
}
const NODE_TYPE_LABELS: Record<MarkdownDocumentNodeType, string> = {
  DOCUMENT: '文档',
  FOLDER: '目录',
}
const documentTreeSelectProps = {
  children: 'children',
  disabled: 'disabled',
  label: 'label',
  value: 'value',
}

const loadingList = ref(false)
const loadingTree = ref(false)
const saving = ref(false)
const documents = ref<MarkdownDocument[]>([])
const treeNodes = ref<MarkdownDocumentTreeNode[]>([])
const selectedDocument = ref<MarkdownDocument | null>(null)
const selectedPath = ref<MarkdownDocument[]>([])
const selectedRefs = ref<MarkdownDocumentRefs | null>(null)
const editorVisible = ref(false)
const editorMode = ref<EditorMode>('create')
const editingDocumentId = ref('')
const editorFormRef = ref<FormInstance>()
const editorForm = reactive<EditorForm>({
  documentId: '',
  title: '',
  nodeType: 'DOCUMENT',
  type: 'note',
  status: 'draft',
  parentId: '',
  refs: [],
  body: '',
})
const uploadOverwrite = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedFolderId = ref(ROOT_PARENT_ID)
const query = ref({
  keyword: '',
  type: '',
  status: '',
})

const editorRules: FormRules<EditorForm> = {
  documentId: [
    { required: true, message: '请输入文档 ID', trigger: 'blur' },
    { pattern: DOCUMENT_ID_PATTERN, message: '只支持字母、数字、点、下划线、冒号和短横线', trigger: 'blur' },
  ],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  nodeType: [{ required: true, message: '请选择节点类型', trigger: 'change' }],
  type: [{ required: true, message: '请输入类型', trigger: 'change' }],
  status: [{ required: true, message: '请输入状态', trigger: 'change' }],
}

const missingRefSet = computed(() => new Set(selectedRefs.value?.missingRefs || []))
const folderTreeNodes = computed(() => filterFolderTree(treeNodes.value))
const folderTreeNodesWithRoot = computed<MarkdownDocumentTreeNode[]>(() => [{
  documentId: ROOT_PARENT_ID,
  title: '根目录',
  nodeType: 'FOLDER',
  type: 'folder',
  status: 'active',
  refs: [],
  children: folderTreeNodes.value,
}])
const folderCount = computed(() => countFolders(folderTreeNodes.value))
const selectedFolderTitle = computed(() => findTreeNode(folderTreeNodesWithRoot.value, selectedFolderId.value)?.title || '根目录')
const currentParentId = computed(() => normalizeFolderId(selectedFolderId.value))
const editorDialogTitle = computed(() => {
  const prefix = editorMode.value === 'create' ? '新增' : '编辑'
  return `${prefix}${editorForm.nodeType === 'FOLDER' ? '目录' : 'Markdown 文档'}`
})
const flatDocumentOptions = computed(() => flattenTreeOptions(treeNodes.value))
const parentDocumentTreeOptions = computed(() => buildMarkdownDocumentTreeSelectOptions(folderTreeNodes.value, {
  excludeId: editingDocumentId.value,
}))
const referenceDocumentTreeOptions = computed(() => buildMarkdownDocumentTreeSelectOptions(treeNodes.value, {
  documentOnly: true,
  excludeId: editingDocumentId.value,
  promoteExcludedChildren: true,
}))
const typeOptions = computed(() => uniqueOptions([
  ...DEFAULT_TYPE_OPTIONS,
  ...flatDocumentOptions.value.map(item => item.type),
]))
const statusOptions = computed(() => uniqueOptions([
  ...DEFAULT_STATUS_OPTIONS,
  ...flatDocumentOptions.value.map(item => item.status),
]))

onMounted(() => {
  void loadAll()
})

async function loadAll() {
  await loadTree()
  await loadList()
}

async function loadTree() {
  loadingTree.value = true
  try {
    treeNodes.value = await fetchMarkdownDocumentTree()
    if (selectedFolderId.value !== ROOT_PARENT_ID && !findTreeNode(folderTreeNodes.value, selectedFolderId.value)) {
      selectedFolderId.value = ROOT_PARENT_ID
    }
  } finally {
    loadingTree.value = false
  }
}

async function loadList() {
  loadingList.value = true
  try {
    const result = await fetchMarkdownDocuments({
      ...query.value,
      nodeType: 'DOCUMENT',
      parentId: selectedFolderId.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    documents.value = result.items
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } finally {
    loadingList.value = false
  }
}

async function handleSearch() {
  page.value = 1
  await loadList()
}

async function resetQuery() {
  query.value = {
    keyword: '',
    type: '',
    status: '',
  }
  page.value = 1
  await loadList()
}

async function handlePageSizeChange(nextPageSize: number) {
  pageSize.value = nextPageSize
  page.value = 1
  await loadList()
}

async function handleTreeClick(node: MarkdownDocumentTreeNode) {
  selectedFolderId.value = node.documentId
  page.value = 1
  await loadList()
  if (isVirtualRoot(node)) {
    selectedDocument.value = null
    selectedPath.value = []
    selectedRefs.value = null
    return
  }
  await loadDocumentDetail(node.documentId)
}

async function selectDocument(row: MarkdownDocument) {
  await loadDocumentDetail(row.documentId)
}

async function loadDocumentDetail(documentId: string) {
  selectedDocument.value = await fetchMarkdownDocument(documentId)
  const [path, refs] = await Promise.all([
    fetchMarkdownDocumentPath(documentId),
    fetchMarkdownDocumentRefs(documentId),
  ])
  selectedPath.value = path
  selectedRefs.value = refs
}

function setEditorForm(value: EditorForm) {
  Object.assign(editorForm, {
    ...value,
    documentId: value.documentId.trim(),
    title: value.title.trim(),
    nodeType: value.nodeType,
    type: value.type.trim(),
    status: value.status.trim(),
    parentId: value.parentId.trim(),
    refs: value.nodeType === 'FOLDER' ? [] : [...value.refs],
    body: value.nodeType === 'FOLDER' ? '' : value.body,
  })
  void nextTick(() => editorFormRef.value?.clearValidate())
}

function buildSavePayload(): SaveMarkdownDocumentPayload {
  const refs = editorForm.refs
    .map(item => item.trim())
    .filter(Boolean)
  const normalizedRefs = editorForm.nodeType === 'FOLDER' ? [] : refs
  const normalizedBody = editorForm.nodeType === 'FOLDER' ? '' : normalizeLineBreaks(editorForm.body)
  const documentId = editorForm.documentId.trim()
  const title = editorForm.title.trim()
  const type = editorForm.type.trim()
  const status = editorForm.status.trim()
  const parentId = editorForm.parentId.trim() || undefined
  return {
    documentId,
    title,
    nodeType: editorForm.nodeType,
    type,
    status,
    parentId,
    refs: normalizedRefs,
    body: normalizedBody,
    content: buildMarkdownContent({
      documentId,
      title,
      nodeType: editorForm.nodeType,
      type,
      status,
      parentId: parentId || '',
      refs: normalizedRefs,
      body: normalizedBody,
    }),
  }
}

function buildMarkdownContent(value: EditorForm) {
  const frontMatterLines = [
    '---',
    `id: ${value.documentId}`,
    `title: ${frontMatterValue(value.title)}`,
    `node_type: ${value.nodeType}`,
    `type: ${frontMatterValue(value.type)}`,
    `status: ${frontMatterValue(value.status)}`,
    `parent_id: ${value.parentId || ''}`,
  ]
  if (value.refs.length) {
    frontMatterLines.push('refs:')
    value.refs.forEach((ref) => frontMatterLines.push(`  - ${ref}`))
  } else {
    frontMatterLines.push('refs: []')
  }
  frontMatterLines.push('---')
  return `${frontMatterLines.join('\n')}\n\n${value.body}`
}

function stripFrontMatter(content: string) {
  const normalized = normalizeLineBreaks(content)
  const match = normalized.match(/^---\n[\s\S]*?\n---\n?/)
  return match ? normalized.slice(match[0].length).replace(/^\n/, '') : normalized
}

function frontMatterValue(value: string) {
  return value.replace(/\r?\n/g, ' ').trim()
}

function normalizeLineBreaks(value: string) {
  return value.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

function filterFolderTree(nodes: MarkdownDocumentTreeNode[]): MarkdownDocumentTreeNode[] {
  return nodes
    .filter(node => (node.nodeType || 'DOCUMENT') === 'FOLDER')
    .map(node => ({
      ...node,
      children: filterFolderTree(node.children || []),
    }))
}

function countFolders(nodes: MarkdownDocumentTreeNode[]): number {
  return nodes.reduce((count, node) => count + 1 + countFolders(node.children || []), 0)
}

function findTreeNode(nodes: MarkdownDocumentTreeNode[], documentId: string): MarkdownDocumentTreeNode | null {
  for (const node of nodes) {
    if (node.documentId === documentId) {
      return node
    }
    const child = findTreeNode(node.children || [], documentId)
    if (child) {
      return child
    }
  }
  return null
}

function normalizeFolderId(documentId?: string) {
  return !documentId || documentId === ROOT_PARENT_ID ? '' : documentId
}

function isVirtualRoot(node?: MarkdownDocumentTreeNode) {
  return node?.documentId === ROOT_PARENT_ID
}

function flattenTreeOptions(nodes: MarkdownDocumentTreeNode[]) {
  const result: DocumentOption[] = []
  nodes.forEach(node => {
    result.push({
      documentId: node.documentId,
      title: node.title,
      nodeType: node.nodeType || 'DOCUMENT',
      type: node.type,
      status: node.status,
    })
    result.push(...flattenTreeOptions(node.children || []))
  })
  return result
}

function uniqueOptions(values: string[]) {
  return Array.from(new Set(values.map(item => item.trim()).filter(Boolean)))
}

function typeLabel(value?: string) {
  if (!value) {
    return '-'
  }
  return TYPE_LABELS[value] || value
}

function statusLabel(value?: string) {
  if (!value) {
    return '-'
  }
  return STATUS_LABELS[value] || value
}

function nodeTypeLabel(value?: MarkdownDocumentNodeType) {
  return NODE_TYPE_LABELS[value || 'DOCUMENT'] || value || '-'
}

async function copyText(text: string) {
  if (!navigator.clipboard?.writeText) {
    throw new Error('clipboard api unavailable')
  }
  await navigator.clipboard.writeText(text)
}

async function copyDocument(document: MarkdownDocument) {
  if (document.nodeType === 'FOLDER') {
    ElMessage.warning('目录没有 Markdown 正文')
    return
  }
  try {
    await copyText(document.content)
    ElMessage.success('Markdown 全文已复制')
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}

async function duplicateDocument(document: MarkdownDocument) {
  if (document.nodeType === 'FOLDER') {
    ElMessage.warning('目录不能复制为 Markdown 文档')
    return
  }
  saving.value = true
  try {
    const content = buildDuplicatedMarkdownContent(document)
    const saved = await createMarkdownDocument({ content })
    ElMessage.success('已复制新建一条 Markdown 文档')
    selectedFolderId.value = saved.parentId || ROOT_PARENT_ID
    await loadAll()
    await loadDocumentDetail(saved.documentId)
  } finally {
    saving.value = false
  }
}

function buildDuplicatedMarkdownContent(document: MarkdownDocument) {
  const duplicateId = `doc-${Date.now()}`
  const body = stripFrontMatter(document.content)
  const refs = document.refs.filter((ref) => ref !== document.documentId)
  const frontMatterLines = [
    '---',
    `id: ${duplicateId}`,
    `title: ${frontMatterValue(document.title)}`,
    'node_type: DOCUMENT',
    `type: ${frontMatterValue(document.type)}`,
    `status: ${frontMatterValue(document.status)}`,
    `parent_id: ${document.parentId || ''}`,
  ]
  if (refs.length) {
    frontMatterLines.push('refs:')
    refs.forEach((ref) => frontMatterLines.push(`  - ${ref}`))
  } else {
    frontMatterLines.push('refs: []')
  }
  frontMatterLines.push('---')
  return `${frontMatterLines.join('\n')}\n\n${body}`
}

function openCreate(parentId = currentParentId.value) {
  editorMode.value = 'create'
  editingDocumentId.value = ''
  setEditorForm({
    documentId: `doc-${Date.now()}`,
    title: '新建文档',
    nodeType: 'DOCUMENT',
    type: 'note',
    status: 'draft',
    parentId: normalizeFolderId(parentId),
    refs: [],
    body: '# 新建文档\n\n请输入 Markdown 内容。\n',
  })
  editorVisible.value = true
}

function openCreateFolder(parentId = currentParentId.value) {
  editorMode.value = 'create'
  editingDocumentId.value = ''
  setEditorForm({
    documentId: `folder-${Date.now()}`,
    title: '新建目录',
    nodeType: 'FOLDER',
    type: 'folder',
    status: 'active',
    parentId: normalizeFolderId(parentId),
    refs: [],
    body: '',
  })
  editorVisible.value = true
}

function openEdit(document: MarkdownDocument) {
  editorMode.value = 'edit'
  editingDocumentId.value = document.documentId
  setEditorForm({
    documentId: document.documentId,
    title: document.title,
    nodeType: document.nodeType || 'DOCUMENT',
    type: document.type,
    status: document.status,
    parentId: document.parentId || '',
    refs: document.nodeType === 'FOLDER' ? [] : [...document.refs],
    body: document.nodeType === 'FOLDER' ? '' : stripFrontMatter(document.content),
  })
  editorVisible.value = true
}

async function saveEditor() {
  const valid = await editorFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const saved = editorMode.value === 'create'
      ? await createMarkdownDocument(buildSavePayload())
      : await updateMarkdownDocument(editingDocumentId.value, buildSavePayload())
    editorVisible.value = false
    ElMessage.success(editorForm.nodeType === 'FOLDER' ? '目录已保存' : 'Markdown 文档已保存')
    selectedFolderId.value = saved.nodeType === 'FOLDER' ? saved.documentId : (saved.parentId || ROOT_PARENT_ID)
    await loadAll()
    await loadDocumentDetail(saved.documentId)
  } finally {
    saving.value = false
  }
}

async function handleUploadChange(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) {
    return
  }
  if (!file.name.toLowerCase().endsWith('.md')) {
    ElMessage.warning('只能上传 .md 文件')
    return
  }
  saving.value = true
  try {
    const saved = await uploadMarkdownDocument(file, uploadOverwrite.value)
    ElMessage.success(uploadOverwrite.value ? 'Markdown 文档已上传并覆盖' : 'Markdown 文档已上传')
    selectedFolderId.value = saved.parentId || ROOT_PARENT_ID
    await loadAll()
    await loadDocumentDetail(saved.documentId)
  } finally {
    saving.value = false
  }
}

async function removeDocument(document: MarkdownDocument) {
  await ElMessageBox.confirm(`确认删除${document.nodeType === 'FOLDER' ? '目录' : 'Markdown 文档'}「${document.title}」？`, '删除确认', {
    type: 'warning',
  })
  await deleteMarkdownDocument(document.documentId)
  ElMessage.success(document.nodeType === 'FOLDER' ? '目录已删除' : 'Markdown 文档已删除')
  if (selectedFolderId.value === document.documentId) {
    selectedFolderId.value = ROOT_PARENT_ID
  }
  if (selectedDocument.value?.documentId === document.documentId) {
    selectedDocument.value = null
    selectedPath.value = []
    selectedRefs.value = null
  }
  await loadAll()
}

async function removeTreeFolder(node: MarkdownDocumentTreeNode) {
  const document = await fetchMarkdownDocument(node.documentId)
  await removeDocument(document)
}

async function openFolderDetail(node: MarkdownDocumentTreeNode) {
  selectedFolderId.value = node.documentId
  page.value = 1
  await Promise.all([
    loadList(),
    loadDocumentDetail(node.documentId),
  ])
  if (selectedDocument.value) {
    openEdit(selectedDocument.value)
  }
}

function downloadDocument(document: MarkdownDocument) {
  if (document.nodeType === 'FOLDER') {
    ElMessage.warning('目录不能下载')
    return
  }
  window.open(markdownDocumentDownloadUrl(document.documentId), '_blank')
}
</script>

<style scoped>
.markdown-library-page {
  display: grid;
  gap: 14px;
}

.markdown-library-page__grid {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 14px;
}

.tree-card,
.list-card,
.detail-card {
  min-width: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 8px;
}

.tree-node__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node__actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.tree-node:hover .tree-node__actions {
  opacity: 1;
}

.tree-node__actions :deep(.el-button) {
  padding: 0 2px;
}

.detail-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}

.detail-section__label {
  color: var(--el-text-color-secondary);
  min-width: 72px;
}

.markdown-library-page__refs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.ref-tag {
  margin: 6px 6px 0 0;
  cursor: pointer;
}

.markdown-preview {
  margin: 16px 0 0;
  padding: 16px;
  border-radius: 14px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-family: 'IBM Plex Mono', 'SFMono-Regular', monospace;
}

.document-editor-form {
  margin-top: 14px;
}

.document-editor-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.document-editor-form__wide {
  grid-column: 1 / -1;
}

.editor-body-item {
  margin-bottom: 0;
}

@media (max-width: 1100px) {
  .markdown-library-page__grid,
  .markdown-library-page__refs,
  .document-editor-form__grid {
    grid-template-columns: 1fr;
  }
}
</style>
