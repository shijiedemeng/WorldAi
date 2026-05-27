<template>
  <div class="admin-page client-page">
    <PageHeader
      title="在线客户端"
    >
      <el-button :loading="loading" @click="loadList()">刷新</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" :model="filters" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="客户端编码 / 名称 / 主机 / Agent" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" class="w-full">
            <el-option v-for="opt in clientNodeStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="client-stats">
      <el-card class="client-stat" shadow="never">
        <span>在线列表</span>
        <strong>{{ list.length }}</strong>
      </el-card>
      <el-card class="client-stat" shadow="never">
        <span>心跳正常</span>
        <strong>{{ onlineCount }}</strong>
      </el-card>
      <el-card class="client-stat" shadow="never">
        <span>已关联 Agent</span>
        <strong>{{ linkedAgentCount }}</strong>
      </el-card>
      <el-card class="client-stat" shadow="never">
        <span>未关联配置</span>
        <strong>{{ unlinkedAgentCount }}</strong>
      </el-card>
    </div>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>实时连接状态</strong>
          <span>共 {{ filteredList.length }} 台在线客户端。点击客户端编码或查看按钮进入详情。</span>
        </div>
      </div>

      <el-table :data="filteredList" v-loading="loading" row-key="clientCode" stripe>
        <el-table-column label="客户端编码" min-width="190">
          <template #default="{ row }">
            <el-button link type="primary" class="client-code-link" @click="openDetail(row)">{{ row.clientCode }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="clientName" label="客户端名称" min-width="170" />
        <el-table-column prop="osType" label="系统" width="100" />
        <el-table-column prop="hostName" label="主机名" min-width="150" />
        <el-table-column prop="ipAddress" label="IP" min-width="130" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="Agent 关联" width="160">
          <template #default="{ row }">{{ row.agents?.length || 0 }} 已关联 / {{ row.unlinkedAgents?.length || 0 }} 未关联</template>
        </el-table-column>
        <el-table-column prop="appVersion" label="客户端版本" min-width="130" />
        <el-table-column prop="lastHeartbeatTime" label="最后心跳" min-width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="72%" destroy-on-close>
      <div v-if="selectedClient" class="client-detail-drawer" v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户端编码">{{ selectedClient.clientCode }}</el-descriptions-item>
          <el-descriptions-item label="客户端名称">{{ selectedClient.clientName }}</el-descriptions-item>
          <el-descriptions-item label="系统">{{ selectedClient.osType }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="selectedClient.status" />
          </el-descriptions-item>
          <el-descriptions-item label="主机名">{{ selectedClient.hostName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ selectedClient.ipAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户端版本">{{ selectedClient.appVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后心跳">{{ selectedClient.lastHeartbeatTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="selectedClient.unlinkedAgents?.length"
          type="warning"
          show-icon
          :closable="false"
          title="客户端配置已读取，但有 Agent 没有关联"
          description="服务端只关联 Agent 管理中已经存在的编号；未存在的编号会保留在这里作为运行态提示。"
        />

        <section class="client-detail-block">
          <div class="client-detail-block__title">已关联 Agent</div>
          <el-table :data="selectedClient.agents || []" size="small" empty-text="暂无关联 Agent。请确认客户端本地配置里的 agentCode 已在 Agent 管理中存在。">
            <el-table-column prop="agentCode" label="Agent 编码" min-width="160" />
            <el-table-column prop="agentName" label="名称" min-width="140" />
            <el-table-column prop="projectCode" label="项目" min-width="120" />
            <el-table-column prop="agentRole" label="角色" width="100" />
            <el-table-column prop="status" label="Agent 状态" width="120">
              <template #default="{ row: agent }">
                <StatusTag :value="agent.status" />
              </template>
            </el-table-column>
            <el-table-column prop="enabledFlag" label="启用" width="90">
              <template #default="{ row: agent }">{{ agent.enabledFlag ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="skillCodes" label="技能" min-width="180" show-overflow-tooltip>
              <template #default="{ row: agent }">{{ (agent.skillCodes || []).join('、') || '-' }}</template>
            </el-table-column>
            <el-table-column prop="skillsDir" label="技能目录" min-width="240" show-overflow-tooltip />
            <el-table-column prop="workspaceDir" label="工作目录" min-width="240" show-overflow-tooltip />
            <el-table-column prop="workerCommand" label="启动命令" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row: agent }">
                <el-button link type="primary" @click="openCommandRecords(agent)">执行记录</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="client-detail-block">
          <div class="client-detail-block__title-row">
            <div class="client-detail-block__title">会话管理</div>
            <el-space wrap>
              <el-button size="small" type="primary" :disabled="!(selectedClient.agents || []).length" @click="openSessionDialog">
                新增会话
              </el-button>
            </el-space>
          </div>
          <el-table :data="clientSessions" size="small" empty-text="暂无会话。新增后由客户端创建真实会话并回填会话ID。">
            <el-table-column prop="sessionName" label="会话名称" min-width="150" />
            <el-table-column label="会话ID" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.sessionId || '创建中' }}</span>
                <div v-if="!row.sessionId && row.requestId" class="client-session-request">请求 {{ row.requestId }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="agentCode" label="Agent" min-width="150" />
            <el-table-column prop="sessionType" label="类型" width="120" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="defaultFlag" label="默认" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.defaultFlag" type="success" size="small">默认</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="workspaceDir" label="工作目录" min-width="240" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
            <el-table-column label="记录数" width="90">
              <template #default="{ row }">{{ sessionInterfaceEventCount(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <div class="session-actions">
                  <el-button
                    link
                    type="primary"
                    :disabled="row.defaultFlag || row.status !== 'READY' || !row.sessionId"
                    @click="handleSetDefaultSession(row.sessionId)"
                  >
                    设默认
                  </el-button>
                  <el-button link type="primary" @click="openSessionEvents(row)">收发记录</el-button>
                  <el-button link type="primary" @click="openAgentHistory(row)">智能体历史</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="client-detail-block">
          <div class="client-detail-block__title">未关联 Agent 配置</div>
          <el-table :data="selectedClient.unlinkedAgents || []" size="small" empty-text="配置里的 Agent 都已成功关联">
            <el-table-column prop="agentCode" label="Agent 编码" min-width="160" />
            <el-table-column prop="enabledFlag" label="启用" width="90">
              <template #default="{ row: agent }">{{ agent.enabledFlag ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="skillsDir" label="技能目录" min-width="240" show-overflow-tooltip />
            <el-table-column prop="workspaceDir" label="工作目录" min-width="240" show-overflow-tooltip />
            <el-table-column prop="workerCommand" label="启动命令" min-width="220" show-overflow-tooltip />
            <el-table-column prop="reason" label="未关联原因" min-width="240" show-overflow-tooltip />
          </el-table>
        </section>

        <section class="client-detail-block">
          <div class="client-detail-block__title">本机运行时</div>
          <el-table :data="selectedClient.runtimes || []" size="small" empty-text="暂无运行时探测结果">
            <el-table-column prop="agentType" label="类型" min-width="130" />
            <el-table-column prop="agentName" label="名称" min-width="140" />
            <el-table-column prop="agentVersion" label="版本" min-width="130" show-overflow-tooltip />
            <el-table-column prop="availableFlag" label="可用" width="90">
              <template #default="{ row: runtime }">{{ runtime.availableFlag ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="commandPath" label="命令路径" min-width="240" show-overflow-tooltip />
            <el-table-column prop="lastProbeTime" label="探测时间" min-width="170" />
          </el-table>
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="sessionDialogVisible" title="新增客户端会话" width="520px" destroy-on-close>
      <el-form :model="sessionForm" label-width="100px">
        <el-form-item label="Agent">
          <el-select v-model="sessionForm.agentCode" class="w-full" placeholder="选择已关联 Agent">
            <el-option
              v-for="agent in selectedClient?.agents || []"
              :key="agent.agentCode"
              :label="`${agent.agentName || agent.agentCode} (${agent.agentCode})`"
              :value="agent.agentCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="会话名称">
          <el-input v-model="sessionForm.sessionName" placeholder="例如 默认会话 / Qoder 会话" />
        </el-form-item>
        <el-form-item label="会话类型">
          <el-select v-model="sessionForm.sessionType" class="w-full">
            <el-option label="CLI Prompt" value="CLI_PROMPT" />
            <el-option label="ACP" value="ACP" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作目录">
          <el-input v-model="sessionForm.workspaceDir" placeholder="默认使用 Agent 配置工作目录" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="sessionForm.defaultFlag" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sessionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sessionSaving" @click="handleCreateSession">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="commandRecordsVisible"
      :title="commandRecordsTitle"
      width="1080px"
      destroy-on-close
      draggable
    >
      <el-table :data="selectedAgentCommands" size="small" empty-text="暂无下发记录">
        <el-table-column prop="commandId" label="命令编号" min-width="220" show-overflow-tooltip />
        <el-table-column prop="requirementNo" label="需求" min-width="150" />
        <el-table-column prop="linkId" label="链路" width="90" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="resultSummary" label="结果摘要" min-width="260" show-overflow-tooltip />
        <el-table-column prop="executionDetails" label="执行详情" min-width="260" show-overflow-tooltip />
        <el-table-column prop="deliverablePath" label="交付路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="sessionEventVisible" title="会话实际收发内容" width="900px" destroy-on-close>
      <div v-if="selectedSession" class="session-event-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Agent">{{ selectedSession.agentCode }}</el-descriptions-item>
          <el-descriptions-item label="会话ID">{{ selectedSession.sessionId || '创建中' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selectedSession.status }}</el-descriptions-item>
          <el-descriptions-item label="请求ID">{{ selectedSession.requestId || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="selectedSessionEventRows.length" class="session-chat session-chat--dialog">
          <article
            v-for="event in selectedSessionEventRows"
            :key="event.eventId"
            class="session-message"
            :class="messageClass(event)"
          >
            <div class="session-message__meta">
              <span>{{ directionLabel(event.direction) }}</span>
              <span>{{ eventTypeText(event.eventType) }}</span>
              <time>{{ event.createdAt }}</time>
            </div>
            <div class="session-message__bubble">
              <template v-if="event.summary">
                <div class="session-summary">
                  <div class="session-summary__title">{{ event.summary.title }}</div>
                  <div class="session-summary__grid">
                    <div v-if="event.summary.method"><span>请求</span><strong>{{ event.summary.method }} {{ event.summary.path }}</strong></div>
                    <div v-if="event.summary.sessionId"><span>真实会话</span><strong>{{ event.summary.sessionId }}</strong></div>
                    <div v-if="event.summary.commandId"><span>命令</span><strong>{{ event.summary.commandId }}</strong></div>
                    <div v-if="event.summary.requirementNo"><span>需求</span><strong>{{ event.summary.requirementNo }}</strong></div>
                    <div v-if="event.summary.linkId"><span>链路</span><strong>{{ event.summary.linkId }}</strong></div>
                    <div v-if="event.summary.agentCode"><span>Agent</span><strong>{{ event.summary.agentCode }}</strong></div>
                    <div v-if="event.summary.status"><span>状态</span><strong>{{ statusText(event.summary.status) }}</strong></div>
                    <div v-if="event.summary.runtimeType"><span>运行时</span><strong>{{ runtimeText(event.summary.runtimeType) }}</strong></div>
                    <div v-if="event.summary.workspaceDir"><span>工作目录</span><strong>{{ event.summary.workspaceDir }}</strong></div>
                    <div v-if="event.summary.resultSummary"><span>结果摘要</span><strong>{{ event.summary.resultSummary }}</strong></div>
                    <div v-if="event.summary.deliverablePath"><span>交付物</span><strong>{{ event.summary.deliverablePath }}</strong></div>
                    <div v-if="event.summary.errorMessage"><span>错误</span><strong>{{ event.summary.errorMessage }}</strong></div>
                  </div>
                  <div v-if="event.summary.prompt" class="session-summary__content">
                    <span>发送内容</span>
                    <pre>{{ formatContent(event.summary.prompt) }}</pre>
                  </div>
                  <div v-if="event.summary.text" class="session-summary__content">
                    <span>返回内容</span>
                    <pre>{{ formatContent(event.summary.text) }}</pre>
                  </div>
                  <div v-if="event.summary.thought" class="session-summary__content">
                    <span>思考/过程</span>
                    <pre>{{ formatContent(event.summary.thought) }}</pre>
                  </div>
                  <div v-if="event.summary.executionDetails" class="session-summary__content">
                    <span>执行详情</span>
                    <pre>{{ formatContent(event.summary.executionDetails) }}</pre>
                  </div>
                  <div v-if="event.summary.skills?.length" class="session-summary__skills">
                    <div class="session-summary__subtitle">初始化技能</div>
                    <div v-for="skill in event.summary.skills" :key="skill.skillCode || skill.skillName" class="session-skill">
                      <span class="session-skill__name">{{ skill.skillName || skill.skillCode }}</span>
                      <el-tag :type="skillTagType(skill)" size="small">{{ skillStatusText(skill) }}</el-tag>
                      <span v-if="skill.projectCode">项目：{{ skill.projectCode }}</span>
                      <span v-if="skill.agentRole">角色：{{ skill.agentRole }}</span>
                      <span v-if="skill.localPath">路径：{{ skill.localPath }}</span>
                      <span v-if="skill.reason">说明：{{ skillReasonText(skill.reason) }}</span>
                    </div>
                  </div>
                </div>
              </template>
              <pre v-else class="session-message__raw">{{ formatPayload(event.payload) }}</pre>
            </div>
          </article>
        </div>
        <EmptyBlock v-else description="暂无接口或会话控制收发内容。ACP 对话请在当前会话的智能体历史中查看。" />
      </div>
    </el-dialog>

    <el-dialog
      v-model="agentHistoryVisible"
      :title="selectedSession ? `智能体历史 - ${selectedSession.sessionName || selectedSession.sessionId || selectedSession.requestId}` : '智能体历史'"
      width="980px"
      destroy-on-close
      draggable
    >
      <div v-if="selectedSession" class="agent-session-card">
        <div class="agent-session-card__header">
          <div>
            <strong>{{ selectedSession.sessionName || '未命名会话' }}</strong>
            <small>{{ selectedSession.sessionId || (selectedSession.requestId ? `请求 ${selectedSession.requestId}` : '-') }}</small>
          </div>
          <StatusTag :value="selectedSession.status" />
        </div>
        <el-descriptions :column="2" size="small" border class="agent-session-meta">
          <el-descriptions-item label="Agent">{{ selectedSession.agentCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话类型">{{ selectedSession.sessionType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="运行时">{{ runtimeText(selectedSession.runtimeType) }}</el-descriptions-item>
          <el-descriptions-item label="默认">{{ selectedSession.defaultFlag ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ selectedSession.updatedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工作目录">{{ selectedSession.workspaceDir || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedSession.errorMessage" label="错误" :span="2">{{ selectedSession.errorMessage }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="selectedAgentHistoryRows.length" class="session-chat session-chat--dialog agent-session-card__chat">
          <article
            v-for="event in selectedAgentHistoryRows"
            :key="event.eventId"
            class="session-message"
            :class="messageClass(event)"
          >
            <div class="session-message__meta">
              <span>{{ directionLabel(event.direction) }}</span>
              <span>{{ eventTypeText(event.eventType) }}</span>
              <time>{{ event.createdAt }}</time>
            </div>
            <div class="session-message__bubble">
              <template v-if="event.summary">
                <div class="session-summary">
                  <div class="session-summary__title">{{ event.summary.title }}</div>
                  <div class="session-summary__grid">
                    <div v-if="event.summary.commandId"><span>命令</span><strong>{{ event.summary.commandId }}</strong></div>
                    <div v-if="event.summary.requirementNo"><span>需求</span><strong>{{ event.summary.requirementNo }}</strong></div>
                    <div v-if="event.summary.linkId"><span>链路</span><strong>{{ event.summary.linkId }}</strong></div>
                    <div v-if="event.summary.workspaceDir"><span>工作目录</span><strong>{{ event.summary.workspaceDir }}</strong></div>
                    <div v-if="event.summary.resultSummary"><span>结果摘要</span><strong>{{ event.summary.resultSummary }}</strong></div>
                    <div v-if="event.summary.errorMessage"><span>错误</span><strong>{{ event.summary.errorMessage }}</strong></div>
                  </div>
                  <div v-if="event.summary.prompt" class="session-summary__content">
                    <span>发送给智能体</span>
                    <pre>{{ formatContent(event.summary.prompt) }}</pre>
                  </div>
                  <div v-if="event.summary.text" class="session-summary__content">
                    <span>智能体返回</span>
                    <pre>{{ formatContent(event.summary.text) }}</pre>
                  </div>
                  <div v-if="event.summary.thought" class="session-summary__content">
                    <span>智能体思考/过程</span>
                    <pre>{{ formatContent(event.summary.thought) }}</pre>
                  </div>
                  <div v-if="event.summary.executionDetails" class="session-summary__content">
                    <span>执行详情</span>
                    <pre>{{ formatContent(event.summary.executionDetails) }}</pre>
                  </div>
                </div>
              </template>
              <pre v-else class="session-message__raw">{{ formatPayload(event.payload) }}</pre>
            </div>
          </article>
        </div>
        <EmptyBlock v-else description="该会话暂无智能体对话内容" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  createClientSession,
  fetchClientCommands,
  fetchClientNode,
  fetchClientNodes,
  fetchClientSessions,
  setDefaultClientSession,
} from '@/api/client'
import type {
  ClientAgentSession,
  ClientCommand,
  ClientControllableAgent,
  ClientNode,
  ClientSessionEvent,
  CreateClientAgentSessionPayload,
} from '@/types/client'
import { clientNodeStatusOptions } from '@/types/options'
import { addConsolePushListener, type ConsolePushEvent } from '@/composables/useConsolePush'

interface SessionEventRow extends ClientSessionEvent {
  agentCode: string
  sessionName: string
  sessionId?: string
  requestId?: string
  sessionType?: string
  runtimeType?: string
  summary?: SessionEventSummary | null
}

interface SessionEventSummary {
  title: string
  method?: string
  path?: string
  sessionId?: string
  commandId?: string
  requirementNo?: string
  linkId?: string
  agentCode?: string
  status?: string
  runtimeType?: string
  workspaceDir?: string
  resultSummary?: string
  deliverablePath?: string
  errorMessage?: string
  prompt?: string
  text?: string
  thought?: string
  executionDetails?: string
  skills?: SessionSkillSummary[]
}

interface SessionSkillSummary {
  skillCode?: string
  skillName?: string
  localPath?: string
  projectCode?: string
  agentRole?: string
  matched?: boolean
  loaded?: boolean
  reason?: string
}

interface DisplaySessionEvent extends ClientSessionEvent {
  summary?: SessionEventSummary | null
}

const ACP_CONVERSATION_EVENT_TYPES = new Set(['AGENT_PROMPT', 'AGENT_RESPONSE', 'AGENT_ERROR'])

const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const sessionDialogVisible = ref(false)
const sessionEventVisible = ref(false)
const agentHistoryVisible = ref(false)
const commandRecordsVisible = ref(false)
const sessionSaving = ref(false)
const list = ref<ClientNode[]>([])
const clientSessions = ref<ClientAgentSession[]>([])
const clientCommands = ref<ClientCommand[]>([])
const selectedClient = ref<ClientNode | null>(null)
const selectedSession = ref<ClientAgentSession | null>(null)
const selectedCommandAgent = ref<ClientControllableAgent | null>(null)
const sessionForm = ref<CreateClientAgentSessionPayload>({
  agentCode: '',
  sessionName: '',
  sessionType: 'CLI_PROMPT',
  defaultFlag: true,
  workspaceDir: '',
})
const filters = ref({ keyword: '', status: '' })
let refreshTimer: number | undefined
let removeConsolePushListener: (() => void) | undefined

const filteredList = computed(() => {
  const keyword = filters.value.keyword.trim().toLowerCase()
  return list.value.filter((item) => {
    const keywordFields = [
      item.clientCode,
      item.clientName,
      item.hostName,
      item.ipAddress,
      item.supportedAgentTypes,
      ...(item.agents || []).map((agent) => `${agent.agentCode} ${agent.agentName || ''} ${agent.projectCode || ''}`),
      ...(item.unlinkedAgents || []).map((agent) => `${agent.agentCode} ${agent.reason || ''}`),
    ]
    const matchesKeyword = !keyword || keywordFields.some((value) => value?.toLowerCase().includes(keyword))
    const matchesStatus = !filters.value.status || item.status === filters.value.status
    return matchesKeyword && matchesStatus
  })
})

const onlineCount = computed(() => list.value.filter((item) => item.status === 'ONLINE').length)
const linkedAgentCount = computed(() => list.value.reduce((total, item) => total + (item.agents?.length || 0), 0))
const unlinkedAgentCount = computed(() => list.value.reduce((total, item) => total + (item.unlinkedAgents?.length || 0), 0))
const detailTitle = computed(() => (selectedClient.value ? `客户端详情 - ${selectedClient.value.clientCode}` : '客户端详情'))
const selectedAgentCommands = computed(() => {
  const agentCode = selectedCommandAgent.value?.agentCode
  return agentCode ? clientCommands.value.filter((item) => item.agentCode === agentCode) : []
})
const commandRecordsTitle = computed(() => {
  if (!selectedCommandAgent.value) {
    return '执行记录'
  }
  const agentName = selectedCommandAgent.value.agentName ? ` / ${selectedCommandAgent.value.agentName}` : ''
  return `执行记录 - ${selectedCommandAgent.value.agentCode}${agentName}`
})
const selectedSessionEventRows = computed<DisplaySessionEvent[]>(() => (selectedSession.value?.events || []).map((event) => ({
  ...event,
  summary: buildSessionEventSummary(event, selectedSession.value?.sessionType),
})).filter((event) => isInterfaceSessionEvent(event)))
const selectedAgentHistoryRows = computed<SessionEventRow[]>(() => (selectedSession.value?.events || [])
  .filter(isAgentConversationEvent)
  .map((event): SessionEventRow => ({
    ...event,
    agentCode: selectedSession.value?.agentCode || '',
    sessionName: selectedSession.value?.sessionName || '',
    sessionId: selectedSession.value?.sessionId,
    requestId: selectedSession.value?.requestId,
    sessionType: selectedSession.value?.sessionType,
    runtimeType: selectedSession.value?.runtimeType,
    summary: buildSessionEventSummary(event, selectedSession.value?.sessionType),
  }))
  .sort((left, right) => (left.createdAt || '').localeCompare(right.createdAt || '')))

onMounted(async () => {
  removeConsolePushListener = addConsolePushListener(handleConsolePushEvent)
  await loadList()
  refreshTimer = window.setInterval(refreshRuntimeView, 10000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
  removeConsolePushListener?.()
})

async function loadList(showLoading = true) {
  if (showLoading) {
    loading.value = true
  }
  try {
    list.value = await fetchClientNodes()
  } finally {
    if (showLoading) {
      loading.value = false
    }
  }
}

async function refreshRuntimeView() {
  await loadList(false)
  if (detailVisible.value && selectedClient.value?.clientCode) {
    await refreshDetail(selectedClient.value.clientCode, false)
  }
}

function handleConsolePushEvent(event: ConsolePushEvent) {
  if (event.type === 'CLIENT_STATUS_CHANGED') {
    void refreshRuntimeView()
  }
}

async function openDetail(row: ClientNode) {
  selectedClient.value = row
  detailVisible.value = true
  await refreshDetail(row.clientCode)
}

async function refreshDetail(clientCode: string, showLoading = true) {
  if (showLoading) {
    detailLoading.value = true
  }
  try {
    selectedClient.value = await fetchClientNode(clientCode)
    await Promise.all([loadSessions(clientCode), loadCommands(clientCode)])
  } finally {
    if (showLoading) {
      detailLoading.value = false
    }
  }
}

async function loadSessions(clientCode: string) {
  clientSessions.value = await fetchClientSessions(clientCode)
  if (selectedSession.value) {
    selectedSession.value = clientSessions.value.find((item) =>
      (selectedSession.value?.sessionId && item.sessionId === selectedSession.value.sessionId)
      || (selectedSession.value?.requestId && item.requestId === selectedSession.value.requestId),
    ) || selectedSession.value
  }
}

async function loadCommands(clientCode: string) {
  clientCommands.value = await fetchClientCommands(clientCode)
}

function openSessionDialog() {
  const firstAgent = selectedClient.value?.agents?.[0]
  sessionForm.value = {
    agentCode: firstAgent?.agentCode || '',
    sessionName: '默认会话',
    sessionType: 'CLI_PROMPT',
    defaultFlag: true,
    workspaceDir: firstAgent?.workspaceDir || '',
  }
  sessionDialogVisible.value = true
}

async function handleCreateSession() {
  if (!selectedClient.value || !sessionForm.value.agentCode) {
    return
  }
  sessionSaving.value = true
  try {
    await createClientSession(selectedClient.value.clientCode, sessionForm.value)
    ElMessage.success('已下发创建会话，等待客户端回填会话ID')
    sessionDialogVisible.value = false
    await loadSessions(selectedClient.value.clientCode)
  } finally {
    sessionSaving.value = false
  }
}

async function handleSetDefaultSession(sessionId: string) {
  if (!selectedClient.value) {
    return
  }
  await setDefaultClientSession(selectedClient.value.clientCode, sessionId)
  ElMessage.success('默认会话已更新')
  await loadSessions(selectedClient.value.clientCode)
}

function openSessionEvents(row: ClientAgentSession) {
  selectedSession.value = row
  sessionEventVisible.value = true
}

function openAgentHistory(row: ClientAgentSession) {
  selectedSession.value = row
  agentHistoryVisible.value = true
}

function openCommandRecords(agent: ClientControllableAgent) {
  selectedCommandAgent.value = agent
  commandRecordsVisible.value = true
}

function sessionInterfaceEventCount(session: ClientAgentSession) {
  return (session.events || []).filter(isInterfaceSessionEvent).length
}

function isInterfaceSessionEvent(event: Pick<ClientSessionEvent, 'eventType'>) {
  return !ACP_CONVERSATION_EVENT_TYPES.has(event.eventType)
}

function isAgentConversationEvent(event: Pick<ClientSessionEvent, 'eventType'>) {
  return ACP_CONVERSATION_EVENT_TYPES.has(event.eventType)
}

function directionLabel(direction?: string) {
  return direction === 'OUT' ? '发送' : '接收'
}

function eventTypeText(eventType?: string) {
  const map: Record<string, string> = {
    CREATE_SESSION: '创建会话请求',
    SESSION_CREATE_RESULT: '会话创建结果',
    EXECUTE_COMMAND: '下发执行命令',
    COMMAND_STARTED: '执行开始回执',
    COMMAND_COMPLETED: '执行完成回执',
    AGENT_PROMPT: '发送给智能体',
    AGENT_RESPONSE: '智能体返回',
    AGENT_ERROR: '智能体错误',
    AGENT_EVENT: '智能体事件',
  }
  return eventType ? (map[eventType] || eventType) : '-'
}

function messageClass(event: Pick<ClientSessionEvent, 'direction'>) {
  return event.direction === 'OUT' ? 'session-message--out' : 'session-message--in'
}

function buildSessionEventSummary(event: ClientSessionEvent, sessionType?: string): SessionEventSummary | null {
  const root = parseObjectPayload(event.payload)
  if (!root) {
    return null
  }
  const data = asObject(root.data)
  const body = asObject(root.body)
  if (event.eventType === 'CREATE_SESSION' && data) {
    return {
      title: '创建会话请求',
      sessionId: readString(data, 'requestId'),
      status: readString(data, 'status'),
      runtimeType: readString(data, 'runtimeType'),
      workspaceDir: readString(data, 'workspaceDir'),
      errorMessage: readString(data, 'errorMessage'),
    }
  }
  if (event.eventType === 'EXECUTE_COMMAND' && data) {
    return {
      title: '接口下发执行命令',
      commandId: readText(data, 'commandId'),
      requirementNo: readText(data, 'requirementNo'),
      linkId: readText(data, 'linkId'),
      agentCode: readText(data, 'agentCode'),
      status: readText(data, 'status'),
      prompt: readText(data, 'prompt'),
    }
  }
  if (event.eventType === 'COMMAND_STARTED') {
    return {
      title: '客户端开始执行回执',
      method: readString(root, 'method'),
      path: readString(root, 'path'),
      status: 'RUNNING',
    }
  }
  if (event.eventType === 'COMMAND_COMPLETED') {
    const target = body || root
    return {
      title: '客户端完成执行回执',
      method: readString(root, 'method'),
      path: readString(root, 'path'),
      status: readText(target, 'status'),
      resultSummary: readText(target, 'resultSummary'),
      executionDetails: readText(target, 'executionDetails'),
      deliverablePath: readText(target, 'deliverablePath'),
    }
  }
  if (event.eventType === 'AGENT_PROMPT') {
    const acpSession = sessionType === 'ACP'
    return {
      title: acpSession ? '发送给 ACP 会话' : '发送给智能体',
      commandId: readText(root, 'commandId'),
      requirementNo: readText(root, 'requirementNo'),
      linkId: readText(root, 'linkId'),
      agentCode: readText(root, 'agentCode'),
      workspaceDir: readText(root, 'workspaceDir'),
      prompt: readText(root, 'prompt'),
    }
  }
  if (event.eventType === 'AGENT_RESPONSE') {
    const acpSession = sessionType === 'ACP'
    return {
      title: acpSession ? 'ACP 会话返回' : '智能体返回',
      commandId: readText(root, 'commandId'),
      requirementNo: readText(root, 'requirementNo'),
      linkId: readText(root, 'linkId'),
      status: readText(root, 'status'),
      resultSummary: readText(root, 'resultSummary'),
      text: readText(root, 'text') || readText(root, 'stdout'),
      thought: readText(root, 'thought'),
      executionDetails: readText(root, 'executionDetails') || readText(root, 'stderr'),
    }
  }
  if (event.eventType === 'AGENT_ERROR') {
    const errorType = readText(root, 'errorType')
    const message = readText(root, 'message')
    const acpSession = sessionType === 'ACP'
    return {
      title: acpSession ? 'ACP 会话错误' : '智能体执行错误',
      commandId: readText(root, 'commandId'),
      requirementNo: readText(root, 'requirementNo'),
      linkId: readText(root, 'linkId'),
      errorMessage: [errorType, message].filter(Boolean).join('：') || undefined,
    }
  }
  const target = body || root
  const skills = readSkills(target.initializedSkills)
  const looksLikeSessionResult = event.eventType === 'SESSION_CREATE_RESULT'
    || Boolean(readString(target, 'sessionId'))
    || Boolean(readString(target, 'runtimeType'))
    || skills.length > 0
  if (!looksLikeSessionResult) {
    return {
      title: eventTypeText(event.eventType),
      method: readString(root, 'method'),
      path: readString(root, 'path'),
      commandId: readText(target, 'commandId'),
      requirementNo: readText(target, 'requirementNo'),
      linkId: readText(target, 'linkId'),
      agentCode: readText(target, 'agentCode'),
      status: readText(target, 'status'),
      resultSummary: readText(target, 'resultSummary'),
      executionDetails: readText(target, 'executionDetails') || readableObjectLines(target),
    }
  }
  const status = readString(target, 'status')
  return {
    title: status === 'READY' ? '会话创建成功' : status === 'FAILED' ? '会话创建失败' : '会话创建结果',
    method: readString(root, 'method'),
    path: readString(root, 'path'),
    sessionId: readString(target, 'sessionId'),
    status,
    runtimeType: readString(target, 'runtimeType'),
    errorMessage: readString(target, 'errorMessage'),
    skills,
  }
}

function parseObjectPayload(payload?: string) {
  if (!payload) {
    return null
  }
  try {
    return asObject(JSON.parse(payload))
  } catch {
    return null
  }
}

function asObject(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function readString(record: Record<string, unknown>, key: string) {
  const value = record[key]
  return typeof value === 'string' && value.trim() ? value : undefined
}

function readText(record: Record<string, unknown>, key: string) {
  const value = record[key]
  if (typeof value === 'string') {
    return value.trim() || undefined
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return undefined
}

function readBoolean(record: Record<string, unknown>, key: string) {
  const value = record[key]
  return typeof value === 'boolean' ? value : undefined
}

function readableObjectLines(record: Record<string, unknown>) {
  return Object.entries(record)
    .filter(([, value]) => value !== null && value !== undefined && typeof value !== 'object')
    .slice(0, 8)
    .map(([key, value]) => `${key}：${String(value)}`)
    .join('\n') || undefined
}

function readSkills(value: unknown): SessionSkillSummary[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .map((item): SessionSkillSummary | null => {
      const record = asObject(item)
      if (!record) {
        return null
      }
      return {
        skillCode: readString(record, 'skillCode'),
        skillName: readString(record, 'skillName'),
        localPath: readString(record, 'localPath'),
        projectCode: readString(record, 'projectCode'),
        agentRole: readString(record, 'agentRole'),
        matched: readBoolean(record, 'matched'),
        loaded: readBoolean(record, 'loaded'),
        reason: readString(record, 'reason'),
      }
    })
    .filter((item): item is SessionSkillSummary => item !== null)
}

function statusText(status?: string) {
  const map: Record<string, string> = {
    READY: '就绪',
    FAILED: '失败',
    CREATING: '创建中',
    PENDING: '等待中',
    RUNNING: '执行中',
    SUCCESS: '成功',
    BLOCKED: '阻塞',
    CANCELLED: '已取消',
    TIMEOUT: '超时',
  }
  return status ? `${map[status] || status} (${status})` : '-'
}

function runtimeText(runtimeType?: string) {
  const map: Record<string, string> = {
    QODER_CLI: 'Qoder CLI',
    CODEX_CLI: 'Codex CLI',
    CLAUDE_CODE: 'Claude Code',
  }
  return runtimeType ? `${map[runtimeType] || runtimeType} (${runtimeType})` : '-'
}

function skillStatusText(skill: SessionSkillSummary) {
  if (skill.loaded === false) {
    return '未加载'
  }
  if (skill.matched === false) {
    return '未匹配'
  }
  if (skill.matched === true) {
    return '已初始化'
  }
  return '已返回'
}

function skillTagType(skill: SessionSkillSummary) {
  if (skill.loaded === false) {
    return 'warning'
  }
  if (skill.matched === false) {
    return 'danger'
  }
  return 'success'
}

function skillReasonText(reason?: string) {
  if (!reason) {
    return ''
  }
  if (reason.includes('session document context is empty')) {
    return '项目通用文档和开发文档为空，所以没有内容需要加载'
  }
  if (reason.includes('development document context is empty')) {
    return '项目开发文档为空，所以没有内容需要加载'
  }
  return reason
}

function formatPayload(payload?: string) {
  if (!payload) {
    return '-'
  }
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

function formatContent(value?: string, limit = 8000) {
  const text = value?.trim() || ''
  if (!text) {
    return '-'
  }
  return text.length > limit ? `${text.slice(0, limit)}\n...` : text
}

function resetFilters() {
  filters.value = { keyword: '', status: '' }
}
</script>

<style scoped>
.client-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.client-stat :deep(.el-card__body) {
  display: grid;
  gap: 8px;
}

.client-stat span {
  color: var(--admin-muted);
  font-size: 13px;
}

.client-stat strong {
  color: var(--admin-title);
  font-size: 30px;
  line-height: 1;
}

.client-session-request {
  color: var(--admin-muted);
  font-size: 12px;
  line-height: 1.4;
}

.session-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.session-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0;
}

.client-detail-drawer {
  display: grid;
  gap: 18px;
}

.session-event-dialog {
  display: grid;
  gap: 14px;
}

.session-chat {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 620px;
  overflow: auto;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background:
    radial-gradient(circle at 20% 0%, rgba(59, 130, 246, 0.08), transparent 32%),
    linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

.session-chat--dialog {
  max-height: 58vh;
}

.session-message {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 82%;
}

.session-message--out {
  align-self: flex-end;
  align-items: flex-end;
}

.session-message--in {
  align-self: flex-start;
  align-items: flex-start;
}

.session-message__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--admin-muted);
  font-size: 12px;
}

.session-message__meta span {
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
}

.session-message__bubble {
  max-width: 100%;
  padding: 12px 14px;
  border-radius: 16px;
  line-height: 1.5;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
}

.session-message__raw {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}

.session-summary {
  display: grid;
  gap: 10px;
  min-width: min(540px, 70vw);
  font-size: 13px;
}

.session-summary__title {
  font-size: 15px;
  font-weight: 700;
}

.session-summary__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.session-summary__grid div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.session-summary__grid span,
.session-summary__subtitle {
  opacity: 0.72;
  font-size: 12px;
}

.session-summary__grid strong {
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 600;
}

.session-summary__content {
  display: grid;
  gap: 6px;
}

.session-summary__content span {
  opacity: 0.72;
  font-size: 12px;
}

.session-summary__content pre {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.65;
}

.session-summary__skills {
  display: grid;
  gap: 8px;
}

.session-skill {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  overflow-wrap: anywhere;
}

.session-message--in .session-skill {
  background: #f1f5f9;
}

.session-skill__name {
  font-weight: 700;
}

.session-message--out .session-message__bubble {
  border-top-right-radius: 4px;
  background: #1d4ed8;
  color: #eff6ff;
}

.session-message--in .session-message__bubble {
  border-top-left-radius: 4px;
  background: #ffffff;
  color: #111827;
}

.client-detail-block {
  display: grid;
  gap: 10px;
}

.client-detail-block__title {
  color: var(--admin-title);
  font-weight: 700;
}

.client-detail-block__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.agent-session-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: #ffffff;
}

.agent-session-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.agent-session-card__header > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.agent-session-card__header strong,
.agent-session-card__header small {
  overflow-wrap: anywhere;
}

.agent-session-card__header small {
  color: var(--admin-muted);
}

.agent-session-card__chat {
  max-height: 52vh;
}

.agent-session-meta :deep(.el-descriptions__content) {
  overflow-wrap: anywhere;
}

.client-code-link {
  padding-left: 0;
  padding-right: 0;
}

.w-full {
  width: 100%;
}
</style>
