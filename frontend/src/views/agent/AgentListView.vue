<template>
  <div class="admin-page">
    <PageHeader title="Agent 列表">
      <el-button :loading="loading" @click="loadList">刷新</el-button>
      <el-button type="primary" @click="$router.push({ name: 'agent-create' })">新建 Agent</el-button>
    </PageHeader>
    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" :model="filters" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="Agent 编码 / 名称 / 项目" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.agentRole" clearable placeholder="全部角色" class="w-full">
            <el-option v-for="opt in agentRoleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" class="w-full">
            <el-option v-for="opt in agentStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" @click="page = 1">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>Agent 数据</strong>
          <span>共 {{ filteredList.length }} 条 Agent 记录</span>
        </div>
      </div>
      <el-table :data="pagedList" v-loading="loading" stripe>
        <el-table-column prop="agentCode" label="编码" width="160" />
        <el-table-column prop="agentName" label="名称" />
        <el-table-column prop="projectCode" label="项目编号" width="160" />
        <el-table-column prop="agentEngineType" label="智能体" width="100">
          <template #default="{ row }">
            {{ agentEngineTypeLabelMap[row.agentEngineType] || row.agentEngineType || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="agentRole" label="角色" width="100" />
        <el-table-column prop="mcpTransportProtocol" label="MCP协议" width="120">
          <template #default="{ row }">
            {{ mcpTransportProtocolLabelMap[row.mcpTransportProtocol || 'SSE'] || row.mcpTransportProtocol || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="客户端心跳" min-width="240">
          <template #default="{ row }">
            <div v-if="getAgentClientHeartbeats(row.agentCode).length" class="agent-heartbeat">
              <el-tag type="success" effect="light" size="small">在线 {{ getAgentClientHeartbeats(row.agentCode).length }}</el-tag>
              <span class="agent-heartbeat__time">{{ latestAgentClientHeartbeat(row.agentCode)?.lastHeartbeatTime || '-' }}</span>
              <el-tooltip :content="formatClientNames(getAgentClientHeartbeats(row.agentCode))" placement="top">
                <span class="agent-heartbeat__clients">{{ formatClientNames(getAgentClientHeartbeats(row.agentCode)) }}</span>
              </el-tooltip>
            </div>
            <span v-else class="agent-heartbeat__empty">无在线客户端</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastHeartbeatTime" label="最后心跳" width="180" />
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'agent-edit', params: { agentCode: row.agentCode } })">编辑</el-button>
            <el-button link type="primary" @click="router.push({ name: 'agent-tasks', params: { agentCode: row.agentCode } })">任务</el-button>
            <el-button link type="primary" @click="openAgentSessions(row)">会话</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="filteredList.length"
        />
      </div>
    </el-card>

    <el-drawer v-model="sessionDrawerVisible" :title="sessionDrawerTitle" size="72%" destroy-on-close>
      <div class="agent-session-drawer" v-loading="sessionLoading">
        <div class="table-toolbar">
          <div class="table-toolbar__title">
            <strong>执行过任务的会话记录</strong>
            <span>客户端开始或完成任务时自动保存，后续可用于追踪 Agent 实际使用过的会话。</span>
          </div>
          <el-button :loading="sessionLoading" @click="loadAgentSessions()">刷新</el-button>
        </div>
        <el-table :data="agentSessions" size="small" stripe empty-text="暂无执行过任务的会话记录">
          <el-table-column prop="sessionName" label="会话名称" min-width="140" />
          <el-table-column prop="externalSessionId" label="实际会话ID" min-width="220" show-overflow-tooltip />
          <el-table-column prop="sessionCode" label="记录编号" min-width="190" show-overflow-tooltip />
          <el-table-column prop="clientCode" label="客户端" min-width="150" />
          <el-table-column prop="projectCode" label="项目" min-width="130" />
          <el-table-column prop="requirementNo" label="最近需求" min-width="150" />
          <el-table-column prop="rootRequirementNo" label="根需求" min-width="150" />
          <el-table-column prop="sessionType" label="运行时" width="120" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'RUNNING' ? 'warning' : row.status === 'BLOCKED' ? 'danger' : 'success'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastActiveTime" label="最后执行时间" min-width="170" />
          <el-table-column prop="createdAt" label="首次保存时间" min-width="170" />
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { deleteAgent, fetchAgents } from '@/api/agent'
import { fetchClientNodes } from '@/api/client'
import { fetchAgentSessions } from '@/api/session'
import type { Agent } from '@/types/agent'
import type { ClientNode } from '@/types/client'
import type { AgentSession } from '@/types/session'
import { agentEngineTypeLabelMap, agentRoleOptions, agentStatusOptions, mcpTransportProtocolLabelMap } from '@/types/options'

interface AgentClientHeartbeat {
  clientCode: string
  clientName: string
  lastHeartbeatTime?: string
  lastSeenTime?: string
  workspaceDir?: string
}

const router = useRouter()
const list = ref<Agent[]>([])
const clientNodes = ref<ClientNode[]>([])
const agentSessions = ref<AgentSession[]>([])
const selectedAgent = ref<Agent | null>(null)
const loading = ref(false)
const sessionLoading = ref(false)
const sessionDrawerVisible = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filters = ref({ keyword: '', agentRole: '', status: '' })
let clientHeartbeatTimer: number | undefined

const filteredList = computed(() => {
  const keyword = filters.value.keyword.trim().toLowerCase()
  return list.value.filter((item) => {
    const matchesKeyword = !keyword || [item.agentCode, item.agentName, item.projectCode, item.agentEngineType].some((value) => value?.toLowerCase().includes(keyword))
    const matchesRole = !filters.value.agentRole || item.agentRole === filters.value.agentRole
    const matchesStatus = !filters.value.status || item.status === filters.value.status
    return matchesKeyword && matchesRole && matchesStatus
  })
})
const pagedList = computed(() => filteredList.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const sessionDrawerTitle = computed(() => selectedAgent.value ? `Agent 会话记录 - ${selectedAgent.value.agentName || selectedAgent.value.agentCode}` : 'Agent 会话记录')
const agentClientHeartbeatMap = computed(() => {
  const map = new Map<string, AgentClientHeartbeat[]>()
  clientNodes.value.forEach((client) => {
    ;(client.agents || []).forEach((agent) => {
      const current = map.get(agent.agentCode) || []
      current.push({
        clientCode: client.clientCode,
        clientName: client.clientName,
        lastHeartbeatTime: client.lastHeartbeatTime,
        lastSeenTime: agent.lastSeenTime,
        workspaceDir: agent.workspaceDir,
      })
      map.set(agent.agentCode, current)
    })
  })
  map.forEach((items) => {
    items.sort((left, right) => (right.lastHeartbeatTime || '').localeCompare(left.lastHeartbeatTime || ''))
  })
  return map
})

onMounted(async () => {
  await loadList()
  clientHeartbeatTimer = window.setInterval(loadClientHeartbeats, 10000)
})

onBeforeUnmount(() => {
  if (clientHeartbeatTimer) {
    window.clearInterval(clientHeartbeatTimer)
  }
})

async function loadList() {
  loading.value = true
  try {
    const [agents, clients] = await Promise.all([fetchAgents(), fetchClientNodes().catch(() => [])])
    list.value = agents
    clientNodes.value = clients
  } finally {
    loading.value = false
  }
}

async function loadClientHeartbeats() {
  clientNodes.value = await fetchClientNodes().catch(() => [])
}

async function openAgentSessions(row: Agent) {
  selectedAgent.value = row
  sessionDrawerVisible.value = true
  await loadAgentSessions(row.agentCode)
}

async function loadAgentSessions(agentCode = selectedAgent.value?.agentCode) {
  if (!agentCode) {
    return
  }
  sessionLoading.value = true
  try {
    agentSessions.value = await fetchAgentSessions({ agentCode })
  } finally {
    sessionLoading.value = false
  }
}

function getAgentClientHeartbeats(agentCode: string) {
  return agentClientHeartbeatMap.value.get(agentCode) || []
}

function latestAgentClientHeartbeat(agentCode: string) {
  return getAgentClientHeartbeats(agentCode)[0]
}

function formatClientNames(items: AgentClientHeartbeat[]) {
  return items.map((item) => item.clientName || item.clientCode).join('、')
}

async function handleDelete(row: Agent) {
  try {
    await ElMessageBox.confirm(`确定删除 Agent ${row.agentName} (${row.agentCode}) 吗？`, '删除 Agent', {
      type: 'warning',
    })
    await deleteAgent(row.agentCode)
    ElMessage.success('删除成功')
    await loadList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}

function resetFilters() {
  filters.value = { keyword: '', agentRole: '', status: '' }
  page.value = 1
}
</script>

<style scoped>
.agent-heartbeat {
  display: grid;
  gap: 4px;
}

.agent-heartbeat__time {
  color: var(--admin-title);
  font-size: 13px;
  line-height: 1.2;
}

.agent-heartbeat__clients,
.agent-heartbeat__empty {
  color: var(--admin-muted);
  font-size: 12px;
}

.agent-heartbeat__clients {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-session-drawer {
  display: grid;
  gap: 14px;
}
</style>
