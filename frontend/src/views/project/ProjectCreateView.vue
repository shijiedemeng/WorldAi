<template>
  <div class="admin-page project-edit-page" v-loading="loading">
    <PageHeader :title="pageTitle" />
    <div class="project-edit-page__form">
      <el-form class="project-edit-form" :model="form" label-width="120px">
        <el-row :gutter="24">
          <el-col :md="12" :xs="24">
            <el-form-item label="项目编码" required>
              <el-input v-model="form.projectCode" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="项目名称" required>
              <el-input v-model="form.projectName" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="技术栈">
              <el-input v-model="form.techStack" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="仓库地址">
              <el-input v-model="form.repositoryUrl" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="MCP文件检索">
              <el-select
                v-model="fileSearchMcpAgentCodes"
                multiple
                filterable
                allow-create
                default-first-option
                class="w-full"
                placeholder="选择或输入可提供文件检索的 Agent"
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
          <el-col :md="12" :xs="24">
            <el-form-item label="额外 MD 冲突">
              <el-select v-model="form.markdownSyncMode" class="w-full">
                <el-option
                  v-for="opt in projectMarkdownSyncModeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="基础 MD 同步">
              <el-select v-model="form.baseMarkdownSyncMode" class="w-full">
                <el-option
                  v-for="opt in projectMarkdownBaseSyncModeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="允许客户端上传">
              <el-switch
                v-model="form.baseMarkdownAllowClientUpload"
                :disabled="form.baseMarkdownSyncMode !== 'AUTO_UPDATE'"
                active-text="允许"
                inactive-text="禁止"
              />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="状态">
              <el-select v-model="form.status" class="w-full">
                <el-option v-for="opt in projectStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="项目描述">
              <el-input v-model="form.projectDesc" type="textarea" :rows="4" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="业务目标">
              <el-input v-model="form.businessGoal" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item>
              <div class="form-actions">
                <el-button type="primary" :loading="saving" @click="handleSubmit">{{ submitText }}</el-button>
                <el-button @click="leaveToProjectList">取消</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { createProject, fetchProject, updateProject } from '@/api/project'
import { fetchAgents } from '@/api/agent'
import { projectMarkdownBaseSyncModeOptions, projectMarkdownSyncModeOptions, projectStatusOptions } from '@/types/options'
import type { CreateProjectPayload, UpdateProjectPayload } from '@/types/project'
import type { Agent } from '@/types/agent'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const loading = ref(false)
const saving = ref(false)
const agents = ref<Agent[]>([])
const fileSearchMcpAgentCodes = ref<string[]>([])
const isEdit = computed(() => Boolean(route.params.projectCode))
const currentProjectCode = computed(() => route.params.projectCode as string | undefined)
const pageTitle = computed(() => (isEdit.value ? '编辑项目' : '新建项目'))
const submitText = computed(() => (isEdit.value ? '保存' : '提交'))
const form = ref<CreateProjectPayload>({
  projectCode: '',
  projectName: '',
  projectDesc: '',
  businessGoal: '',
  techStack: '',
  repositoryUrl: '',
  ownerAgentCode: '',
  fileSearchMcpAgentCodes: '',
  markdownSyncMode: 'CANCEL',
  baseMarkdownSyncMode: 'INDEPENDENT',
  baseMarkdownAllowClientUpload: false,
  status: 'ENABLED',
})

watch(() => form.value.projectCode, async (projectCode) => {
  if (!projectCode) {
    agents.value = []
    return
  }
  agents.value = await fetchAgents(projectCode).catch(() => [])
})

watch(() => form.value.baseMarkdownSyncMode, (mode) => {
  if (mode !== 'AUTO_UPDATE') {
    form.value.baseMarkdownAllowClientUpload = false
  }
})

onMounted(async () => {
  if (!isEdit.value || !currentProjectCode.value) {
    return
  }
  loading.value = true
  try {
    const project = await fetchProject(currentProjectCode.value)
    form.value = {
      projectCode: project.projectCode,
      projectName: project.projectName,
      projectDesc: project.projectDesc || '',
      businessGoal: project.businessGoal || '',
      techStack: project.techStack || '',
      repositoryUrl: project.repositoryUrl || '',
      ownerAgentCode: project.ownerAgentCode || '',
      fileSearchMcpAgentCodes: project.fileSearchMcpAgentCodes || '',
      markdownSyncMode: project.markdownSyncMode,
      baseMarkdownSyncMode: project.baseMarkdownSyncMode || 'INDEPENDENT',
      baseMarkdownAllowClientUpload: Boolean(project.baseMarkdownAllowClientUpload),
      status: project.status,
    }
    fileSearchMcpAgentCodes.value = splitCodes(project.fileSearchMcpAgentCodes)
    agents.value = await fetchAgents(project.projectCode).catch(() => [])
  } finally {
    loading.value = false
  }
})

async function handleSubmit() {
  saving.value = true
  try {
    if (isEdit.value && currentProjectCode.value) {
      const payload: UpdateProjectPayload = {
        projectName: form.value.projectName,
        projectDesc: form.value.projectDesc,
        businessGoal: form.value.businessGoal,
        techStack: form.value.techStack,
        repositoryUrl: form.value.repositoryUrl,
        ownerAgentCode: form.value.ownerAgentCode,
        fileSearchMcpAgentCodes: joinCodes(fileSearchMcpAgentCodes.value),
        markdownSyncMode: form.value.markdownSyncMode,
        baseMarkdownSyncMode: form.value.baseMarkdownSyncMode,
        baseMarkdownAllowClientUpload: form.value.baseMarkdownAllowClientUpload,
        status: form.value.status,
      }
      await updateProject(currentProjectCode.value, payload)
      ElMessage.success('更新成功')
      leaveToProjectList()
      return
    }
    const project = await createProject({
      ...form.value,
      fileSearchMcpAgentCodes: joinCodes(fileSearchMcpAgentCodes.value),
    })
    ElMessage.success('创建成功，请继续维护项目 MD')
    appStore.closeActiveTabAndOpen({ name: 'project-markdown-config', params: { projectCode: project.projectCode } }, router)
  } catch (e: any) {
    ElMessage.error(e.message || (isEdit.value ? '更新失败' : '创建失败'))
  } finally {
    saving.value = false
  }
}

function leaveToProjectList() {
  appStore.closeActiveTabAndOpen({ name: 'projects' }, router)
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
