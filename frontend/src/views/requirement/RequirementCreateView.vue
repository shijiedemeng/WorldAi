<template>
  <div class="admin-page">
    <PageHeader
      :title="pageTitle"
    >
      <el-button
        v-if="backParentRequirementNo"
        @click="goParentRequirement"
      >
        返回上一层
      </el-button>
    </PageHeader>
    <el-card class="form-card form-shell" shadow="never">
      <template #header>需求信息</template>
      <el-form :model="form" label-width="120px">
        <el-row :gutter="24">
          <el-col :md="12" :xs="24">
            <el-form-item :label="isChildCreateMode ? '父需求' : '模块类型'" required>
              <el-input :model-value="moduleContextLabel" disabled />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="所属项目" required>
              <el-select v-model="form.projectCode" placeholder="选择项目" class="w-full" :disabled="isChildCreateMode || isEditMode">
                <el-option
                  v-for="p in projects"
                  :key="p.projectCode"
                  :label="p.projectName"
                  :value="p.projectCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="需求编号" required>
              <el-input v-model="form.requirementNo" :disabled="isEditMode" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm" :md="12" :xs="24">
            <el-form-item label="负责 Agent">
              <el-select v-model="form.mainAgentCode" clearable placeholder="可选" class="w-full">
                <el-option
                  v-for="agent in agents"
                  :key="agent.agentCode"
                  :label="`${agent.agentName} (${agent.agentCode})`"
                  :value="agent.agentCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="优先级">
              <el-select v-model="form.priority" class="w-full">
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="状态">
              <el-select v-model="form.status" class="w-full">
                <el-option
                  v-for="opt in requirementStatusOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                  :disabled="opt.value === 'ANALYZING'"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="当前阶段">
              <el-input v-model="form.currentStage" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.requirementType !== 'SUB'" :md="12" :xs="24">
            <el-form-item label="执行模式">
              <el-select v-model="form.executionMode" class="w-full">
                <el-option
                  v-for="opt in requirementExecutionModeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.requirementType !== 'SUB'" :md="12" :xs="24">
            <el-form-item label="自动执行">
              <el-switch v-model="form.autoExecuteFlag" active-text="允许" inactive-text="禁止" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.requirementType !== 'SUB'" :md="12" :xs="24">
            <el-form-item label="MCP文件检索">
              <el-select
                v-model="fileSearchMcpAgentCodes"
                multiple
                filterable
                allow-create
                default-first-option
                class="w-full"
                placeholder="默认读取项目配置，可手工调整"
              >
                <el-option
                  v-for="agent in agents"
                  :key="agent.agentCode"
                  :label="`${agent.agentName} (${agent.agentCode})`"
                  :value="agent.agentCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm" :md="12" :xs="24">
            <el-form-item label="回执可提取">
              <el-switch v-model="form.resultExtractableFlag" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm" :md="12" :xs="24">
            <el-form-item label="启用MCP检索">
              <el-switch v-model="form.mcpFileSearchEnabledFlag" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm" :md="12" :xs="24">
            <el-form-item label="项目储备库">
              <el-switch v-model="form.projectKnowledgeSearchEnabledFlag" active-text="允许" inactive-text="关闭" />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm && form.projectKnowledgeSearchEnabledFlag" :md="12" :xs="24">
            <el-form-item label="读取数量">
              <el-input-number v-model="form.projectKnowledgeSearchLimit" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm && form.projectKnowledgeSearchEnabledFlag" :md="12" :xs="24">
            <el-form-item label="最低准确值">
              <el-input-number v-model="form.projectKnowledgeSearchMinScore" :min="0" :max="100" :step="1" />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm" :md="24" :xs="24">
            <el-form-item label="关联文档">
              <el-tree-select
                v-model="documentIds"
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
                placeholder="选择执行前需要 Agent 阅读的文档，可为空"
                show-checkbox
              />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm" :md="12" :xs="24">
            <el-form-item label="会话策略">
              <el-select v-model="form.sessionStrategy" clearable placeholder="可选" class="w-full">
                <el-option
                  v-for="opt in sessionStrategyOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="排序号">
              <el-input-number v-model="form.sortNo" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="来源">
              <el-input v-model="form.source" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="创建人">
              <el-input v-model="form.createdBy" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="描述">
              <el-input v-model="form.requirementDesc" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col v-if="isSubRequirementForm" :span="24">
            <el-form-item label="执行步骤">
              <el-input
                v-model="form.executionSteps"
                type="textarea"
                :rows="6"
                placeholder="一行一个步骤，例如：&#10;1. 拉取项目 Markdown&#10;2. 补接口&#10;3. 联调并回填结果"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <div class="form-actions">
                <el-button type="primary" :loading="saving || loading" @click="handleSubmit">
                  {{ isEditMode ? '保存' : '提交' }}
                </el-button>
                <el-button @click="leaveCurrentPage">取消</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { fetchAgents } from '@/api/agent'
import { fetchMarkdownDocumentTree } from '@/api/markdownDocument'
import { fetchProjects } from '@/api/project'
import {
  createChildRequirement,
  createMasterRequirement,
  fetchRequirement,
  fetchRequirementChildren,
  fetchRequirements,
  updateRequirement,
} from '@/api/requirement'
import {
  requirementExecutionModeOptions,
  requirementStatusOptions,
  sessionStrategyOptions,
} from '@/types/options'
import type { Agent } from '@/types/agent'
import type { MarkdownDocumentTreeNode } from '@/types/markdownDocument'
import type { Project } from '@/types/project'
import type {
  CreateRequirementPayload,
  Requirement,
  UpdateRequirementPayload,
} from '@/types/requirement'
import { useAppStore } from '@/stores/app'
import { buildMarkdownDocumentTreeSelectOptions } from '@/utils/markdownDocumentTree'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const saving = ref(false)
const loading = ref(false)
const projects = ref<Project[]>([])
const agents = ref<Agent[]>([])
const masterRequirements = ref<Requirement[]>([])
const fileSearchMcpAgentCodes = ref<string[]>([])
const documentIds = ref<string[]>([])
const documentTreeNodes = ref<MarkdownDocumentTreeNode[]>([])
const documentTreeSelectProps = {
  children: 'children',
  disabled: 'disabled',
  label: 'label',
  value: 'value',
}
const routeParentRequirementNo = typeof route.params.parentRequirementNo === 'string' ? route.params.parentRequirementNo : ''
const queryParentRequirementNo = typeof route.query.parentRequirementNo === 'string' ? route.query.parentRequirementNo : ''
const initialParentRequirementNo = routeParentRequirementNo || queryParentRequirementNo
const parentRequirementNo = ref<string>(initialParentRequirementNo)
const backParentRequirementNo = ref<string>(initialParentRequirementNo)
const currentRequirementNo = ref<string>((route.params.requirementNo as string) || '')
const isEditMode = ref(Boolean(currentRequirementNo.value))
const isChildCreateMode = computed(() => !isEditMode.value && Boolean(initialParentRequirementNo))
const isSubRequirementForm = computed(() => form.value.requirementType === 'SUB')
const parentRequirementLabel = computed(() => {
  const parent = masterRequirements.value.find((item) => item.requirementNo === parentRequirementNo.value)
  return parent ? `${parent.requirementNo} / ${parent.title}` : parentRequirementNo.value || '-'
})
const moduleContextLabel = computed(() => {
  if (isChildCreateMode.value) {
    return parentRequirementLabel.value
  }
  if (isEditMode.value) {
    return form.value.requirementType === 'SUB' ? '子模块' : '总模块'
  }
  return '总模块'
})
const pageTitle = computed(() => {
  if (isEditMode.value) {
    return '编辑需求'
  }
  return isChildCreateMode.value ? '新建子模块' : '新建总模块'
})
const documentTreeSelectOptions = computed(() => buildMarkdownDocumentTreeSelectOptions(documentTreeNodes.value))

const form = ref<CreateRequirementPayload>({
  requirementNo: '',
  projectCode: (route.query.projectCode as string) || '',
  title: '',
  requirementDesc: '',
  priority: 'MEDIUM',
  requirementType: initialParentRequirementNo ? 'SUB' : 'MASTER',
  sortNo: 0,
  status: 'PENDING',
  source: '',
  mainAgentCode: '',
  sessionStrategy: 'NEW',
  preferredSessionCode: '',
  currentStage: '',
  expectedDeadline: '',
  createdBy: '',
  executionSteps: '',
  autoExecuteFlag: false,
  fileSearchMcpAgentCodes: '',
  mcpFileSearchEnabledFlag: false,
  documentIds: '',
  projectKnowledgeSearchEnabledFlag: false,
  projectKnowledgeSearchLimit: 5,
  projectKnowledgeSearchMinScore: 70,
  executionMode: 'NORMAL',
  resultExtractableFlag: true,
})

onMounted(async () => {
  loading.value = true
  const projectList = await fetchProjects().catch(() => [])
  projects.value = projectList
  documentTreeNodes.value = await fetchMarkdownDocumentTree().catch(() => [])
  applyProjectMcpDefault()
  if (isChildCreateMode.value && parentRequirementNo.value) {
    const parent = await fetchRequirement(parentRequirementNo.value).catch(() => null)
    if (parent) {
      form.value.projectCode = parent.projectCode
      form.value.requirementType = 'SUB'
      form.value.executionMode = parent.executionMode || 'NORMAL'
      parentRequirementNo.value = parent.requirementNo
      backParentRequirementNo.value = parent.requirementNo
      await fillDefaultChildRequirementNo(parent.requirementNo)
    }
  } else if (isEditMode.value && currentRequirementNo.value) {
    const current = await fetchRequirement(currentRequirementNo.value)
    parentRequirementNo.value = current.parentRequirementNo || ''
    backParentRequirementNo.value = current.parentRequirementNo || ''
    form.value = {
      requirementNo: current.requirementNo,
      projectCode: current.projectCode,
      title: current.title,
      requirementDesc: current.requirementDesc || '',
      priority: current.priority || 'MEDIUM',
      requirementType: current.requirementType,
      sortNo: current.sortNo || 0,
      status: current.status,
      source: current.source || '',
      mainAgentCode: current.mainAgentCode || '',
      sessionStrategy: current.sessionStrategy || 'NEW',
      preferredSessionCode: current.preferredSessionCode || '',
      currentStage: current.currentStage || '',
      expectedDeadline: current.expectedDeadline || '',
      createdBy: current.createdBy || '',
      executionSteps: current.executionSteps || '',
      autoExecuteFlag: Boolean(current.autoExecuteFlag),
      fileSearchMcpAgentCodes: current.fileSearchMcpAgentCodes || '',
      mcpFileSearchEnabledFlag: Boolean(current.mcpFileSearchEnabledFlag),
      documentIds: current.documentIds || '',
      projectKnowledgeSearchEnabledFlag: Boolean(current.projectKnowledgeSearchEnabledFlag),
      projectKnowledgeSearchLimit: current.projectKnowledgeSearchLimit ?? 5,
      projectKnowledgeSearchMinScore: current.projectKnowledgeSearchMinScore ?? 70,
      executionMode: current.executionMode || 'NORMAL',
      resultExtractableFlag: current.resultExtractableFlag !== false,
    }
    fileSearchMcpAgentCodes.value = splitCodes(current.fileSearchMcpAgentCodes)
    documentIds.value = splitCodes(current.documentIds)
  }
  await loadAgents()
  await loadMasterRequirements()
  loading.value = false
})

watch(
  () => form.value.projectCode,
  async () => {
    if (!form.value.projectCode) {
      agents.value = []
      masterRequirements.value = []
      form.value.mainAgentCode = ''
      return
    }
    await Promise.all([loadAgents(), loadMasterRequirements()])
    applyProjectMcpDefault()
  },
)

async function loadAgents() {
  if (!form.value.projectCode) {
    agents.value = []
    return
  }
  agents.value = await fetchAgents(form.value.projectCode).catch(() => [])
  if (
    form.value.mainAgentCode &&
    !agents.value.some((item) => item.agentCode === form.value.mainAgentCode)
  ) {
    form.value.mainAgentCode = ''
  }
}

async function loadMasterRequirements() {
  if (!form.value.projectCode) {
    masterRequirements.value = []
    return
  }
  const list = await fetchRequirements(form.value.projectCode).catch(() => [])
  masterRequirements.value = list.filter((item) => item.requirementType === 'MASTER')
}

async function fillDefaultChildRequirementNo(masterRequirementNo: string) {
  if (form.value.requirementNo.trim()) {
    return
  }
  const children = await fetchRequirementChildren(masterRequirementNo).catch(() => [])
  const nextNo = children.length + 1
  form.value.requirementNo = `${masterRequirementNo}-${nextNo}`
  form.value.sortNo = nextNo
}

async function handleSubmit() {
  if (isChildCreateMode.value && !parentRequirementNo.value) {
    ElMessage.warning('缺少父需求')
    return
  }

  saving.value = true
  try {
    const sharedPayload: UpdateRequirementPayload = {
      title: form.value.title.trim(),
      requirementDesc: optionalText(form.value.requirementDesc),
      priority: optionalText(form.value.priority),
      sortNo: form.value.sortNo,
      status: form.value.status,
      source: optionalText(form.value.source),
      currentStage: optionalText(form.value.currentStage),
      expectedDeadline: optionalText(form.value.expectedDeadline),
      createdBy: optionalText(form.value.createdBy),
    }
    if (isSubRequirementForm.value) {
      sharedPayload.mainAgentCode = optionalText(form.value.mainAgentCode)
      sharedPayload.sessionStrategy = form.value.sessionStrategy || undefined
      sharedPayload.preferredSessionCode = optionalText(form.value.preferredSessionCode)
      sharedPayload.executionSteps = optionalText(form.value.executionSteps)
      sharedPayload.resultExtractableFlag = form.value.resultExtractableFlag !== false
      sharedPayload.documentIds = joinCodes(documentIds.value)
      sharedPayload.projectKnowledgeSearchEnabledFlag = form.value.projectKnowledgeSearchEnabledFlag === true
      sharedPayload.projectKnowledgeSearchLimit = form.value.projectKnowledgeSearchEnabledFlag
        ? Number(form.value.projectKnowledgeSearchLimit || 5)
        : undefined
      sharedPayload.projectKnowledgeSearchMinScore = form.value.projectKnowledgeSearchEnabledFlag
        ? Number(form.value.projectKnowledgeSearchMinScore ?? 70)
        : undefined
    } else {
      sharedPayload.executionMode = form.value.executionMode
      sharedPayload.autoExecuteFlag = Boolean(form.value.autoExecuteFlag)
      sharedPayload.fileSearchMcpAgentCodes = joinCodes(fileSearchMcpAgentCodes.value)
    }
    if (isSubRequirementForm.value) {
      sharedPayload.mcpFileSearchEnabledFlag = Boolean(form.value.mcpFileSearchEnabledFlag)
    }
    const requirement = isEditMode.value && currentRequirementNo.value
      ? await updateRequirement(currentRequirementNo.value, sharedPayload as UpdateRequirementPayload)
      : await (
          form.value.requirementType === 'SUB'
            ? createChildRequirement(parentRequirementNo.value, {
                requirementNo: form.value.requirementNo.trim(),
                projectCode: form.value.projectCode,
                requirementType: form.value.requirementType,
                ...sharedPayload,
              } as CreateRequirementPayload)
            : createMasterRequirement({
                requirementNo: form.value.requirementNo.trim(),
                projectCode: form.value.projectCode,
                requirementType: form.value.requirementType,
                ...sharedPayload,
              } as CreateRequirementPayload)
        )
    ElMessage.success(isEditMode.value ? '保存成功' : '创建成功')
    appStore.closeActiveTabAndOpen({ name: 'requirement-detail', params: { requirementNo: requirement.requirementNo } }, router)
  } catch (e: any) {
    ElMessage.error(e.message || (isEditMode.value ? '保存失败' : '创建失败'))
  } finally {
    saving.value = false
  }
}

function goParentRequirement() {
  const target = parentRequirementNo.value || backParentRequirementNo.value
  if (target) {
    appStore.closeActiveTabAndOpen({ name: 'requirement-detail', params: { requirementNo: target } }, router)
    return
  }
  appStore.closeActiveTabAndOpen({ name: 'requirements' }, router)
}

function leaveCurrentPage() {
  const target = parentRequirementNo.value || backParentRequirementNo.value
  if (target) {
    appStore.closeActiveTabAndOpen({ name: 'requirement-detail', params: { requirementNo: target } }, router)
    return
  }
  appStore.closeActiveTabAndOpen({ name: 'requirements' }, router)
}

function optionalText(value?: string) {
  const next = value?.trim()
  return next ? next : undefined
}

function applyProjectMcpDefault() {
  if (isEditMode.value || isSubRequirementForm.value || fileSearchMcpAgentCodes.value.length > 0) {
    return
  }
  const project = projects.value.find((item) => item.projectCode === form.value.projectCode)
  fileSearchMcpAgentCodes.value = splitCodes(project?.fileSearchMcpAgentCodes)
}

function splitCodes(value?: string) {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function joinCodes(value: string[]) {
  return value.map((item) => item.trim()).filter(Boolean).join(',')
}
</script>
