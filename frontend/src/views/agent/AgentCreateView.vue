<template>
  <div class="admin-page" v-loading="loading">
    <PageHeader :title="pageTitle" />
    <el-card class="form-card form-shell" shadow="never">
      <template #header>Agent 信息</template>
      <el-form :model="form" label-width="120px">
        <el-form-item label="Agent 编码" required>
          <el-input v-model="form.agentCode" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="Agent 名称" required>
          <el-input v-model="form.agentName" />
        </el-form-item>
        <el-form-item label="所属项目" required>
          <el-select v-model="form.projectCode" placeholder="选择项目" class="w-full">
            <el-option
              v-for="project in projects"
              :key="project.projectCode"
              :label="`${project.projectName} (${project.projectCode})`"
              :value="project.projectCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="智能体" required>
          <el-select v-model="form.agentEngineType" class="w-full">
            <el-option v-for="opt in agentEngineTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.agentRole" class="w-full">
            <el-option v-for="opt in agentRoleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.agentDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="能力标签">
          <el-input v-model="form.capabilityTags" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="选择技能">
          <el-select v-model="form.skillCodes" multiple filterable clearable class="w-full" placeholder="选择 Agent 初始化时使用的技能">
            <el-option
              v-for="skill in skills"
              :key="skill.skillCode"
              :label="`${skill.skillName} (${skill.skillCode})`"
              :value="skill.skillCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="回调模式">
          <el-select v-model="form.callbackMode" class="w-full">
            <el-option v-for="opt in callbackModeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="端点地址">
          <el-input v-model="form.endpointUrl" />
        </el-form-item>
        <el-form-item label="MCP传输协议">
          <el-select v-model="form.mcpTransportProtocol" class="w-full">
            <el-option v-for="opt in mcpTransportProtocolOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <div class="form-help">SSE/REST 会在会话提示里告知 Agent 如何接入；本地已配置 MCP 时选择不注入。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" class="w-full">
            <el-option v-for="opt in agentStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <div class="form-actions">
            <el-button type="primary" :loading="saving" @click="handleSubmit">{{ submitText }}</el-button>
            <el-button @click="leaveToAgentList">取消</el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { createAgent, fetchAgent, updateAgent } from '@/api/agent'
import { fetchSkills } from '@/api/skill'
import { fetchProjects } from '@/api/project'
import { agentEngineTypeOptions, agentRoleOptions, agentStatusOptions, callbackModeOptions, mcpTransportProtocolOptions } from '@/types/options'
import type { CreateAgentPayload, UpdateAgentPayload } from '@/types/agent'
import type { Project } from '@/types/project'
import type { Skill } from '@/types/skill'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const loading = ref(false)
const saving = ref(false)
const projects = ref<Project[]>([])
const skills = ref<Skill[]>([])
const isEdit = computed(() => Boolean(route.params.agentCode))
const currentAgentCode = computed(() => route.params.agentCode as string | undefined)
const pageTitle = computed(() => (isEdit.value ? '编辑 Agent' : '新建 Agent'))
const submitText = computed(() => (isEdit.value ? '保存' : '提交'))
const form = ref<CreateAgentPayload>({
  agentCode: '',
  agentName: '',
  projectCode: '',
  agentEngineType: 'QODER',
  agentRole: 'DEVELOPER',
  agentDesc: '',
  capabilityTags: '',
  supportedLinkTypes: '',
  skillCodes: [],
  callbackMode: 'PULL',
  endpointUrl: '',
  mcpTransportProtocol: 'SSE',
  status: 'ONLINE',
})

onMounted(async () => {
  loading.value = true
  try {
    const [projectList, skillList] = await Promise.all([fetchProjects().catch(() => []), fetchSkills().catch(() => [])])
    projects.value = projectList
    skills.value = skillList
    if (isEdit.value && currentAgentCode.value) {
      const agent = await fetchAgent(currentAgentCode.value)
      form.value = {
        agentCode: agent.agentCode,
        agentName: agent.agentName,
        projectCode: agent.projectCode || '',
        agentEngineType: agent.agentEngineType || 'QODER',
        agentRole: agent.agentRole,
        agentDesc: agent.agentDesc || '',
        capabilityTags: agent.capabilityTags || '',
        supportedLinkTypes: agent.supportedLinkTypes || '',
        skillCodes: agent.skillCodes || [],
        callbackMode: agent.callbackMode || 'PULL',
        endpointUrl: agent.endpointUrl || '',
        mcpTransportProtocol: agent.mcpTransportProtocol || 'SSE',
        status: agent.status,
      }
    }
  } finally {
    loading.value = false
  }
})

async function handleSubmit() {
  saving.value = true
  try {
    if (isEdit.value && currentAgentCode.value) {
      const payload: UpdateAgentPayload = {
        agentName: form.value.agentName,
        projectCode: form.value.projectCode,
        agentEngineType: form.value.agentEngineType,
        agentRole: form.value.agentRole,
        agentDesc: form.value.agentDesc,
        capabilityTags: form.value.capabilityTags,
        supportedLinkTypes: form.value.supportedLinkTypes,
        skillCodes: form.value.skillCodes,
        callbackMode: form.value.callbackMode,
        endpointUrl: form.value.endpointUrl,
        mcpTransportProtocol: form.value.mcpTransportProtocol,
        status: form.value.status,
      }
      await updateAgent(currentAgentCode.value, payload)
      ElMessage.success('更新成功')
      leaveToAgentList()
      return
    }
    await createAgent(form.value)
    ElMessage.success('创建成功')
    leaveToAgentList()
  } catch (e: any) {
    ElMessage.error(e.message || (isEdit.value ? '更新失败' : '创建失败'))
  } finally {
    saving.value = false
  }
}

function leaveToAgentList() {
  appStore.closeActiveTabAndOpen({ name: 'agents' }, router)
}
</script>

<style scoped>
.form-help {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
