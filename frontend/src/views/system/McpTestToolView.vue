<template>
  <div class="admin-page mcp-test-page">
    <PageHeader title="MCP协议测试">
      <el-button :loading="loadingAgents" @click="loadClients">刷新在线Agent</el-button>
      <el-button :disabled="!form.agentCode" :loading="loadingTools" @click="handleLoadTools">
        获取工具列表
      </el-button>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :lg="10" :md="24">
        <el-card shadow="never" class="mcp-test-card">
          <template #header>调用参数</template>
          <el-form label-position="top">
            <el-form-item label="Agent">
              <el-select
                v-model="form.agentCode"
                class="w-full"
                filterable
                placeholder="选择在线客户端已关联的 Agent"
                @change="handleAgentChange"
              >
                <el-option
                  v-for="item in onlineAgentOptions"
                  :key="`${item.client.clientCode}:${item.agent.agentCode}`"
                  :label="`${item.agent.agentCode} / ${item.client.clientName}`"
                  :value="item.agent.agentCode"
                >
                  <div class="mcp-agent-option">
                    <span>{{ item.agent.agentCode }}</span>
                    <small>{{ item.client.clientName }} / {{ item.agent.workspaceDir || '-' }}</small>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>

            <el-descriptions v-if="selectedAgent" :column="1" border size="small" class="mcp-agent-info">
              <el-descriptions-item label="客户端">{{ selectedAgent.client.clientName }}（{{ selectedAgent.client.clientCode }}）</el-descriptions-item>
              <el-descriptions-item label="MCP状态">
                <el-tag :type="selectedAgent.client.mcpEnabled ? 'success' : 'info'">
                  {{ selectedAgent.client.mcpEnabled ? '已开启' : '已关闭' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="MCP地址">{{ selectedAgent.client.mcpServerUrl || '/api/mcp' }}</el-descriptions-item>
              <el-descriptions-item label="SSE地址">{{ selectedSseUrl }}</el-descriptions-item>
              <el-descriptions-item label="项目">{{ selectedAgent.agent.projectCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="工作目录">{{ selectedAgent.agent.workspaceDir || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider />

            <el-form-item label="请求方式">
              <el-radio-group v-model="customMode">
                <el-radio-button :label="false">表单</el-radio-button>
                <el-radio-button :label="true">自定义JSON</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <template v-if="!customMode">
              <el-form-item label="操作">
                <el-select v-model="form.operation" class="w-full">
                  <el-option label="TREE 目录树" value="TREE" />
                  <el-option label="LIST 目录列表" value="LIST" />
                  <el-option label="READ 读取文件" value="READ" />
                  <el-option label="SEARCH 搜索文件" value="SEARCH" />
                </el-select>
              </el-form-item>
              <el-form-item label="路径">
                <el-input v-model="form.path" placeholder="默认 .，相对 Agent 工作目录" />
              </el-form-item>
              <el-form-item v-if="form.operation === 'SEARCH'" label="关键字">
                <el-input v-model="form.keyword" placeholder="搜索文件名或文件内容" />
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="最大深度">
                    <el-input-number v-model="form.maxDepth" :min="0" :max="20" class="w-full" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="返回数量">
                    <el-input-number v-model="form.limit" :min="1" :max="1000" class="w-full" />
                  </el-form-item>
                </el-col>
              </el-row>
            </template>

            <el-form-item v-else label="请求JSON">
              <el-input
                v-model="customJson"
                type="textarea"
                :rows="12"
                placeholder="{&quot;operation&quot;:&quot;TREE&quot;,&quot;path&quot;:&quot;.&quot;,&quot;maxDepth&quot;:3,&quot;limit&quot;:100}"
              />
            </el-form-item>

            <div class="mcp-test-actions">
              <el-button type="primary" :disabled="!form.agentCode" :loading="executing" @click="handleExecute">
                执行测试
              </el-button>
              <el-button @click="resetForm">重置</el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>

      <el-col :lg="14" :md="24">
        <el-card shadow="never" class="mcp-test-card">
          <template #header>
            <div class="mcp-result-header">
              <span>测试结果</span>
              <el-tag v-if="result" :type="result.errorMessage ? 'danger' : result.enabled === false ? 'info' : 'success'">
                {{ result.errorMessage ? '失败' : result.enabled === false ? 'MCP关闭' : '成功' }}
              </el-tag>
            </div>
          </template>

          <el-empty v-if="!result && tools.length === 0" description="请选择 Agent 后手动获取工具列表或执行测试" />

          <div v-if="tools.length > 0" class="mcp-result-section">
            <div class="mcp-result-section__title">工具列表</div>
            <el-table :data="tools" size="small">
              <el-table-column prop="name" label="工具" width="120" />
              <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </div>

          <div v-if="result" class="mcp-result-section">
            <div class="mcp-result-section__title">概要</div>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="请求ID">{{ result.requestId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="操作">{{ result.operation || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Agent">{{ result.agentCode || form.agentCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="客户端">{{ result.clientCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="根目录">{{ result.basePath || '-' }}</el-descriptions-item>
              <el-descriptions-item label="路径">{{ result.path || '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="result.errorMessage" label="错误" :span="2">
                <el-text type="danger">{{ result.errorMessage }}</el-text>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <div v-if="entryRows.length > 0" class="mcp-result-section">
            <div class="mcp-result-section__title">文件/目录结果</div>
            <el-table :data="entryRows" size="small" max-height="340">
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
              <el-table-column prop="size" label="大小" width="110" />
              <el-table-column prop="depth" label="深度" width="80" />
              <el-table-column prop="preview" label="预览" min-width="220" show-overflow-tooltip />
            </el-table>
          </div>

          <div v-if="result?.content" class="mcp-result-section">
            <div class="mcp-result-section__title">文件内容</div>
            <pre class="mcp-content">{{ result.content }}</pre>
          </div>

          <div v-if="result" class="mcp-result-section">
            <div class="mcp-result-section__title">原始响应</div>
            <pre class="mcp-content">{{ prettyResult }}</pre>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { fetchClientNodes } from '@/api/client'
import { executeMcpFileTool, fetchMcpFileTools } from '@/api/mcp'
import type { ClientControllableAgent, ClientNode } from '@/types/client'
import type { McpFileOperation, McpFileRequestPayload, McpFileResponse, McpFileTool } from '@/types/mcp'

interface OnlineAgentOption {
  client: ClientNode
  agent: ClientControllableAgent
}

const loadingAgents = ref(false)
const loadingTools = ref(false)
const executing = ref(false)
const customMode = ref(false)
const clients = ref<ClientNode[]>([])
const tools = ref<McpFileTool[]>([])
const result = ref<McpFileResponse | null>(null)
const customJson = ref('{\n  "operation": "TREE",\n  "path": ".",\n  "maxDepth": 3,\n  "limit": 100\n}')
const form = ref({
  agentCode: '',
  operation: 'TREE' as McpFileOperation,
  path: '.',
  keyword: '',
  maxDepth: 3,
  limit: 100,
})

const onlineAgentOptions = computed<OnlineAgentOption[]>(() => {
  const rows: OnlineAgentOption[] = []
  clients.value
    .filter((client) => client.status === 'ONLINE')
    .forEach((client) => {
      ;(client.agents || [])
        .filter((agent) => agent.enabledFlag !== false)
        .forEach((agent) => rows.push({ client, agent }))
    })
  return rows
})

const selectedAgent = computed(() => onlineAgentOptions.value.find((item) => item.agent.agentCode === form.value.agentCode) || null)
const selectedSseUrl = computed(() => form.value.agentCode ? `/api/mcp/agents/${encodeURIComponent(form.value.agentCode)}/sse` : '-')
const entryRows = computed(() => [...(result.value?.entries || []), ...(result.value?.matches || [])])
const prettyResult = computed(() => result.value ? JSON.stringify(result.value, null, 2) : '')

onMounted(async () => {
  await loadClients()
})

async function loadClients() {
  loadingAgents.value = true
  try {
    clients.value = await fetchClientNodes()
    if (!form.value.agentCode && onlineAgentOptions.value.length) {
      form.value.agentCode = onlineAgentOptions.value[0].agent.agentCode
    }
  } finally {
    loadingAgents.value = false
  }
}

function handleAgentChange() {
  tools.value = []
  result.value = null
}

async function handleLoadTools() {
  if (!form.value.agentCode) {
    ElMessage.warning('请选择 Agent')
    return
  }
  loadingTools.value = true
  try {
    tools.value = await fetchMcpFileTools(form.value.agentCode)
    if (tools.value.length === 0) {
      ElMessage.info('该客户端 MCP 未开启或没有返回可用工具')
    }
  } finally {
    loadingTools.value = false
  }
}

async function handleExecute() {
  if (!form.value.agentCode) {
    ElMessage.warning('请选择 Agent')
    return
  }
  const payload = buildPayload()
  if (!payload) {
    return
  }
  executing.value = true
  try {
    result.value = await executeMcpFileTool(form.value.agentCode, payload)
  } finally {
    executing.value = false
  }
}

function buildPayload(): McpFileRequestPayload | null {
  if (customMode.value) {
    try {
      const parsed = JSON.parse(customJson.value)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        ElMessage.warning('自定义 JSON 必须是对象')
        return null
      }
      return parsed
    } catch (e: any) {
      ElMessage.error(`JSON格式错误：${e.message || e}`)
      return null
    }
  }
  if (form.value.operation === 'SEARCH' && !form.value.keyword.trim()) {
    ElMessage.warning('SEARCH 操作需要填写关键字')
    return null
  }
  return {
    operation: form.value.operation,
    path: form.value.path.trim() || '.',
    keyword: form.value.operation === 'SEARCH' ? form.value.keyword.trim() : undefined,
    maxDepth: form.value.maxDepth,
    limit: form.value.limit,
  }
}

function resetForm() {
  form.value.operation = 'TREE'
  form.value.path = '.'
  form.value.keyword = ''
  form.value.maxDepth = 3
  form.value.limit = 100
  customJson.value = '{\n  "operation": "TREE",\n  "path": ".",\n  "maxDepth": 3,\n  "limit": 100\n}'
  result.value = null
}
</script>

<style scoped>
.mcp-test-card {
  margin-bottom: 16px;
}

.mcp-agent-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mcp-agent-option small {
  color: #64748b;
}

.mcp-agent-info {
  margin-top: 8px;
}

.mcp-test-actions {
  display: flex;
  gap: 10px;
}

.mcp-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mcp-result-section {
  margin-bottom: 18px;
}

.mcp-result-section__title {
  margin-bottom: 8px;
  font-weight: 700;
  color: #0f172a;
}

.mcp-content {
  margin: 0;
  padding: 14px;
  max-height: 420px;
  overflow: auto;
  border-radius: 12px;
  background: #0f172a;
  color: #dbeafe;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
