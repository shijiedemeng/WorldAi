<template>
  <div>
    <PageHeader :title="requirement?.title || '需求详情'">
      <el-button @click="goRequirementList">
        返回列表
      </el-button>
      <el-button :loading="loading" @click="loadData">
        刷新
      </el-button>
      <el-button
        v-if="requirement?.parentRequirementNo"
        @click="goParentRequirement"
      >
        返回上一层
      </el-button>
      <el-button
        v-if="canOpenExecute"
        type="success"
        :disabled="pageLocked"
        @click="openExecuteDialog()"
      >
        {{ executeButtonLabel }}
      </el-button>
      <el-button
        v-if="requirement?.requirementType === 'MASTER'"
        type="warning"
        :disabled="children.length > 0 || requirement?.status === 'ANALYZING' || pageLocked || taskOperationLocked"
        :loading="aiAnalyzing"
        @click="openAiAnalyzeDialog"
      >
        AI分析
      </el-button>
      <el-button
        v-if="canShowHeaderCreateChild"
        type="primary"
        :disabled="pageLocked || taskOperationLocked"
        @click="goCreateChild"
      >
        新建子模块
      </el-button>
      <el-button
        v-if="requirement"
        type="primary"
        plain
        :disabled="pageLocked"
        @click="openKnowledgeDialog"
      >
        {{ confirmedKnowledgeItem ? '查看向量' : '新增向量' }}
      </el-button>
    </PageHeader>

    <div
      v-if="requirement"
      class="requirement-detail__content"
      v-loading="pageLocked"
      :element-loading-text="pageLoadingText"
    >
      <el-alert
        v-if="requirement.status === 'ANALYZING'"
        type="warning"
        show-icon
        :closable="false"
        title="当前总需求正在 AI 分析，页面已锁定"
        description="分析完成后会自动刷新子模块和状态，在此期间不能执行编辑、删除或下发操作。"
      />
      <el-alert
        v-if="taskOperationLocked"
        type="warning"
        show-icon
        :closable="false"
        title="当前存在执行中的子模块"
        description="执行期间不允许新增、编辑、删除子模块或调整工作流关系，避免并发修改影响智能体执行上下文。"
      />

      <RequirementBaseInfoCard
        :requirement="requirement"
        :editable-description="canEditDescription"
        @edit-description="openDescriptionEditor"
      />

      <el-card v-if="requirement.requirementType === 'MASTER' && requirement.executionMode === 'WORKFLOW'" class="workflow-card">
        <template #header>
          <div class="workflow-header">
            <div>
              <div class="workflow-header__title">工作流编排</div>
              <div class="workflow-header__meta">
                {{ workflowSummary }} · 右键画布空白处或拖线到空白处快速创建子模块
              </div>
            </div>
            <div class="workflow-card__actions">
              <el-button
                type="primary"
                plain
                size="small"
                :disabled="pageLocked || taskOperationLocked"
                @click="openWorkflowChildDialog()"
              >
                新建子模块
              </el-button>
              <el-button
                type="primary"
                size="small"
                plain
                :disabled="pageLocked || taskOperationLocked || children.length === 0 || !workflowDirty"
                :loading="workflowSaving"
                @click="handleSaveWorkflow"
              >
                保存流程
              </el-button>
              <el-button
                type="success"
                :loading="workflowDispatching"
                :disabled="pageLocked || children.length === 0 || workflowRunning"
                @click="handleDispatchWorkflowReady"
              >
                从头开始执行
              </el-button>
            </div>
          </div>
        </template>

        <div v-if="workflowColumns.length === 0" class="workflow-empty" @contextmenu.prevent="handleWorkflowPaneContextMenu">
          <div class="workflow-empty__title">暂无子模块</div>
          <div class="workflow-empty__desc">点击“新建子模块”或在画布区域右键即可快速创建。</div>
          <el-button type="primary" :disabled="pageLocked || taskOperationLocked" @click="openWorkflowChildDialog()">新建子模块</el-button>
        </div>

        <div v-else class="workflow-flow-shell">
          <VueFlow
            v-model:nodes="flowNodes"
            v-model:edges="flowEdges"
            class="workflow-flow"
            :fit-view-on-init="true"
            :min-zoom="0.35"
            :max-zoom="1.4"
            :delete-key-code="null"
            @connect-start="handleWorkflowConnectStart"
            @connect="handleWorkflowConnect"
            @connect-end="handleWorkflowConnectEnd"
            @edge-click="handleWorkflowEdgeClick"
            @pane-click="clearSelectedWorkflowEdge"
            @pane-context-menu="handleWorkflowPaneContextMenu"
          >
            <template #edge-workflowEdge="edgeProps">
              <BaseEdge
                :id="edgeProps.id"
                :path="workflowEdgePath(edgeProps)[0]"
                :marker-end="edgeProps.markerEnd"
                :style="edgeProps.style"
                :interaction-width="24"
              />
              <EdgeLabelRenderer>
                <div
                  v-if="selectedWorkflowEdgeId === edgeProps.id"
                  class="workflow-edge-toolbar"
                  :style="workflowEdgeToolbarStyle(edgeProps)"
                  @click.stop
                >
                  <span>{{ workflowEdgeLabelById(edgeProps.id) }}</span>
                  <el-button
                    link
                    type="danger"
                    :disabled="pageLocked || taskOperationLocked"
                    @click="removeWorkflowFlowEdge(edgeProps.id)"
                  >
                    删除
                  </el-button>
                </div>
              </EdgeLabelRenderer>
            </template>
            <template #node-requirementWorkflow="{ data }">
              <div
                class="workflow-node"
                :class="[
                  `is-${data.view.node.status.toLowerCase()}`,
                  {
                    'is-ready': data.view.ready,
                    'is-editable': data.view.node.status === 'PENDING' && !pageLocked && !taskOperationLocked && !data.view.node.executionMarker,
                  },
                ]"
                @dblclick.stop="handleWorkflowNodeDoubleClick(data.view.node)"
              >
                <Handle type="target" :position="Position.Left" />
                <Handle type="source" :position="Position.Right" />
                <div class="workflow-node__title">{{ data.view.node.requirementNo }} / {{ data.view.node.title }}</div>
                <div class="workflow-node__tags">
                  <StatusTag :value="data.view.node.status" />
                  <el-tag v-if="data.view.ready" size="small" type="success">已就绪</el-tag>
                  <el-tag v-if="data.view.node.executionMarker" size="small" type="danger">
                    执行中
                  </el-tag>
                  <el-tag v-if="data.view.start" size="small" type="info">起点</el-tag>
                  <el-tag v-if="data.view.node.reviewRequiredFlag && !data.view.node.reviewApprovedFlag" size="small" type="warning">
                    待人工审核
                  </el-tag>
                  <el-tag v-if="data.view.node.reviewRequiredFlag && data.view.node.reviewApprovedFlag" size="small" type="success">
                    审核通过
                  </el-tag>
                  <el-tag size="small" :type="data.view.node.resultExtractableFlag === false ? 'info' : 'primary'">
                    {{ data.view.node.resultExtractableFlag === false ? '回执不提取' : '回执可提取' }}
                  </el-tag>
                  <el-tag v-if="data.view.node.mcpFileSearchEnabledFlag" size="small" type="success">
                    MCP检索
                  </el-tag>
                  <el-tag v-if="data.view.node.projectKnowledgeSearchEnabledFlag" size="small" type="success">
                    储备库
                  </el-tag>
                </div>
                <div class="workflow-node__meta">
                  <span>Agent：{{ data.view.node.mainAgentCode || '-' }}</span>
                  <span>前置：{{ requirementNames(data.view.previous) }}</span>
                  <span>下一步：{{ requirementNames(data.view.next) }}</span>
                </div>
                <div class="workflow-node__actions">
                  <el-button link type="primary" @click="$router.push({ name: 'requirement-detail', params: { requirementNo: data.view.node.requirementNo } })">
                    查看
                  </el-button>
                  <el-button
                    v-if="data.view.node.status === 'IN_PROGRESS'"
                    link
                    type="warning"
                    :disabled="pageLocked || Boolean(data.view.node.executionMarker)"
                    @click="handleResetChildExecution(data.view.node)"
                  >
                    重置
                  </el-button>
                  <el-button
                    v-if="data.view.node.status === 'PENDING'"
                    link
                    type="primary"
                    :disabled="pageLocked || taskOperationLocked || Boolean(data.view.node.executionMarker)"
                    @click="openWorkflowChildDialog(data.view.node)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-if="canApproveWorkflowNode(data.view)"
                    link
                    type="success"
                    :disabled="pageLocked || workflowDispatching"
                    @click="handleApproveWorkflowReview(data.view.node)"
                  >
                    审核通过
                  </el-button>
                  <el-button
                    v-if="data.view.node.status === 'PENDING'"
                    link
                    type="danger"
                    :disabled="pageLocked || taskOperationLocked || Boolean(data.view.node.executionMarker)"
                    @click="handleDeleteChild(data.view.node.requirementNo)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
            </template>
          </VueFlow>
          <div class="workflow-edge-list">
            <div class="workflow-edge-list__title">关系</div>
            <el-table
              :data="flowEdgeRows"
              size="small"
              empty-text="暂无流程关系，所有待执行子模块都会作为起点"
              :row-class-name="workflowEdgeRowClassName"
              @row-click="handleWorkflowEdgeRowClick"
            >
              <el-table-column prop="fromLabel" label="完成后" min-width="160" show-overflow-tooltip />
              <el-table-column prop="toLabel" label="下一步" min-width="160" show-overflow-tooltip />
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button link type="danger" :disabled="pageLocked || taskOperationLocked" @click="removeWorkflowFlowEdge(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-card>

      <el-card v-if="requirement.requirementType === 'MASTER' && requirement.executionMode !== 'WORKFLOW'">
        <template #header>
          <div class="card-header">
            <span>子模块列表</span>
            <span class="requirement-detail__subtitle">
              子模块才是具体 Agent 的执行单元，每个子模块都有独立步骤。
            </span>
          </div>
        </template>

        <el-table :data="pagedChildren" empty-text="暂无子模块">
          <el-table-column prop="requirementNo" label="子模块编号" width="170" />
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="mainAgentCode" label="负责 Agent" width="140" />
          <el-table-column prop="currentStage" label="当前阶段" width="140" />
          <el-table-column label="步骤" min-width="260">
            <template #default="{ row }">
              <div class="requirement-detail__steps">
                <div v-for="item in parseSteps(row.executionSteps)" :key="item">{{ item }}</div>
                <span v-if="parseSteps(row.executionSteps).length === 0">-</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="180">
            <template #default="{ row }">
              <StatusTag :value="row.status" />
              <el-tag v-if="row.executionMarker" size="small" type="danger" class="ml-6">
                执行中
              </el-tag>
              <el-tag v-if="row.mcpFileSearchEnabledFlag" size="small" type="success" class="ml-6">
                MCP检索
              </el-tag>
              <el-tag v-if="row.projectKnowledgeSearchEnabledFlag" size="small" type="success" class="ml-6">
                储备库
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="330">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="pageLocked"
                @click="$router.push({ name: 'requirement-detail', params: { requirementNo: row.requirementNo } })"
              >
                查看
              </el-button>
              <el-button link type="primary" @click="openProgressDialog(row)">
                进度
              </el-button>
              <el-button
                v-if="row.status === 'PENDING'"
                link
                type="primary"
                :disabled="pageLocked || taskOperationLocked || Boolean(row.executionMarker)"
                @click="goEditChild(row.requirementNo)"
              >
                编辑
              </el-button>
              <el-button
                v-if="isExecutableRequirement(row)"
                link
                type="success"
                :disabled="pageLocked"
                @click="openExecuteDialog(row.requirementNo)"
              >
                执行
              </el-button>
              <el-button
                v-if="row.status === 'IN_PROGRESS'"
                link
                type="warning"
                :disabled="pageLocked || Boolean(row.executionMarker)"
                @click="handleResetChildExecution(row)"
              >
                重置
              </el-button>
              <el-button
                v-if="row.status === 'PENDING'"
                link
                type="danger"
                :disabled="pageLocked || taskOperationLocked || Boolean(row.executionMarker)"
                @click="handleDeleteChild(row.requirementNo)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="children.length > 0" class="requirement-detail__pagination">
          <el-pagination
            v-model:current-page="childrenPage"
            v-model:page-size="childrenPageSize"
            layout="total, sizes, prev, pager, next"
            :page-sizes="[5, 10, 20, 50]"
            :total="children.length"
          />
        </div>
      </el-card>

      <RequirementLinkTable :links="links" :loading="loading" />
      <RequirementTestTable :records="records" :loading="loading" />
    </div>

    <el-dialog v-model="executeDialogVisible" title="下发执行任务" width="980px" destroy-on-close>
      <div class="execute-dialog" v-loading="executeLoading">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          :title="executeScopeTitle"
          description="执行对象按子模块判断：待处理或阻塞的子模块可以下发；进行中、测试中、已完成或已关闭的子模块不会重复下发。"
        />
        <div class="execute-dialog__section-title">当前在线已关联 Agent</div>
        <el-table :data="onlineLinkedAgents" size="small" empty-text="当前没有在线客户端关联 Agent">
          <el-table-column prop="agent.agentCode" label="Agent" min-width="150" />
          <el-table-column prop="agent.projectCode" label="项目" width="120" />
          <el-table-column prop="client.clientName" label="在线客户端" min-width="180" />
          <el-table-column prop="agent.workspaceDir" label="工作目录" min-width="220" show-overflow-tooltip />
        </el-table>

        <div class="execute-dialog__section-title">可下发任务</div>
        <el-table
          :data="executionTargets"
          size="small"
          :empty-text="executionEmptyText"
        >
          <el-table-column prop="task.sourceLabel" label="来源" width="120" />
          <el-table-column prop="task.requirementNo" label="需求编号" min-width="150" />
          <el-table-column prop="task.taskTitle" label="任务" min-width="200" show-overflow-tooltip />
          <el-table-column prop="agent.agentCode" label="Agent" min-width="150" />
          <el-table-column prop="client.clientName" label="在线客户端" min-width="180" />
          <el-table-column prop="agent.workspaceDir" label="工作目录" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTaskDispatch(row)">任务详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="taskDialogVisible" title="任务详情与下发" width="760px" destroy-on-close>
      <div v-if="selectedExecutionTarget" class="task-dispatch-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="需求编号">{{ selectedExecutionTarget.task.requirementNo }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ selectedExecutionTarget.task.sourceLabel }}</el-descriptions-item>
          <el-descriptions-item label="任务标题">{{ selectedExecutionTarget.task.taskTitle }}</el-descriptions-item>
          <el-descriptions-item label="Agent">{{ selectedExecutionTarget.agent.agentCode }}</el-descriptions-item>
          <el-descriptions-item label="客户端">{{ selectedExecutionTarget.client.clientName }}</el-descriptions-item>
          <el-descriptions-item label="工作目录">{{ selectedExecutionTarget.agent.workspaceDir || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-position="top">
          <el-form-item label="会话">
            <el-select v-model="selectedSessionId" class="w-full" clearable placeholder="默认已就绪会话">
              <el-option
                v-for="session in availableSessions"
                :key="session.sessionId || session.requestId"
                :label="`${session.sessionName} ${session.defaultFlag ? '(默认)' : ''} - ${session.sessionId || session.status}`"
                :value="session.sessionId || ''"
                :disabled="session.status !== 'READY' || !session.sessionId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="下发提示词">
            <el-input v-model="dispatchPrompt" type="textarea" :rows="10" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dispatching" @click="handleDispatchTask">下发</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="workflowChildDialogVisible"
      :title="workflowChildDialogTitle"
      width="760px"
      destroy-on-close
      :close-on-click-modal="!workflowChildSaving"
      :close-on-press-escape="!workflowChildSaving"
      :show-close="!workflowChildSaving"
      @closed="resetWorkflowChildAttach"
    >
      <div class="workflow-child-form">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          :title="workflowChildAlertTitle"
          :description="workflowChildAlertDescription"
        />
        <el-form :model="workflowChildForm" label-width="110px">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="子模块编号" required>
                <el-input v-model="workflowChildForm.requirementNo" :disabled="workflowChildMode === 'edit'" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="负责 Agent">
                <el-select v-model="workflowChildForm.mainAgentCode" clearable filterable placeholder="可选" class="w-full">
                  <el-option
                    v-for="agent in workflowAgents"
                    :key="agent.agentCode"
                    :label="`${agent.agentName} (${agent.agentCode})`"
                    :value="agent.agentCode"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="标题" required>
                <el-input v-model="workflowChildForm.title" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="优先级">
                <el-select v-model="workflowChildForm.priority" class="w-full">
                  <el-option label="高" value="HIGH" />
                  <el-option label="中" value="MEDIUM" />
                  <el-option label="低" value="LOW" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="当前阶段">
                <el-input v-model="workflowChildForm.currentStage" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="回执可提取">
                <el-switch v-model="workflowChildForm.resultExtractableFlag" active-text="是" inactive-text="否" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="人工审核">
                <el-switch v-model="workflowChildForm.reviewRequiredFlag" active-text="需要" inactive-text="不需要" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="MCP文件检索">
                <el-switch v-model="workflowChildForm.mcpFileSearchEnabledFlag" active-text="启用" inactive-text="关闭" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="项目储备库">
                <el-switch v-model="workflowChildForm.projectKnowledgeSearchEnabledFlag" active-text="允许" inactive-text="关闭" />
              </el-form-item>
            </el-col>
            <el-col v-if="workflowChildForm.projectKnowledgeSearchEnabledFlag" :span="12">
              <el-form-item label="读取数量">
                <el-input-number v-model="workflowChildForm.projectKnowledgeSearchLimit" :min="1" :max="20" />
              </el-form-item>
            </el-col>
            <el-col v-if="workflowChildForm.projectKnowledgeSearchEnabledFlag" :span="12">
              <el-form-item label="最低准确值">
                <el-input-number v-model="workflowChildForm.projectKnowledgeSearchMinScore" :min="0" :max="100" :step="1" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="关联文档">
                <el-tree-select
                  v-model="workflowChildForm.documentIds"
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
            <el-col :span="12">
              <el-form-item label="会话策略">
                <el-select v-model="workflowChildForm.sessionStrategy" class="w-full">
                  <el-option
                    v-for="opt in sessionStrategyOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="workflowChildForm.sessionStrategy === 'REUSE_SELECTED'" :span="12">
              <el-form-item label="指定会话ID">
                <el-input v-model="workflowChildForm.preferredSessionCode" placeholder="填写客户端会话ID" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="描述">
                <el-input v-model="workflowChildForm.requirementDesc" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="执行步骤">
                <el-input v-model="workflowChildForm.executionSteps" type="textarea" :rows="5" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
      <template #footer>
        <el-button :disabled="workflowChildSaving" @click="workflowChildDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="workflowChildSaving" @click="handleSaveWorkflowChild">
          {{ workflowChildSubmitText }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="aiDialogVisible"
      title="AI 分析并创建子模块"
      width="720px"
      destroy-on-close
      :close-on-click-modal="!aiAnalyzing"
      :close-on-press-escape="!aiAnalyzing"
      :show-close="!aiAnalyzing"
    >
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="仅当子模块列表为空时可使用"
        description="AI 会根据总需求内容和当前项目已配置 Agent 自动拆分子模块；开发链路由后续 Agent 执行时提交。"
      />
      <el-form class="ai-analyze-form" :model="aiForm" label-position="top">
        <el-form-item label="AI 模型">
          <el-select v-model="aiForm.aiSettingKey" class="w-full" placeholder="选择启用模型">
            <el-option
              v-for="item in textAiSettings"
              :key="item.settingKey"
              :label="`${item.providerName} / ${item.modelName} (${item.settingKey})`"
              :value="item.settingKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="额外拆分要求">
          <el-input
            v-model="aiForm.promptText"
            type="textarea"
            :rows="5"
            placeholder="可选，例如：优先拆接口和后端，再补测试；每个子模块控制在一天内可完成。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="aiAnalyzing" @click="aiDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="aiAnalyzing" :disabled="!textAiSettings.length" @click="handleAiAnalyze">
          开始分析
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="descriptionDialogVisible"
      title="编辑需求说明"
      width="860px"
      destroy-on-close
      :close-on-click-modal="!descriptionSaving"
      :close-on-press-escape="!descriptionSaving"
      :show-close="!descriptionSaving"
    >
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="仅在当前需求可修改时允许编辑"
        description="总需求必须没有子模块且处于待处理状态；保存后会立即刷新详情。"
      />
      <el-input
        v-model="descriptionDraft"
        class="requirement-detail__description-editor"
        type="textarea"
        :rows="16"
        resize="vertical"
        placeholder="请输入需求说明，支持多段文本、链接、步骤说明。"
      />
      <template #footer>
        <el-button :disabled="descriptionSaving" @click="descriptionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="descriptionSaving" @click="handleSaveDescription">保存说明</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progressDialogVisible" title="子模块执行进度" width="720px" destroy-on-close>
      <div v-if="selectedProgressChild" class="requirement-progress">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="子模块编号">{{ selectedProgressChild.requirementNo }}</el-descriptions-item>
          <el-descriptions-item label="负责 Agent">{{ selectedProgressChild.mainAgentCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ selectedProgressChild.title }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <StatusTag :value="selectedProgressChild.status" />
          </el-descriptions-item>
        </el-descriptions>

        <div class="requirement-progress__flow">
          <div
            v-for="stage in progressStages"
            :key="stage.value"
            class="requirement-progress__stage"
            :class="progressStageClass(stage.value)"
          >
            <svg class="requirement-progress__icon" viewBox="0 0 48 48" aria-hidden="true">
              <circle cx="24" cy="24" r="18" />
              <path v-if="stage.value === 'PENDING'" d="M24 14v11l8 5" />
              <path v-else-if="stage.value === 'IN_PROGRESS'" d="M16 25h16M25 17l8 8-8 8" />
              <path v-else-if="stage.value === 'TESTING'" d="M16 25l5 5 11-12" />
              <path v-else d="M15 25l7 7 12-16" />
            </svg>
            <div class="requirement-progress__label">{{ stage.label }}</div>
          </div>
        </div>
        <el-alert
          v-if="selectedProgressChild.status === 'BLOCKED'"
          type="error"
          show-icon
          :closable="false"
          title="当前子模块处于阻塞状态"
          description="请查看开发链路回执或智能体实际会话历史，确认阻塞原因后再处理。"
        />
      </div>
    </el-dialog>

    <el-dialog
      v-model="knowledgeDialogVisible"
      :title="confirmedKnowledgeItem ? '查看项目储备知识' : '新增项目储备知识'"
      width="900px"
      destroy-on-close
      :close-on-click-modal="!knowledgeSaving"
      :close-on-press-escape="!knowledgeSaving"
    >
      <div v-if="confirmedKnowledgeItem" class="knowledge-dialog">
        <el-alert
          type="success"
          show-icon
          :closable="false"
          title="当前需求已经保存过向量知识"
          description="已成功入库的需求不能重复新增向量；如需调整，请到项目储备库修改对应条目。"
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="知识ID">{{ confirmedKnowledgeItem.id }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ confirmedKnowledgeItem.projectCode }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ confirmedKnowledgeItem.title }}</el-descriptions-item>
          <el-descriptions-item label="向量状态">{{ confirmedKnowledgeItem.vectorStatus }}</el-descriptions-item>
          <el-descriptions-item label="集合">{{ confirmedKnowledgeItem.vectorCollection || '-' }}</el-descriptions-item>
          <el-descriptions-item label="向量ID">{{ confirmedKnowledgeItem.vectorId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="切片数">{{ confirmedKnowledgeItem.vectorChunkCount || 0 }}</el-descriptions-item>
        </el-descriptions>
        <el-input
          :model-value="confirmedKnowledgeItem.confirmedContent || confirmedKnowledgeItem.organizedContent || confirmedKnowledgeItem.detailContent"
          type="textarea"
          :rows="12"
          readonly
        />
      </div>
      <div v-else class="knowledge-dialog">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="从当前需求沉淀项目储备知识"
          description="系统会读取当前需求的可提取执行回执作为原始材料，先由 AI 整理；人工只微调整理结果，确认入库时才会生成向量写入 Milvus Lite。"
        />
        <el-card class="knowledge-receipts-card" shadow="never" v-loading="knowledgeReceiptLoading">
          <template #header>
            <div class="knowledge-receipts-card__header">
              <span>执行回执列表（AI整理原始材料）</span>
              <el-tag size="small" type="info">{{ knowledgeReceiptItems.length }} 条</el-tag>
            </div>
          </template>
          <el-empty v-if="!knowledgeReceiptItems.length" description="当前没有可提取执行回执，无法从需求沉淀项目储备知识" />
          <el-table
            v-else
            ref="knowledgeReceiptTableRef"
            :data="knowledgeReceiptItems"
            row-key="requirementNo"
            size="small"
            max-height="260"
            @selection-change="handleKnowledgeReceiptSelectionChange"
          >
            <el-table-column type="selection" width="46" :selectable="isKnowledgeReceiptSelectable" />
            <el-table-column prop="requirementNo" label="子模块" width="150" show-overflow-tooltip />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="agentCode" label="Agent" width="150" show-overflow-tooltip />
            <el-table-column label="摘要" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.resultSummary || row.executionDetails || '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-form :model="knowledgeForm" label-position="top">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="类型">
                <el-select v-model="knowledgeForm.knowledgeType" :disabled="knowledgeOperationLocked" class="w-full">
                  <el-option v-for="opt in projectKnowledgeTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="标题">
                <el-input v-model="knowledgeForm.title" :disabled="knowledgeOperationLocked" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="AI整理配置">
                <el-select v-model="knowledgeForm.aiSettingKey" :disabled="knowledgeOperationLocked" clearable filterable placeholder="选择文本模型" class="w-full">
                  <el-option v-for="item in textAiSettings" :key="item.settingKey" :label="`${item.providerName} / ${item.modelName} (${item.settingKey})`" :value="item.settingKey" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="向量配置">
                <el-select v-model="knowledgeForm.embeddingSettingKey" :disabled="knowledgeOperationLocked" clearable filterable placeholder="选择 embedding 模型" class="w-full">
                  <el-option v-for="item in embeddingAiSettings" :key="item.settingKey" :label="`${item.providerName} / ${item.modelName} (${item.settingKey})`" :value="item.settingKey" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="文档标准">
                <el-tree-select
                  v-model="knowledgeForm.documentIds"
                  :data="documentTreeSelectOptions"
                  :props="documentTreeSelectProps"
                  check-on-click-node
                  check-strictly
                  class="w-full"
                  collapse-tags
                  collapse-tags-tooltip
                  clearable
                  default-expand-all
                  :disabled="knowledgeOperationLocked"
                  filterable
                  multiple
                  node-key="value"
                  placeholder="AI整理时参考，可多选"
                  show-checkbox
                />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="简单描述">
                <el-input v-model="knowledgeForm.simpleDesc" :disabled="knowledgeOperationLocked" type="textarea" :rows="2" />
              </el-form-item>
            </el-col>
            <el-col v-if="draftKnowledgeItem" :span="24">
              <el-form-item label="AI整理结果（人工微调后确认入库）">
                <el-input v-model="knowledgeForm.confirmedContent" :disabled="knowledgeOperationLocked" type="textarea" :rows="12" resize="vertical" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <el-alert
          v-if="draftKnowledgeItem"
          :type="draftKnowledgeItem.vectorStatus === 'FAILED' ? 'error' : 'warning'"
          show-icon
          :closable="false"
          :title="`当前草稿：${draftKnowledgeItem.status} / ${draftKnowledgeItem.vectorStatus}`"
          :description="draftKnowledgeItem.errorMessage || '请先 AI 整理，再人工确认内容并入库。'"
        />
      </div>
      <template #footer>
        <el-button :disabled="knowledgeSaving" @click="knowledgeDialogVisible = false">关闭</el-button>
        <el-button v-if="confirmedKnowledgeItem" type="primary" @click="$router.push({ name: 'project-knowledge', query: { projectCode: confirmedKnowledgeItem.projectCode } })">
          打开储备库
        </el-button>
        <template v-else>
          <el-button v-if="draftKnowledgeItem" :disabled="knowledgeOperationLocked" :loading="knowledgeSaving" @click="handleSaveRequirementKnowledgeDraft">
            保存微调
          </el-button>
          <el-button
            :disabled="!canPrepareKnowledgeFromReceipts"
            :loading="knowledgeSaving || knowledgeOrganizing || knowledgeOperationLocked"
            type="warning"
            @click="handlePrepareRequirementKnowledge"
          >
            {{ knowledgeOperationLocked ? 'AI整理中' : draftKnowledgeItem ? '重新AI整理' : '读取回执并AI整理' }}
          </el-button>
          <el-button :disabled="knowledgeOperationLocked || !draftKnowledgeItem?.vectorDirtyFlag || !knowledgeForm.confirmedContent" :loading="knowledgeConfirming" type="success" @click="handleConfirmRequirementKnowledge">确认入库</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { BaseEdge, EdgeLabelRenderer, Handle, MarkerType, Position, VueFlow, getSmoothStepPath } from '@vue-flow/core'
import type { EdgeMouseEvent } from '@vue-flow/core'
import PageHeader from '@/components/common/PageHeader.vue'
import RequirementBaseInfoCard from '@/components/requirement/RequirementBaseInfoCard.vue'
import RequirementLinkTable from '@/components/requirement/RequirementLinkTable.vue'
import RequirementTestTable from '@/components/requirement/RequirementTestTable.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { fetchAgents } from '@/api/agent'
import { dispatchClientCommand, fetchClientNodes, fetchClientSessions } from '@/api/client'
import { fetchRequirementLinks, fetchRequirementLinksWithChildren } from '@/api/link'
import { fetchMarkdownDocumentTree } from '@/api/markdownDocument'
import {
  analyzeRequirement,
  approveRequirementReview,
  createChildRequirement,
  deleteRequirement,
  dispatchReadyWorkflowTasks,
  fetchRequirement,
  fetchRequirementChildren,
  fetchRequirementWorkflow,
  fetchRequirementWorkflowResults,
  resetRequirementExecution,
  saveRequirementWorkflow,
  updateRequirement,
  updateRequirementDescription,
} from '@/api/requirement'
import {
  confirmProjectKnowledge,
  createProjectKnowledgeFromRequirement,
  fetchProjectKnowledgeByRequirement,
  organizeProjectKnowledge,
  updateProjectKnowledge,
} from '@/api/projectKnowledge'
import { fetchAiSettings } from '@/api/system'
import { fetchRequirementTests } from '@/api/testRecord'
import type { Agent } from '@/types/agent'
import type { ClientAgentSession, ClientControllableAgent, ClientNode } from '@/types/client'
import type { RequirementLink } from '@/types/link'
import type { MarkdownDocumentTreeNode } from '@/types/markdownDocument'
import type {
  CreateRequirementPayload,
  Requirement,
  RequirementWorkflow,
  RequirementWorkflowResultItem,
  SessionStrategyValue,
  UpdateRequirementPayload,
} from '@/types/requirement'
import type { ProjectKnowledge, ProjectKnowledgeTypeValue } from '@/types/projectKnowledge'
import type { AiModelSetting } from '@/types/system'
import type { TestRecord } from '@/types/testRecord'
import { useAppStore } from '@/stores/app'
import { addConsolePushListener, type ConsolePushEvent } from '@/composables/useConsolePush'
import { projectKnowledgeTypeOptions, sessionStrategyOptions } from '@/types/options'
import { buildMarkdownDocumentTreeSelectOptions } from '@/utils/markdownDocumentTree'

const props = defineProps<{ requirementNo: string }>()

interface ExecutionTarget {
  task: ExecutionTask
  link?: RequirementLink
  requirement?: Requirement
  client: ClientNode
  agent: ClientControllableAgent
}

interface OnlineLinkedAgent {
  client: ClientNode
  agent: ClientControllableAgent
}

interface ExecutionTask {
  source: 'LINK' | 'REQUIREMENT'
  sourceLabel: string
  requirementNo: string
  linkId?: number
  linkType?: RequirementLink['linkType']
  taskTitle: string
  taskDesc?: string
  agentCode: string
  status: RequirementLink['status'] | Requirement['status']
}

interface WorkflowNodeView {
  node: Requirement
  previous: Requirement[]
  next: Requirement[]
  depth: number
  ready: boolean
  start: boolean
}

interface WorkflowColumn {
  depth: number
  nodes: WorkflowNodeView[]
}

interface WorkflowFlowNode {
  id: string
  type: string
  position: { x: number; y: number }
  data: { view: WorkflowNodeView }
  draggable: boolean
  connectable: boolean
}

interface WorkflowFlowEdge {
  id: string
  source: string
  target: string
  type: string
  markerEnd: MarkerType
  animated: boolean
  selected?: boolean
  style: Record<string, string | number>
}

interface WorkflowEdgeRow {
  id: string
  fromLabel: string
  toLabel: string
  selected: boolean
}

interface RequirementKnowledgeForm {
  knowledgeType: ProjectKnowledgeTypeValue
  title: string
  simpleDesc: string
  aiSettingKey: string
  embeddingSettingKey: string
  documentIds: string[]
  confirmedContent: string
}

interface WorkflowConnection {
  source?: string | null
  target?: string | null
}

interface WorkflowConnectStartEvent {
  nodeId?: string | null
  handleType?: string | null
}

const router = useRouter()
const appStore = useAppStore()
const requirement = ref<Requirement | null>(null)
const children = ref<Requirement[]>([])
const links = ref<RequirementLink[]>([])
const records = ref<TestRecord[]>([])
const onlineClients = ref<ClientNode[]>([])
const availableSessions = ref<ClientAgentSession[]>([])
const selectedExecutionTarget = ref<ExecutionTarget | null>(null)
const loading = ref(false)
const executeLoading = ref(false)
const executeDialogVisible = ref(false)
const taskDialogVisible = ref(false)
const dispatching = ref(false)
const executionScopeRequirementNo = ref('')
const aiDialogVisible = ref(false)
const aiAnalyzing = ref(false)
const descriptionDialogVisible = ref(false)
const descriptionSaving = ref(false)
const descriptionDraft = ref('')
const progressDialogVisible = ref(false)
const selectedProgressChild = ref<Requirement | null>(null)
const knowledgeDialogVisible = ref(false)
const knowledgeSaving = ref(false)
const knowledgeOrganizing = ref(false)
const knowledgeConfirming = ref(false)
const requirementKnowledgeItems = ref<ProjectKnowledge[]>([])
const knowledgeReceiptLoading = ref(false)
const knowledgeReceiptItems = ref<RequirementWorkflowResultItem[]>([])
const selectedKnowledgeReceiptItems = ref<RequirementWorkflowResultItem[]>([])
const knowledgeReceiptTableRef = ref<{
  clearSelection: () => void
  toggleRowSelection: (row: RequirementWorkflowResultItem, selected?: boolean) => void
}>()
const knowledgeForm = ref<RequirementKnowledgeForm>(emptyRequirementKnowledgeForm())
const workflow = ref<RequirementWorkflow | null>(null)
const workflowDispatching = ref(false)
const workflowSaving = ref(false)
const workflowDirty = ref(false)
const selectedWorkflowEdgeId = ref('')
const flowNodes = ref<WorkflowFlowNode[]>([])
const flowEdges = ref<WorkflowFlowEdge[]>([])
const workflowChildDialogVisible = ref(false)
const workflowChildSaving = ref(false)
const workflowChildMode = ref<'create' | 'edit'>('create')
const workflowEditingChild = ref<Requirement | null>(null)
const workflowAttachSourceNo = ref('')
const workflowConnectStartNodeNo = ref('')
const workflowConnectCompleted = ref(false)
const workflowAgents = ref<Agent[]>([])
const documentTreeNodes = ref<MarkdownDocumentTreeNode[]>([])
const documentTreeSelectProps = {
  children: 'children',
  disabled: 'disabled',
  label: 'label',
  value: 'value',
}
const workflowChildForm = ref({
  requirementNo: '',
  title: '',
  requirementDesc: '',
  priority: 'MEDIUM',
  mainAgentCode: '',
  currentStage: '待开发',
  executionSteps: '',
  resultExtractableFlag: true,
  reviewRequiredFlag: false,
  mcpFileSearchEnabledFlag: false,
  documentIds: [] as string[],
  projectKnowledgeSearchEnabledFlag: false,
  projectKnowledgeSearchLimit: 5,
  projectKnowledgeSearchMinScore: 70,
  sessionStrategy: 'NEW' as SessionStrategyValue,
  preferredSessionCode: '',
})
const aiSettings = ref<AiModelSetting[]>([])
const childrenPage = ref(1)
const childrenPageSize = ref(10)
const selectedSessionId = ref('')
const dispatchPrompt = ref('')
const aiForm = ref({ aiSettingKey: '', promptText: '' })
let analysisPollTimer: number | undefined
let pushRefreshTimer: number | undefined
let knowledgePollTimer: number | undefined
let removeConsolePushListener: (() => void) | undefined
let loadVersion = 0
const progressStages: Array<{ value: Requirement['status']; label: string }> = [
  { value: 'PENDING', label: '等待执行' },
  { value: 'IN_PROGRESS', label: '执行中' },
  { value: 'TESTING', label: '测试中' },
  { value: 'DONE', label: '完成' },
]

const pagedChildren = computed(() => {
  const start = (childrenPage.value - 1) * childrenPageSize.value
  return children.value.slice(start, start + childrenPageSize.value)
})
const documentTreeSelectOptions = computed(() => buildMarkdownDocumentTreeSelectOptions(documentTreeNodes.value))
const enabledAiSettings = computed(() => aiSettings.value.filter((item) => item.enabledFlag))
const textAiSettings = computed(() => enabledAiSettings.value.filter((item) => modelPurpose(item) === 'LANGUAGE'))
const embeddingAiSettings = computed(() => enabledAiSettings.value.filter((item) => modelPurpose(item) === 'VECTOR'))
const confirmedKnowledgeItem = computed(() => requirementKnowledgeItems.value.find((item) =>
  item.status === 'CONFIRMED' && item.vectorStatus === 'READY',
) || null)
const draftKnowledgeItem = computed(() => requirementKnowledgeItems.value.find((item) =>
  item.status !== 'CONFIRMED' || item.vectorStatus !== 'READY',
) || null)
const knowledgeOperationLocked = computed(() => draftKnowledgeItem.value?.status === 'AI_ORGANIZING')
const canPrepareKnowledgeFromReceipts = computed(() => !knowledgeOperationLocked.value && selectedKnowledgeReceiptItems.value.length > 0)
const pageLocked = computed(() => aiAnalyzing.value || requirement.value?.status === 'ANALYZING')
const taskOperationLocked = computed(() => {
  const items = requirement.value ? [requirement.value, ...children.value, ...workflowNodes.value] : []
  return items.some((item) => Boolean(item.executionMarker) || item.status === 'IN_PROGRESS')
})
const pageLoadingText = computed(() => {
  if (aiAnalyzing.value || requirement.value?.status === 'ANALYZING') {
    return 'AI 分析中，请勿操作'
  }
  return '加载中'
})
const canAiAnalyze = computed(() => requirement.value?.requirementType === 'MASTER'
  && children.value.length === 0
  && requirement.value?.status !== 'ANALYZING')
const canShowHeaderCreateChild = computed(() => requirement.value?.requirementType === 'MASTER'
  && requirement.value.executionMode !== 'WORKFLOW')
const canEditDescription = computed(() => {
  if (!requirement.value || pageLocked.value || taskOperationLocked.value || requirement.value.status !== 'PENDING') {
    return false
  }
  if (requirement.value.requirementType === 'MASTER') {
    return children.value.length === 0
  }
  return requirement.value.requirementType === 'SUB'
})
const executionRequirementCandidates = computed<Requirement[]>(() => {
  if (!requirement.value) {
    return []
  }
  if (executionScopeRequirementNo.value) {
    const scoped = findRequirementForExecutionScope(executionScopeRequirementNo.value)
    return scoped ? [scoped] : []
  }
  if (requirement.value.requirementType === 'MASTER') {
    return children.value.length ? children.value : [requirement.value]
  }
  return [requirement.value]
})
const scopedExecutableRequirements = computed(() => {
  const executable = executionRequirementCandidates.value.filter(isExecutableRequirement)
  if (requirement.value?.executionMode === 'WORKFLOW') {
    const readyNos = new Set(workflow.value?.readyRequirementNos || [])
    return executable.filter((item) => readyNos.has(item.requirementNo))
  }
  return executable
})
const canOpenExecute = computed(() => {
  if (requirement.value?.requirementType === 'MASTER' && requirement.value.executionMode === 'WORKFLOW') {
    return false
  }
  return scopedExecutableRequirements.value.length > 0
})
const executeButtonLabel = computed(() => requirement.value?.requirementType === 'MASTER' ? '执行全部' : '执行当前')
const executeScopeTitle = computed(() => {
  if (executionScopeRequirementNo.value) {
    return `当前只下发子模块：${executionScopeRequirementNo.value}`
  }
  if (requirement.value?.requirementType === 'MASTER') {
    return '当前下发范围：全部可执行子模块'
  }
  return '当前下发范围：当前子模块'
})
const onlineLinkedAgents = computed<OnlineLinkedAgent[]>(() => {
  const result: OnlineLinkedAgent[] = []
  onlineClients.value
    .filter((client) => client.status === 'ONLINE')
    .forEach((client) => {
      ;(client.agents || []).forEach((agent) => {
        result.push({ client, agent })
      })
    })
  return result
})
const executionTargets = computed<ExecutionTarget[]>(() => {
  const targets: ExecutionTarget[] = []
  scopedExecutableRequirements.value.forEach((item) => {
    onlineClients.value.filter((client) => client.status === 'ONLINE').forEach((client) => {
      const agent = (client.agents || []).find((agentItem) => agentItem.agentCode === item.mainAgentCode)
      if (!agent) {
        return
      }
      const link = findReusableLink(item)
      targets.push({
        task: buildRequirementExecutionTask(item, link),
        requirement: item,
        link,
        client,
        agent,
      })
    })
  })
  return targets
})
const executionEmptyText = computed(() => {
  if (!scopedExecutableRequirements.value.length) {
    return '当前范围没有可下发子模块。只有待处理或阻塞状态的子模块可以下发。'
  }
  if (scopedExecutableRequirements.value.some((item) => !item.mainAgentCode)) {
    return '当前可执行子模块未配置负责 Agent。'
  }
  return '当前可执行子模块没有匹配的在线关联 Agent。请确认客户端已在线且关联了子模块负责 Agent。'
})
const workflowNodes = computed(() => workflow.value?.nodes?.length ? workflow.value.nodes : children.value)
const workflowEdges = computed(() => workflow.value?.edges || [])
const workflowReadyNoSet = computed(() => new Set(workflow.value?.readyRequirementNos || []))
const workflowStartNoSet = computed(() => new Set(workflow.value?.startRequirementNos || []))
const workflowNodeMap = computed(() => {
  const result = new Map<string, Requirement>()
  workflowNodes.value.forEach((item) => result.set(item.requirementNo, item))
  return result
})
const workflowPreviousMap = computed(() => {
  const result = new Map<string, string[]>()
  workflowEdges.value.forEach((edge) => {
    result.set(edge.toRequirementNo, [...(result.get(edge.toRequirementNo) || []), edge.fromRequirementNo])
  })
  return result
})
const workflowNextMap = computed(() => {
  const result = new Map<string, string[]>()
  workflowEdges.value.forEach((edge) => {
    result.set(edge.fromRequirementNo, [...(result.get(edge.fromRequirementNo) || []), edge.toRequirementNo])
  })
  return result
})
const workflowNodeDepthMap = computed(() => {
  const result = new Map<string, number>()
  workflowNodes.value.forEach((item) => result.set(item.requirementNo, 0))
  for (let index = 0; index < workflowNodes.value.length; index++) {
    let changed = false
    workflowEdges.value.forEach((edge) => {
      const fromDepth = result.get(edge.fromRequirementNo) ?? 0
      const nextDepth = fromDepth + 1
      if ((result.get(edge.toRequirementNo) ?? 0) < nextDepth) {
        result.set(edge.toRequirementNo, nextDepth)
        changed = true
      }
    })
    if (!changed) {
      break
    }
  }
  return result
})
const workflowNodeViews = computed<WorkflowNodeView[]>(() => workflowNodes.value
  .map((node) => {
    const previous = (workflowPreviousMap.value.get(node.requirementNo) || [])
      .map((requirementNo) => workflowNodeMap.value.get(requirementNo))
      .filter(Boolean) as Requirement[]
    const next = (workflowNextMap.value.get(node.requirementNo) || [])
      .map((requirementNo) => workflowNodeMap.value.get(requirementNo))
      .filter(Boolean) as Requirement[]
    return {
      node,
      previous,
      next,
      depth: workflowNodeDepthMap.value.get(node.requirementNo) || 0,
      ready: workflowReadyNoSet.value.has(node.requirementNo),
      start: workflowStartNoSet.value.has(node.requirementNo),
    }
  })
  .sort((left, right) => left.depth - right.depth
    || (left.node.sortNo || 0) - (right.node.sortNo || 0)
    || left.node.requirementNo.localeCompare(right.node.requirementNo)))
const workflowColumns = computed<WorkflowColumn[]>(() => {
  const grouped = new Map<number, WorkflowNodeView[]>()
  workflowNodeViews.value.forEach((view) => {
    grouped.set(view.depth, [...(grouped.get(view.depth) || []), view])
  })
  return Array.from(grouped.entries())
    .sort(([left], [right]) => left - right)
    .map(([depth, nodes]) => ({ depth, nodes }))
})
const workflowSummary = computed(() => {
  const readyCount = workflowReadyNoSet.value.size
  const doneCount = workflowNodes.value.filter(isFinishedRequirement).length
  return `节点 ${workflowNodes.value.length} 个，关系 ${flowEdges.value.length} 条，已完成 ${doneCount} 个，当前就绪 ${readyCount} 个`
})
const workflowRunning = computed(() => workflowNodes.value.some((item) =>
  item.status === 'IN_PROGRESS' || Boolean(item.executionMarker),
))
const flowEdgeRows = computed<WorkflowEdgeRow[]>(() => flowEdges.value.map((edge) => ({
  id: edge.id,
  fromLabel: workflowNodeOptionLabelByNo(edge.source),
  toLabel: workflowNodeOptionLabelByNo(edge.target),
  selected: edge.id === selectedWorkflowEdgeId.value,
})))
const workflowChildDialogTitle = computed(() => workflowChildMode.value === 'edit' ? '编辑子模块' : '新建子模块')
const workflowChildSubmitText = computed(() => workflowChildMode.value === 'edit' ? '保存子模块' : '创建子模块')
const workflowChildAlertTitle = computed(() => workflowChildMode.value === 'edit'
  ? '直接在当前页面修改子模块'
  : '子模块会继承当前总需求的工作流模式')
const workflowChildAlertDescription = computed(() => workflowChildMode.value === 'edit'
  ? '保存后会立即刷新画布，不会再打开单独的编辑页签。'
  : workflowAttachSourceNo.value
    ? `创建后会自动作为“${workflowNodeOptionLabelByNo(workflowAttachSourceNo.value)}”的下一步，并保存流程关系。`
    : '创建后会直接出现在画布中，再通过拖线配置它的前置和下一步关系。')

watch(
  () => props.requirementNo,
  async () => {
    childrenPage.value = 1
    await loadData()
  },
  { immediate: true },
)

watch(
  () => children.value.length,
  () => {
    const maxPage = Math.max(1, Math.ceil(children.value.length / childrenPageSize.value))
    if (childrenPage.value > maxPage) {
      childrenPage.value = maxPage
    }
  },
)

watch(
  () => requirement.value?.status,
  (status) => {
    if (status === 'ANALYZING') {
      startAnalysisPolling()
      return
    }
    stopAnalysisPolling()
  },
  { immediate: true },
)

watch(
  () => draftKnowledgeItem.value?.status,
  (status) => {
    if (status === 'AI_ORGANIZING') {
      startKnowledgePolling()
      return
    }
    stopKnowledgePolling()
  },
  { immediate: true },
)

onMounted(() => {
  removeConsolePushListener = addConsolePushListener(handleConsolePushEvent)
})

onBeforeUnmount(() => {
  stopAnalysisPolling()
  stopKnowledgePolling()
  stopPushRefresh()
  removeConsolePushListener?.()
})

async function loadData() {
  const currentLoad = ++loadVersion
  loading.value = true
  try {
    const [requirementData, testData, knowledgeData] = await Promise.all([
      fetchRequirement(props.requirementNo),
      fetchRequirementTests(props.requirementNo),
      fetchProjectKnowledgeByRequirement(props.requirementNo).catch(() => []),
    ])
    if (currentLoad !== loadVersion) {
      return
    }
    requirement.value = requirementData
    records.value = testData
    requirementKnowledgeItems.value = knowledgeData
    const nextChildren =
      requirementData.requirementType === 'MASTER'
        ? await fetchRequirementChildren(props.requirementNo)
        : []
    const workflowRootNo = requirementData.requirementType === 'MASTER'
      ? requirementData.requirementNo
      : requirementData.rootRequirementNo
    const nextWorkflow =
      requirementData.executionMode === 'WORKFLOW' && workflowRootNo
        ? await fetchRequirementWorkflow(workflowRootNo)
        : null
    const nextLinks =
      requirementData.requirementType === 'MASTER'
        ? await fetchRequirementLinksWithChildren(props.requirementNo)
        : await fetchRequirementLinks(props.requirementNo)
    if (currentLoad !== loadVersion) {
      return
    }
    children.value = nextChildren
    workflow.value = nextWorkflow
    links.value = nextLinks
    syncWorkflowFlow(false)
    if (requirementData.status === 'ANALYZING') {
      startAnalysisPolling()
    }
    if (knowledgeData.some((item) => item.status === 'AI_ORGANIZING')) {
      startKnowledgePolling()
    }
  } finally {
    if (currentLoad === loadVersion) {
      loading.value = false
    }
  }
}

function startKnowledgePolling() {
  if (knowledgePollTimer) {
    return
  }
  knowledgePollTimer = window.setInterval(() => {
    if (!draftKnowledgeItem.value || draftKnowledgeItem.value.status !== 'AI_ORGANIZING') {
      stopKnowledgePolling()
      return
    }
    void refreshRequirementKnowledgeItems()
  }, 3000)
}

function stopKnowledgePolling() {
  if (!knowledgePollTimer) {
    return
  }
  window.clearInterval(knowledgePollTimer)
  knowledgePollTimer = undefined
}

async function refreshRequirementKnowledgeItems() {
  const items = await fetchProjectKnowledgeByRequirement(props.requirementNo).catch(() => requirementKnowledgeItems.value)
  requirementKnowledgeItems.value = items
  const draft = items.find((item) => item.status !== 'CONFIRMED' || item.vectorStatus !== 'READY')
  if (draft && draft.status !== 'AI_ORGANIZING') {
    knowledgeForm.value.confirmedContent = draft.confirmedContent || draft.organizedContent || knowledgeForm.value.confirmedContent
    stopKnowledgePolling()
  }
}

function startAnalysisPolling() {
  if (analysisPollTimer) {
    return
  }
  analysisPollTimer = window.setInterval(() => {
    if (loading.value || requirement.value?.status !== 'ANALYZING') {
      stopAnalysisPolling()
      return
    }
    void loadData()
  }, 5000)
}

function stopAnalysisPolling() {
  if (!analysisPollTimer) {
    return
  }
  window.clearInterval(analysisPollTimer)
  analysisPollTimer = undefined
}

function handleConsolePushEvent(event: ConsolePushEvent) {
  if (event.type === 'REQUIREMENT_TASK_CHANGED') {
    const data = event.data as { requirementNo?: string; rootRequirementNo?: string } | undefined
    if (!isCurrentRequirementPush(data)) {
      return
    }
    schedulePushRefresh()
    return
  }
  if (event.type === 'CLIENT_STATUS_CHANGED' && executeDialogVisible.value) {
    void fetchClientNodes().then((items) => {
      onlineClients.value = items
    })
  }
}

function isCurrentRequirementPush(data?: { requirementNo?: string; rootRequirementNo?: string }) {
  if (!requirement.value || !data) {
    return false
  }
  const currentNo = requirement.value.requirementNo
  const currentRoot = requirement.value.requirementType === 'MASTER'
    ? requirement.value.requirementNo
    : requirement.value.rootRequirementNo
  return data.requirementNo === currentNo
    || data.rootRequirementNo === currentNo
    || (currentRoot && data.rootRequirementNo === currentRoot)
}

function schedulePushRefresh() {
  if (pushRefreshTimer) {
    return
  }
  pushRefreshTimer = window.setTimeout(async () => {
    pushRefreshTimer = undefined
    if (!loading.value) {
      await loadData()
    }
  }, 300)
}

function stopPushRefresh() {
  if (!pushRefreshTimer) {
    return
  }
  window.clearTimeout(pushRefreshTimer)
  pushRefreshTimer = undefined
}

function goCreateChild() {
  if (!requirement.value || pageLocked.value || taskOperationLocked.value) {
    if (taskOperationLocked.value) {
      ElMessage.warning('当前存在执行中的子模块，不能新增子模块')
    }
    return
  }
  router.push({
    name: 'requirement-child-create',
    params: { parentRequirementNo: requirement.value.requirementNo },
  })
}

function goRequirementList() {
  appStore.closeActiveTabAndOpen({ name: 'requirements' }, router)
}

function goParentRequirement() {
  if (requirement.value?.parentRequirementNo) {
    appStore.closeActiveTabAndOpen({
      name: 'requirement-detail',
      params: { requirementNo: requirement.value.parentRequirementNo },
    }, router)
    return
  }
  goRequirementList()
}

function goEditChild(requirementNo: string) {
  if (pageLocked.value || taskOperationLocked.value) {
    if (taskOperationLocked.value) {
      ElMessage.warning('当前存在执行中的子模块，不能编辑子模块')
    }
    return
  }
  const child = children.value.find((item) => item.requirementNo === requirementNo)
  if (child) {
    void openWorkflowChildDialog(child)
    return
  }
  router.push({ name: 'requirement-edit', params: { requirementNo } })
}

async function handleDeleteChild(requirementNo: string) {
  if (pageLocked.value || taskOperationLocked.value) {
    if (taskOperationLocked.value) {
      ElMessage.warning('当前存在执行中的子模块，不能删除子模块')
    }
    return
  }
  await ElMessageBox.confirm('删除后将移除该未执行子任务及其未执行待办数据，是否继续？', '删除子任务', {
    type: 'warning',
  })
  await deleteRequirement(requirementNo)
  await loadData()
}

function openProgressDialog(row: Requirement) {
  selectedProgressChild.value = row
  progressDialogVisible.value = true
}

function progressStageClass(stage: Requirement['status']) {
  const current = selectedProgressChild.value?.status || 'PENDING'
  const currentIndex = progressStageIndex(current)
  const stageIndex = progressStageIndex(stage)
  return {
    'is-done': current !== 'BLOCKED' && stageIndex < currentIndex,
    'is-current': current === stage,
    'is-blocked': current === 'BLOCKED' && stage === 'IN_PROGRESS',
  }
}

function progressStageIndex(status: Requirement['status']) {
  if (status === 'BLOCKED') {
    return 1
  }
  const index = progressStages.findIndex((item) => item.value === status)
  return index < 0 ? 0 : index
}

async function handleResetChildExecution(row: Requirement) {
  if (row.executionMarker) {
    ElMessage.warning('该子模块存在执行标识，说明智能体仍在执行，不能重置')
    return
  }
  await ElMessageBox.confirm('重置后该子模块会回到等待执行，正在处理中的开发链路也会退回待处理。是否继续？', '重置子模块执行状态', {
    type: 'warning',
  })
  await resetRequirementExecution(row.requirementNo)
  ElMessage.success('子模块已重置为等待执行')
  await loadData()
}

async function openAiAnalyzeDialog() {
  if (!canAiAnalyze.value) {
    ElMessage.warning('只有总需求且子模块为空时才能使用 AI 分析')
    return
  }
  aiDialogVisible.value = true
  aiSettings.value = await fetchAiSettings({ modelPurpose: 'LANGUAGE', enabledFlag: true }).catch(() => [])
  if (textAiSettings.value.length && !aiForm.value.aiSettingKey) {
    aiForm.value.aiSettingKey = textAiSettings.value[0].settingKey
  }
}

async function handleAiAnalyze() {
  if (!requirement.value || !canAiAnalyze.value) {
    ElMessage.warning('当前需求不满足 AI 分析条件')
    return
  }
  if (!aiForm.value.aiSettingKey) {
    ElMessage.warning('请选择 AI 模型配置')
    return
  }
  aiAnalyzing.value = true
  try {
    const updatedRequirement = await analyzeRequirement(requirement.value.requirementNo, {
      aiSettingKey: aiForm.value.aiSettingKey,
      promptText: aiForm.value.promptText || undefined,
    })
    requirement.value = {
      ...requirement.value,
      ...updatedRequirement,
    }
    ElMessage.success(updatedRequirement.status === 'ANALYZING' ? 'AI 分析任务已提交，完成后会自动刷新' : 'AI 分析完成')
    aiDialogVisible.value = false
    await loadData()
  } finally {
    aiAnalyzing.value = false
  }
}

function openDescriptionEditor() {
  if (!requirement.value || !canEditDescription.value) {
    ElMessage.warning('当前需求状态不允许修改说明')
    return
  }
  descriptionDraft.value = requirement.value.requirementDesc || ''
  descriptionDialogVisible.value = true
}

async function handleSaveDescription() {
  if (!requirement.value || !canEditDescription.value) {
    ElMessage.warning('当前需求状态不允许修改说明')
    return
  }
  descriptionSaving.value = true
  try {
    const updated = await updateRequirementDescription(requirement.value.requirementNo, {
      requirementDesc: descriptionDraft.value,
    })
    requirement.value = {
      ...requirement.value,
      ...updated,
    }
    descriptionDialogVisible.value = false
    ElMessage.success('需求说明已更新')
    await loadData()
  } finally {
    descriptionSaving.value = false
  }
}

async function openKnowledgeDialog() {
  if (!requirement.value) {
    return
  }
  knowledgeDialogVisible.value = true
  await loadRequirementKnowledgeReceipts()
  const [settings, documents] = await Promise.all([
    fetchAiSettings({ enabledFlag: true }).catch(() => []),
    fetchMarkdownDocumentTree().catch(() => []),
  ])
  aiSettings.value = settings
  if (documents.length) {
    documentTreeNodes.value = documents
  }
  const draft = draftKnowledgeItem.value
  knowledgeForm.value = draft
    ? {
      knowledgeType: draft.knowledgeType,
      title: draft.title,
      simpleDesc: draft.simpleDesc || '',
      aiSettingKey: draft.aiSettingKey || defaultTextAiSettingKey(),
      embeddingSettingKey: draft.embeddingSettingKey || defaultEmbeddingSettingKey(),
      documentIds: csvToList(draft.documentIds),
      confirmedContent: draft.confirmedContent || draft.organizedContent || '',
    }
    : {
      ...emptyRequirementKnowledgeForm(),
      title: requirement.value.title,
      simpleDesc: `来源需求：${requirement.value.requirementNo}`,
      aiSettingKey: defaultTextAiSettingKey(),
      embeddingSettingKey: defaultEmbeddingSettingKey(),
    }
}

async function loadRequirementKnowledgeReceipts() {
  if (!requirement.value) {
    knowledgeReceiptItems.value = []
    return
  }
  knowledgeReceiptLoading.value = true
  try {
    const result = await fetchRequirementWorkflowResults(requirement.value.requirementNo)
    knowledgeReceiptItems.value = result.items || []
    selectedKnowledgeReceiptItems.value = [...knowledgeReceiptItems.value]
    await nextTick()
    knowledgeReceiptTableRef.value?.clearSelection()
    knowledgeReceiptItems.value.forEach((item) => knowledgeReceiptTableRef.value?.toggleRowSelection(item, true))
  } catch {
    knowledgeReceiptItems.value = []
    selectedKnowledgeReceiptItems.value = []
  } finally {
    knowledgeReceiptLoading.value = false
  }
}

function handleKnowledgeReceiptSelectionChange(rows: RequirementWorkflowResultItem[]) {
  selectedKnowledgeReceiptItems.value = rows
}

function isKnowledgeReceiptSelectable() {
  return !knowledgeOperationLocked.value
}

function upsertRequirementKnowledgeItem(item: ProjectKnowledge) {
  const index = requirementKnowledgeItems.value.findIndex((row) => row.id === item.id)
  if (index >= 0) {
    requirementKnowledgeItems.value.splice(index, 1, item)
    return
  }
  requirementKnowledgeItems.value.unshift(item)
}

async function handleSaveRequirementKnowledgeDraft(showMessage = true) {
  if (knowledgeOperationLocked.value) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return null
  }
  if (!requirement.value) {
    return null
  }
  if (!knowledgeForm.value.title) {
    ElMessage.warning('标题不能为空')
    return null
  }
  if (!selectedKnowledgeReceiptItems.value.length) {
    ElMessage.warning('请至少勾选一条执行回执')
    return null
  }
  knowledgeSaving.value = true
  try {
    const draft = draftKnowledgeItem.value
    let saved: ProjectKnowledge
    if (draft) {
      saved = await updateProjectKnowledge(draft.id, {
        knowledgeType: knowledgeForm.value.knowledgeType,
        title: knowledgeForm.value.title,
        simpleDesc: knowledgeForm.value.simpleDesc,
        detailContent: buildSelectedReceiptKnowledgeSeed(),
        organizedContent: knowledgeForm.value.confirmedContent,
        confirmedContent: knowledgeForm.value.confirmedContent,
        aiSettingKey: knowledgeForm.value.aiSettingKey,
        embeddingSettingKey: knowledgeForm.value.embeddingSettingKey,
        documentIds: listToCsv(knowledgeForm.value.documentIds),
      })
      if (showMessage) {
        ElMessage.success('微调内容已保存')
      }
    } else {
      saved = await createProjectKnowledgeFromRequirement(requirement.value.requirementNo, {
        knowledgeType: knowledgeForm.value.knowledgeType,
        title: knowledgeForm.value.title,
        simpleDesc: knowledgeForm.value.simpleDesc,
        aiSettingKey: knowledgeForm.value.aiSettingKey,
        embeddingSettingKey: knowledgeForm.value.embeddingSettingKey,
        documentIds: listToCsv(knowledgeForm.value.documentIds),
        receiptRequirementNos: selectedKnowledgeReceiptItems.value.map((item) => item.requirementNo).join(','),
      })
      if (showMessage) {
        ElMessage.success('回执草稿已创建')
      }
    }
    upsertRequirementKnowledgeItem(saved)
    knowledgeForm.value.confirmedContent = saved.confirmedContent || saved.organizedContent || knowledgeForm.value.confirmedContent
    await loadData()
    return saved
  } finally {
    knowledgeSaving.value = false
  }
}

function buildSelectedReceiptKnowledgeSeed() {
  const current = requirement.value
  const lines: string[] = []
  if (current) {
    lines.push(`需求编号：${current.requirementNo}`)
    lines.push(`标题：${current.title}`)
    lines.push(`状态：${current.status}`)
    lines.push('')
  }
  lines.push('可提取执行回执：')
  selectedKnowledgeReceiptItems.value.forEach((item) => {
    lines.push(`## ${item.requirementNo} ${item.title}`)
    lines.push(`结果摘要：${item.resultSummary || '-'}`)
    lines.push('执行详情：')
    lines.push(item.executionDetails || '-')
  })
  return lines.join('\n').trim()
}

async function handlePrepareRequirementKnowledge() {
  if (knowledgeOperationLocked.value) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return
  }
  if (!await confirmReorganizeRequirementKnowledgeIfNeeded()) {
    return
  }
  const saved = await handleSaveRequirementKnowledgeDraft(false)
  if (!saved) {
    return
  }
  knowledgeOrganizing.value = true
  try {
    const updated = await organizeProjectKnowledge(saved.id, {
      aiSettingKey: knowledgeForm.value.aiSettingKey,
      documentIds: listToCsv(knowledgeForm.value.documentIds),
    })
    upsertRequirementKnowledgeItem(updated)
    if (updated.status === 'AI_ORGANIZING') {
      ElMessage.success('AI整理任务已提交，完成后会自动刷新')
      startKnowledgePolling()
      return
    }
    knowledgeForm.value.confirmedContent = updated.confirmedContent || updated.organizedContent || ''
    ElMessage.success('AI整理完成')
    await loadData()
  } finally {
    knowledgeOrganizing.value = false
  }
}

async function confirmReorganizeRequirementKnowledgeIfNeeded() {
  if (!hasRequirementKnowledgeOrganized()) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      '当前储备知识已经有 AI 整理结果，再次整理会覆盖现有整理内容和人工微调内容，确认继续？',
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

function hasRequirementKnowledgeOrganized() {
  const draft = draftKnowledgeItem.value
  return Boolean(
    draft?.status === 'AI_READY'
    || draft?.status === 'CONFIRMED'
    || draft?.organizedContent?.trim()
    || draft?.confirmedContent?.trim()
    || knowledgeForm.value.confirmedContent?.trim(),
  )
}

async function handleConfirmRequirementKnowledge() {
  if (knowledgeOperationLocked.value) {
    ElMessage.warning('AI整理中，请等待完成后再操作')
    return
  }
  if (!draftKnowledgeItem.value) {
    ElMessage.warning('请先读取回执并完成 AI 整理')
    return
  }
  if (!knowledgeForm.value.confirmedContent) {
    ElMessage.warning('确认内容不能为空')
    return
  }
  if (!knowledgeForm.value.embeddingSettingKey) {
    ElMessage.warning('请先选择向量配置（Embedding 模型）')
    return
  }
  const saved = await handleSaveRequirementKnowledgeDraft(false)
  if (!saved) {
    return
  }
  knowledgeConfirming.value = true
  try {
    await confirmProjectKnowledge(saved.id, {
      confirmedContent: knowledgeForm.value.confirmedContent,
      embeddingSettingKey: knowledgeForm.value.embeddingSettingKey,
    })
    ElMessage.success('项目储备知识已确认并写入向量库')
    await loadData()
  } finally {
    knowledgeConfirming.value = false
  }
}

function findRequirementForExecutionScope(requirementNo: string) {
  if (requirement.value?.requirementNo === requirementNo) {
    return requirement.value
  }
  return children.value.find((item) => item.requirementNo === requirementNo) || null
}

function isExecutableRequirement(item: Requirement) {
  if (item.executionMarker) {
    return false
  }
  return item.status === 'PENDING' || item.status === 'BLOCKED'
}

function isFinishedRequirement(item: Requirement) {
  return item.status === 'DONE' || item.status === 'CLOSED'
}

function workflowPreviousFinished(view: WorkflowNodeView) {
  return view.previous.every(isFinishedRequirement)
}

function canApproveWorkflowNode(view: WorkflowNodeView) {
  return Boolean(view.node.reviewRequiredFlag)
    && !view.node.reviewApprovedFlag
    && isExecutableRequirement(view.node)
    && workflowPreviousFinished(view)
}

function requirementNames(items: Requirement[]) {
  if (!items.length) {
    return '-'
  }
  return items.map((item) => item.requirementNo).join('、')
}

function workflowNodeOptionLabel(item: Requirement) {
  return `${item.requirementNo} / ${item.title}`
}

function workflowNodeOptionLabelByNo(requirementNo?: string) {
  if (!requirementNo) {
    return '-'
  }
  const node = workflowNodeMap.value.get(requirementNo)
  return node ? workflowNodeOptionLabel(node) : requirementNo
}

function syncWorkflowFlow(markDirty: boolean) {
  const existingPositions = new Map(flowNodes.value.map((node) => [node.id, node.position]))
  const columns = workflowColumns.value
  const nodes: WorkflowFlowNode[] = []
  columns.forEach((column) => {
    column.nodes.forEach((view, index) => {
      const savedPosition = existingPositions.get(view.node.requirementNo)
      nodes.push({
        id: view.node.requirementNo,
        type: 'requirementWorkflow',
        position: savedPosition || {
          x: column.depth * 390,
          y: index * 230,
        },
        data: { view },
        draggable: true,
        connectable: true,
      })
    })
  })
  flowNodes.value = nodes
  flowEdges.value = workflowEdges.value.map((edge) => buildFlowEdge(edge.fromRequirementNo, edge.toRequirementNo))
  if (selectedWorkflowEdgeId.value && !flowEdges.value.some((edge) => edge.id === selectedWorkflowEdgeId.value)) {
    selectedWorkflowEdgeId.value = ''
  }
  refreshWorkflowEdgeStyles()
  workflowDirty.value = markDirty
}

function buildFlowEdge(source: string, target: string): WorkflowFlowEdge {
  return {
    id: workflowEdgeId(source, target),
    source,
    target,
    type: 'smoothstep',
    markerEnd: MarkerType.ArrowClosed,
    animated: false,
    selected: selectedWorkflowEdgeId.value === workflowEdgeId(source, target),
    style: workflowEdgeStyle(workflowEdgeId(source, target)),
  }
}

function workflowEdgeStyle(edgeId: string): Record<string, string | number> {
  if (edgeId === selectedWorkflowEdgeId.value) {
    return {
      stroke: '#f97316',
      strokeWidth: 3.5,
      filter: 'drop-shadow(0 3px 8px rgba(249, 115, 22, 0.38))',
    }
  }
  return {
    stroke: '#64748b',
    strokeWidth: 2,
  }
}

function refreshWorkflowEdgeStyles() {
  flowEdges.value = flowEdges.value.map((edge) => ({
    ...edge,
    selected: edge.id === selectedWorkflowEdgeId.value,
    style: workflowEdgeStyle(edge.id),
  }))
}

function workflowEdgeId(source: string, target: string) {
  return `workflow:${source}->${target}`
}

function handleWorkflowConnectStart(event?: WorkflowConnectStartEvent) {
  if (taskOperationLocked.value) {
    workflowConnectCompleted.value = true
    workflowConnectStartNodeNo.value = ''
    return
  }
  workflowConnectCompleted.value = false
  workflowConnectStartNodeNo.value = event?.nodeId || ''
}

function handleWorkflowConnect(connection: WorkflowConnection) {
  if (taskOperationLocked.value) {
    ElMessage.warning('当前存在执行中的子模块，不能调整工作流关系')
    return
  }
  workflowConnectCompleted.value = true
  if (!connection.source || !connection.target) {
    return
  }
  if (connection.source === connection.target) {
    ElMessage.warning('流程关系不能指向自己')
    return
  }
  const id = workflowEdgeId(connection.source, connection.target)
  if (flowEdges.value.some((edge) => edge.id === id)) {
    ElMessage.warning('流程关系已存在')
    return
  }
  flowEdges.value = [...flowEdges.value, buildFlowEdge(connection.source, connection.target)]
  selectWorkflowEdge(id)
  workflowDirty.value = true
  refreshFlowNodeRelationData()
}

function handleWorkflowConnectEnd() {
  if (taskOperationLocked.value) {
    workflowConnectStartNodeNo.value = ''
    workflowConnectCompleted.value = false
    return
  }
  const sourceRequirementNo = workflowConnectStartNodeNo.value
  window.setTimeout(() => {
    if (sourceRequirementNo && !workflowConnectCompleted.value && !pageLocked.value) {
      void openWorkflowChildDialog(undefined, sourceRequirementNo)
    }
    workflowConnectStartNodeNo.value = ''
    workflowConnectCompleted.value = false
  })
}

function handleWorkflowPaneContextMenu(event?: MouseEvent) {
  event?.preventDefault()
  if (taskOperationLocked.value) {
    ElMessage.warning('当前存在执行中的子模块，不能新增子模块')
    return
  }
  void openWorkflowChildDialog()
}

function removeWorkflowFlowEdge(edgeId: string) {
  if (taskOperationLocked.value) {
    ElMessage.warning('当前存在执行中的子模块，不能调整工作流关系')
    return
  }
  flowEdges.value = flowEdges.value.filter((edge) => edge.id !== edgeId)
  if (selectedWorkflowEdgeId.value === edgeId) {
    selectedWorkflowEdgeId.value = ''
  }
  workflowDirty.value = true
  refreshFlowNodeRelationData()
  refreshWorkflowEdgeStyles()
}

function handleWorkflowEdgeClick(payload: EdgeMouseEvent) {
  payload.event?.preventDefault?.()
  payload.event?.stopPropagation?.()
  selectWorkflowEdge(payload.edge.id)
}

function handleWorkflowEdgeRowClick(row: WorkflowEdgeRow) {
  selectWorkflowEdge(row.id)
}

function handleWorkflowNodeDoubleClick(node: Requirement) {
  if (node.status !== 'PENDING') {
    ElMessage.warning('只有待处理子模块可以编辑')
    return
  }
  if (node.executionMarker) {
    ElMessage.warning('子模块已经进入执行流程，不能编辑')
    return
  }
  void openWorkflowChildDialog(node)
}

function selectWorkflowEdge(edgeId: string) {
  if (!flowEdges.value.some((edge) => edge.id === edgeId)) {
    return
  }
  selectedWorkflowEdgeId.value = edgeId
  refreshWorkflowEdgeStyles()
}

function clearSelectedWorkflowEdge() {
  if (!selectedWorkflowEdgeId.value) {
    return
  }
  selectedWorkflowEdgeId.value = ''
  refreshWorkflowEdgeStyles()
}

function workflowEdgePath(edgeProps: any) {
  return getSmoothStepPath({
    sourceX: edgeProps.sourceX,
    sourceY: edgeProps.sourceY,
    targetX: edgeProps.targetX,
    targetY: edgeProps.targetY,
    sourcePosition: edgeProps.sourcePosition,
    targetPosition: edgeProps.targetPosition,
  })
}

function workflowEdgeToolbarStyle(edgeProps: any) {
  const [, labelX, labelY] = workflowEdgePath(edgeProps)
  return {
    transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
  }
}

function workflowEdgeLabelById(edgeId: string) {
  const edge = flowEdges.value.find((item) => item.id === edgeId)
  if (!edge) {
    return '流程关系'
  }
  return `${workflowNodeOptionLabelByNo(edge.source)} -> ${workflowNodeOptionLabelByNo(edge.target)}`
}

function workflowEdgeRowClassName({ row }: { row: WorkflowEdgeRow }) {
  return row.selected ? 'is-selected-edge' : ''
}

function refreshFlowNodeRelationData() {
  const previousMap = new Map<string, string[]>()
  const nextMap = new Map<string, string[]>()
  flowEdges.value.forEach((edge) => {
    nextMap.set(edge.source, [...(nextMap.get(edge.source) || []), edge.target])
    previousMap.set(edge.target, [...(previousMap.get(edge.target) || []), edge.source])
  })
  flowNodes.value = flowNodes.value.map((node) => {
    const requirementNode = workflowNodeMap.value.get(node.id)
    if (!requirementNode) {
      return node
    }
    const previous = (previousMap.get(node.id) || [])
      .map((requirementNo) => workflowNodeMap.value.get(requirementNo))
      .filter(Boolean) as Requirement[]
    const next = (nextMap.get(node.id) || [])
      .map((requirementNo) => workflowNodeMap.value.get(requirementNo))
      .filter(Boolean) as Requirement[]
    return {
      ...node,
      data: {
        view: {
          ...node.data.view,
          previous,
          next,
          start: previous.length === 0,
        },
      },
    }
  })
}

async function handleSaveWorkflow() {
  if (!requirement.value) {
    return
  }
  if (taskOperationLocked.value) {
    ElMessage.warning('当前存在执行中的子模块，不能保存工作流关系')
    return
  }
  const detachedNodes = detachedWorkflowNodesForSave()
  if (detachedNodes.length > 0) {
    ElMessage.warning(`存在未接入流程的子模块：${detachedNodes.map((item) => item.requirementNo).join('、')}`)
    return
  }
  workflowSaving.value = true
  try {
    workflow.value = await saveRequirementWorkflow(requirement.value.requirementNo, { edges: buildWorkflowSaveEdges() })
    syncWorkflowFlow(false)
    ElMessage.success('工作流关系已保存')
    await loadData()
  } finally {
    workflowSaving.value = false
  }
}

function buildWorkflowSaveEdges(extraEdge?: { source: string; target: string }) {
  const result = flowEdges.value.map((edge) => ({
    fromRequirementNo: edge.source,
    toRequirementNo: edge.target,
  }))
  if (extraEdge && !result.some((edge) => edge.fromRequirementNo === extraEdge.source && edge.toRequirementNo === extraEdge.target)) {
    result.push({
      fromRequirementNo: extraEdge.source,
      toRequirementNo: extraEdge.target,
    })
  }
  return result
}

function detachedWorkflowNodesForSave() {
  const nodes = workflowNodes.value
  if (nodes.length <= 1) {
    return []
  }
  if (flowEdges.value.length === 0) {
    return nodes
  }
  const linked = new Set<string>()
  flowEdges.value.forEach((edge) => {
    linked.add(edge.source)
    linked.add(edge.target)
  })
  return nodes.filter((node) => !linked.has(node.requirementNo))
}

async function openWorkflowChildDialog(row?: Requirement | Event, attachSourceNo = '') {
  if (!requirement.value || pageLocked.value || taskOperationLocked.value) {
    if (taskOperationLocked.value) {
      ElMessage.warning('当前存在执行中的子模块，不能新增或编辑子模块')
    }
    return
  }
  const [agents, documents] = await Promise.all([
    fetchAgents(requirement.value.projectCode).catch(() => []),
    loadDocumentTreeOptions().catch(() => []),
  ])
  workflowAgents.value = agents
  documentTreeNodes.value = documents
  const editingRow = isRequirementRow(row) ? row : null
  if (editingRow) {
    workflowAttachSourceNo.value = ''
    workflowChildMode.value = 'edit'
    workflowEditingChild.value = editingRow
    workflowChildForm.value = {
      requirementNo: editingRow.requirementNo,
      title: editingRow.title,
      requirementDesc: editingRow.requirementDesc || '',
      priority: editingRow.priority || requirement.value.priority || 'MEDIUM',
      mainAgentCode: editingRow.mainAgentCode || '',
      currentStage: editingRow.currentStage || '',
      executionSteps: editingRow.executionSteps || '',
      resultExtractableFlag: editingRow.resultExtractableFlag !== false,
      reviewRequiredFlag: Boolean(editingRow.reviewRequiredFlag),
      mcpFileSearchEnabledFlag: Boolean(editingRow.mcpFileSearchEnabledFlag),
      documentIds: splitCodes(editingRow.documentIds),
      projectKnowledgeSearchEnabledFlag: Boolean(editingRow.projectKnowledgeSearchEnabledFlag),
      projectKnowledgeSearchLimit: editingRow.projectKnowledgeSearchLimit ?? 5,
      projectKnowledgeSearchMinScore: editingRow.projectKnowledgeSearchMinScore ?? 70,
      sessionStrategy: editingRow.sessionStrategy || requirement.value.sessionStrategy || 'NEW',
      preferredSessionCode: editingRow.preferredSessionCode || '',
    }
  } else {
    const nextNo = children.value.length + 1
    workflowAttachSourceNo.value = attachSourceNo
    workflowChildMode.value = 'create'
    workflowEditingChild.value = null
    workflowChildForm.value = {
      requirementNo: `${requirement.value.requirementNo}-${nextNo}`,
      title: attachSourceNo ? `后续子模块 ${nextNo}` : `子模块 ${nextNo}`,
      requirementDesc: '',
      priority: requirement.value.priority || 'MEDIUM',
      mainAgentCode: '',
      currentStage: '待开发',
      executionSteps: '',
      resultExtractableFlag: true,
      reviewRequiredFlag: false,
      mcpFileSearchEnabledFlag: false,
      documentIds: [],
      projectKnowledgeSearchEnabledFlag: false,
      projectKnowledgeSearchLimit: 5,
      projectKnowledgeSearchMinScore: 70,
      sessionStrategy: requirement.value.sessionStrategy || 'NEW',
      preferredSessionCode: '',
    }
  }
  workflowChildDialogVisible.value = true
}

function isRequirementRow(value: Requirement | Event | undefined): value is Requirement {
  return Boolean(value && 'requirementNo' in value && typeof value.requirementNo === 'string')
}

function resetWorkflowChildAttach() {
  workflowAttachSourceNo.value = ''
}

async function handleSaveWorkflowChild() {
  if (!requirement.value) {
    return
  }
  if (taskOperationLocked.value) {
    ElMessage.warning('当前存在执行中的子模块，不能保存子模块')
    return
  }
  const requirementNo = workflowChildForm.value.requirementNo.trim()
  const title = workflowChildForm.value.title.trim()
  if (!requirementNo || !title) {
    ElMessage.warning('请填写子模块编号和标题')
    return
  }
  workflowChildSaving.value = true
  try {
    if (workflowChildMode.value === 'edit' && workflowEditingChild.value) {
      const current = workflowEditingChild.value
      const payload: UpdateRequirementPayload = {
        title,
        requirementDesc: optionalText(workflowChildForm.value.requirementDesc),
        priority: workflowChildForm.value.priority,
        sortNo: current.sortNo || 0,
        status: current.status,
        source: optionalText(current.source),
        mainAgentCode: optionalText(workflowChildForm.value.mainAgentCode),
        sessionStrategy: workflowChildForm.value.sessionStrategy || 'NEW',
        preferredSessionCode: workflowChildForm.value.sessionStrategy === 'REUSE_SELECTED'
          ? optionalText(workflowChildForm.value.preferredSessionCode)
          : undefined,
        currentStage: optionalText(workflowChildForm.value.currentStage),
        expectedDeadline: current.expectedDeadline,
        createdBy: optionalText(current.createdBy),
        executionSteps: optionalText(workflowChildForm.value.executionSteps),
        executionMode: current.executionMode || requirement.value.executionMode || 'WORKFLOW',
        resultExtractableFlag: workflowChildForm.value.resultExtractableFlag !== false,
        reviewRequiredFlag: workflowChildForm.value.reviewRequiredFlag === true,
        mcpFileSearchEnabledFlag: workflowChildForm.value.mcpFileSearchEnabledFlag === true,
        documentIds: joinCodes(workflowChildForm.value.documentIds),
        projectKnowledgeSearchEnabledFlag: workflowChildForm.value.projectKnowledgeSearchEnabledFlag === true,
        projectKnowledgeSearchLimit: workflowChildForm.value.projectKnowledgeSearchEnabledFlag
          ? Number(workflowChildForm.value.projectKnowledgeSearchLimit || 5)
          : undefined,
        projectKnowledgeSearchMinScore: workflowChildForm.value.projectKnowledgeSearchEnabledFlag
          ? Number(workflowChildForm.value.projectKnowledgeSearchMinScore ?? 70)
          : undefined,
      }
      await updateRequirement(current.requirementNo, payload)
      workflowChildDialogVisible.value = false
      ElMessage.success('子模块已保存')
      await loadData()
      return
    }
    const attachSourceNo = workflowAttachSourceNo.value
    const payload: CreateRequirementPayload = {
      requirementNo,
      projectCode: requirement.value.projectCode,
      title,
      requirementDesc: optionalText(workflowChildForm.value.requirementDesc),
      priority: workflowChildForm.value.priority,
      requirementType: 'SUB',
      sortNo: children.value.length + 1,
      status: 'PENDING',
      source: `WORKFLOW:${requirement.value.requirementNo}`,
      mainAgentCode: optionalText(workflowChildForm.value.mainAgentCode),
      sessionStrategy: workflowChildForm.value.sessionStrategy || 'NEW',
      preferredSessionCode: workflowChildForm.value.sessionStrategy === 'REUSE_SELECTED'
        ? optionalText(workflowChildForm.value.preferredSessionCode)
        : undefined,
      currentStage: optionalText(workflowChildForm.value.currentStage),
      executionSteps: optionalText(workflowChildForm.value.executionSteps),
      executionMode: requirement.value.executionMode || 'WORKFLOW',
      resultExtractableFlag: workflowChildForm.value.resultExtractableFlag !== false,
      reviewRequiredFlag: workflowChildForm.value.reviewRequiredFlag === true,
      mcpFileSearchEnabledFlag: workflowChildForm.value.mcpFileSearchEnabledFlag === true,
      documentIds: joinCodes(workflowChildForm.value.documentIds),
      projectKnowledgeSearchEnabledFlag: workflowChildForm.value.projectKnowledgeSearchEnabledFlag === true,
      projectKnowledgeSearchLimit: workflowChildForm.value.projectKnowledgeSearchEnabledFlag
        ? Number(workflowChildForm.value.projectKnowledgeSearchLimit || 5)
        : undefined,
      projectKnowledgeSearchMinScore: workflowChildForm.value.projectKnowledgeSearchEnabledFlag
        ? Number(workflowChildForm.value.projectKnowledgeSearchMinScore ?? 70)
        : undefined,
    }
    const created = await createChildRequirement(requirement.value.requirementNo, payload)
    if (attachSourceNo) {
      workflow.value = await saveRequirementWorkflow(requirement.value.requirementNo, {
        edges: buildWorkflowSaveEdges({ source: attachSourceNo, target: created.requirementNo }),
      })
      workflowDirty.value = false
    }
    workflowChildDialogVisible.value = false
    resetWorkflowChildAttach()
    ElMessage.success(attachSourceNo ? '子模块已创建并接入后续流程' : '子模块已创建，可在画布中拖线编排流程')
    await loadData()
  } finally {
    workflowChildSaving.value = false
  }
}

async function handleApproveWorkflowReview(row: Requirement) {
  if (!requirement.value) {
    return
  }
  await ElMessageBox.confirm('审核通过后，该子模块满足前置条件时会自动下发执行。是否继续？', '人工审核', {
    type: 'warning',
  })
  workflowDispatching.value = true
  try {
    await approveRequirementReview(row.requirementNo)
    const dispatched = await dispatchReadyWorkflowTasks(requirement.value.requirementNo)
    if (dispatched.length > 0) {
      ElMessage.success(`审核已通过，并下发 ${dispatched.length} 个工作流任务`)
    } else {
      ElMessage.success('审核已通过，当前没有可下发的在线 Agent 或默认会话')
    }
    await loadData()
  } finally {
    workflowDispatching.value = false
  }
}

async function handleDispatchWorkflowReady() {
  if (!requirement.value) {
    return
  }
  if (workflowRunning.value) {
    ElMessage.warning('已有工作流子模块正在执行，不能重复从头执行')
    return
  }
  workflowDispatching.value = true
  try {
    const dispatched = await dispatchReadyWorkflowTasks(requirement.value.requirementNo)
    const count = dispatched.length
    if (count > 0) {
      ElMessage.success(`已下发 ${count} 个工作流任务`)
    } else {
      ElMessage.warning('当前没有可自动下发的工作流任务，请确认 Agent 在线且默认会话已就绪')
    }
    await loadData()
  } finally {
    workflowDispatching.value = false
  }
}

function findReusableLink(item: Requirement) {
  return links.value.find((link) =>
    link.requirementNo === item.requirementNo
    && link.agentCode === item.mainAgentCode
    && link.status !== 'DOING'
    && link.status !== 'DONE'
    && link.status !== 'SKIPPED',
  )
}

function buildRequirementExecutionTask(item: Requirement, link?: RequirementLink): ExecutionTask {
  return {
    source: 'REQUIREMENT',
    sourceLabel: '子模块',
    requirementNo: item.requirementNo,
    linkId: link?.id,
    linkType: link?.linkType,
    taskTitle: item.title,
    taskDesc: item.requirementDesc || item.executionSteps || '',
    agentCode: item.mainAgentCode || '',
    status: item.status,
  }
}

async function openExecuteDialog(scopeRequirementNo = '') {
  executionScopeRequirementNo.value = scopeRequirementNo
  executeDialogVisible.value = true
  executeLoading.value = true
  try {
    await refreshExecutionData()
    onlineClients.value = await fetchClientNodes()
  } finally {
    executeLoading.value = false
  }
}

async function refreshExecutionData() {
  if (!requirement.value) {
    return
  }
  if (requirement.value.requirementType === 'MASTER') {
    links.value = await fetchRequirementLinksWithChildren(requirement.value.requirementNo)
    children.value = await fetchRequirementChildren(requirement.value.requirementNo)
    requirement.value = await fetchRequirement(requirement.value.requirementNo)
    workflow.value = requirement.value.executionMode === 'WORKFLOW'
      ? await fetchRequirementWorkflow(requirement.value.requirementNo)
      : null
    syncWorkflowFlow(false)
    return
  }
  links.value = await fetchRequirementLinks(requirement.value.requirementNo)
  requirement.value = await fetchRequirement(requirement.value.requirementNo)
  workflow.value = requirement.value.executionMode === 'WORKFLOW' && requirement.value.rootRequirementNo
    ? await fetchRequirementWorkflow(requirement.value.rootRequirementNo)
    : null
  syncWorkflowFlow(false)
}

async function openTaskDispatch(target: ExecutionTarget) {
  selectedExecutionTarget.value = target
  dispatchPrompt.value = buildDispatchPrompt(target)
  availableSessions.value = await fetchClientSessions(target.client.clientCode, target.agent.agentCode).catch(() => [])
  selectedSessionId.value = availableSessions.value.find((item) => item.defaultFlag && item.status === 'READY')?.sessionId || ''
  taskDialogVisible.value = true
}

async function handleDispatchTask() {
  if (!selectedExecutionTarget.value) {
    return
  }
  dispatching.value = true
  try {
    const target = selectedExecutionTarget.value
    await dispatchClientCommand({
      clientCode: target.client.clientCode,
      agentCode: target.agent.agentCode,
      sessionId: selectedSessionId.value || undefined,
      requirementNo: target.task.requirementNo,
      linkId: target.task.linkId,
      title: target.task.taskTitle,
      prompt: dispatchPrompt.value,
    })
    ElMessage.success('任务已下发到客户端')
    taskDialogVisible.value = false
    executeDialogVisible.value = false
    await loadData()
  } finally {
    dispatching.value = false
  }
}

function buildDispatchPrompt(target: ExecutionTarget) {
  const rootRequirementNo = requirement.value?.rootRequirementNo || requirement.value?.requirementNo || props.requirementNo
  const scopeLabel = executionScopeRequirementNo.value ? '当前子模块' : '全部可执行子模块'
  const linkId = target.task.linkId ? String(target.task.linkId) : '-'
  const workflowLines = target.requirement?.executionMode === 'WORKFLOW'
    ? [
        '',
        '工作流上下文：',
        `1. 当前需求是工作流模式，执行前调用 /api/requirements/${target.task.requirementNo}/workflow-results 获取已完成节点回执。`,
        '2. 只整合接口返回的可提取回执，不要读取或生成本地 receipt 文件。',
      ]
    : []
  return [
    '当前使用已初始化的客户端长会话执行任务，不要重复初始化或重新加载技能。',
    '需要查询任务、创建链路或回写结果时，直接使用会话中已初始化的 ai-api-agent-integration 能力。',
    '',
    `执行范围：${scopeLabel}`,
    `当前根需求：${rootRequirementNo}`,
    `本次执行子模块：${target.task.requirementNo}`,
    `已有开发链路 ID：${linkId}`,
    `任务标题：${target.task.taskTitle}`,
    `任务描述：${target.task.taskDesc || '-'}`,
    `负责 Agent：${target.task.agentCode}`,
    ...workflowLines,
    '',
    '执行要求：',
    '1. 必须使用 ai-api-agent-integration 技能查询当前需求和子模块，不要只输出文字说明。',
    '2. 若没有已有开发链路 ID，请先为本次执行子模块创建一条开发链路，再围绕该链路执行。',
    '3. 完成后必须通过 ai-api-agent-integration 回写开发链路为 DONE；无法完成时回写 BLOCKED，并给出原因、执行过程和详细回执。',
    '4. 回执内容只需要回写到 ai-api 的 executionDetails 字段，不要在当前工作目录创建 receipt.md、receipt_*.md 或其他任务回执文件。',
    '5. 回写后再次查询该子模块，确认状态已经同步。',
  ].join('\n')
}

function parseSteps(steps?: string) {
  if (!steps) {
    return []
  }
  return steps
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
}

function optionalText(value?: string) {
  const next = value?.trim()
  return next ? next : undefined
}

async function loadDocumentTreeOptions() {
  return fetchMarkdownDocumentTree()
}

function splitCodes(value?: string) {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function joinCodes(value: string[]) {
  const text = value.map((item) => item.trim()).filter(Boolean).join(',')
  return text || undefined
}

function emptyRequirementKnowledgeForm(): RequirementKnowledgeForm {
  return {
    knowledgeType: 'COMMON_ISSUE',
    title: '',
    simpleDesc: '',
    aiSettingKey: '',
    embeddingSettingKey: '',
    documentIds: [],
    confirmedContent: '',
  }
}

function defaultTextAiSettingKey() {
  return textAiSettings.value[0]?.settingKey || ''
}

function defaultEmbeddingSettingKey() {
  return embeddingAiSettings.value[0]?.settingKey || ''
}

function modelPurpose(item: AiModelSetting) {
  if (item.modelPurpose) {
    return item.modelPurpose
  }
  return item.supportImageFlag ? 'IMAGE' : 'LANGUAGE'
}

function csvToList(value?: string) {
  return splitCodes(value)
}

function listToCsv(value: string[]) {
  return joinCodes(value)
}
</script>

<style>
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
</style>

<style scoped>
.requirement-detail__subtitle {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.requirement-detail__content {
  position: relative;
  min-height: 240px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.requirement-detail__steps {
  white-space: pre-line;
}

.workflow-card :deep(.el-card__body) {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.workflow-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.workflow-header__title {
  color: var(--admin-title);
  font-size: 16px;
  font-weight: 650;
}

.workflow-header__meta {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.workflow-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-flow-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
}

.workflow-flow {
  height: 620px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 20px;
  background: linear-gradient(135deg, #f8fafc, #ffffff);
  overflow: hidden;
}

.workflow-edge-list {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 18px;
  background: #ffffff;
}

.workflow-edge-list__title {
  color: var(--admin-title);
  font-size: 13px;
  font-weight: 600;
}

.workflow-edge-list :deep(.el-table__row) {
  cursor: pointer;
}

.workflow-edge-list :deep(.is-selected-edge > td.el-table__cell) {
  background: #fff7ed !important;
}

.workflow-edge-list :deep(.is-selected-edge .cell) {
  color: #9a3412;
  font-weight: 600;
}

.workflow-edge-toolbar {
  position: absolute;
  z-index: 20;
  display: inline-flex;
  align-items: center;
  max-width: 260px;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid rgba(249, 115, 22, 0.35);
  border-radius: 999px;
  background: rgba(255, 247, 237, 0.96);
  box-shadow: 0 12px 30px rgba(154, 52, 18, 0.18);
  color: #9a3412;
  font-size: 12px;
  font-weight: 600;
  pointer-events: all;
  white-space: nowrap;
}

.workflow-node {
  display: grid;
  gap: 9px;
  width: 300px;
  min-height: 112px;
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.42);
  border-left: 4px solid #94a3b8;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
}

.workflow-node.is-editable {
  cursor: pointer;
}

.workflow-node.is-editable:hover {
  border-color: rgba(59, 130, 246, 0.42);
  box-shadow: 0 20px 48px rgba(37, 99, 235, 0.12);
}

.workflow-node :deep(.vue-flow__handle) {
  width: 12px;
  height: 12px;
  border: 2px solid var(--el-bg-color);
  background: var(--el-color-primary);
}

.workflow-node.is-ready {
  border-left-color: #16a34a;
}

.workflow-node.is-done {
  border-left-color: #22c55e;
  background: #f8fff9;
}

.workflow-node.is-in_progress,
.workflow-node.is-testing {
  border-left-color: #2563eb;
  background: #f8fbff;
}

.workflow-node.is-blocked {
  border-left-color: #dc2626;
  background: #fffafa;
}

.workflow-node__title {
  color: var(--admin-title);
  font-weight: 600;
  line-height: 1.5;
}

.workflow-node__tags,
.workflow-node__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.workflow-node__meta {
  display: grid;
  gap: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.workflow-child-form {
  display: grid;
  gap: 14px;
}

.workflow-empty {
  display: grid;
  place-items: center;
  gap: 10px;
  min-height: 360px;
  border: 1px dashed var(--el-border-color);
  border-radius: 20px;
  background: linear-gradient(135deg, #f8fafc, #ffffff);
}

.workflow-empty__title {
  color: var(--admin-title);
  font-size: 18px;
  font-weight: 650;
}

.workflow-empty__desc {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .workflow-flow-shell {
    grid-template-columns: 1fr;
  }

  .workflow-edge-list {
    min-width: 0;
  }

  .workflow-header {
    align-items: flex-start;
    flex-direction: column;
  }
}

.requirement-detail__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.execute-dialog,
.task-dispatch-dialog,
.ai-analyze-form {
  display: grid;
  gap: 16px;
}

.execute-dialog__section-title {
  color: var(--admin-title);
  font-size: 13px;
  font-weight: 600;
  margin-top: 4px;
}

.requirement-detail__description-editor {
  margin-top: 16px;
}

.requirement-progress {
  display: grid;
  gap: 18px;
}

.requirement-progress__flow {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.requirement-progress__stage {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 14px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.requirement-progress__stage.is-done {
  color: var(--el-color-success);
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}

.requirement-progress__stage.is-current {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.requirement-progress__stage.is-blocked {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}

.requirement-progress__icon {
  width: 44px;
  height: 44px;
}

.requirement-progress__icon circle {
  fill: none;
  stroke: currentColor;
  stroke-width: 3;
}

.requirement-progress__icon path {
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 4;
}

.requirement-progress__label {
  font-size: 13px;
  font-weight: 600;
}

.knowledge-dialog {
  display: grid;
  gap: 14px;
}

.knowledge-receipts-card {
  border-color: #dbe4ef;
}

.knowledge-receipts-card__header {
  align-items: center;
  display: flex;
  font-weight: 700;
  justify-content: space-between;
}

.w-full {
  width: 100%;
}

.ml-6 {
  margin-left: 6px;
}
</style>
