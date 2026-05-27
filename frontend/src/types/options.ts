export const projectStatusOptions = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
]

export const aiModelPurposeOptions = [
  { label: '语言', value: 'LANGUAGE' },
  { label: '向量', value: 'VECTOR' },
  { label: '图片', value: 'IMAGE' },
]

export const aiModelPurposeLabelMap = Object.fromEntries(
  aiModelPurposeOptions.map((item) => [item.value, item.label]),
)

export const projectMarkdownSyncModeOptions = [
  { label: '覆盖本地文件', value: 'OVERWRITE' },
  { label: '遇到冲突时取消', value: 'CANCEL' },
]

export const projectMarkdownBaseSyncModeOptions = [
  { label: '独立', value: 'INDEPENDENT' },
  { label: '自动更新', value: 'AUTO_UPDATE' },
]

export const projectMarkdownChangeSourceLabelMap: Record<string, string> = {
  SERVER: '服务端',
  CLIENT: '客户端',
  ROLLBACK: '回滚',
}

export const projectMarkdownBaseKeyLabels: Record<string, string> = {
  AGENTS: 'AGENTS.md',
  SOUL: 'SOUL.md',
  USER: 'USER.md',
  MEMORY: 'MEMORY.md',
  LEARNINGS: '.learnings/LEARNINGS.md',
}

export const projectMarkdownRoleOptions = [
  { label: '通用', value: 'COMMON' },
  { label: '主控', value: 'MAIN' },
  { label: '开发', value: 'DEVELOPER' },
  { label: '测试', value: 'TESTER' },
  { label: '评审', value: 'REVIEWER' },
  { label: '运维', value: 'OPS' },
]

export const projectMarkdownRoleLabelMap = Object.fromEntries(
  projectMarkdownRoleOptions.map((item) => [item.value, item.label]),
)

export const projectDocumentUsageOptions = [
  { label: '需求 AI 分析', value: 'REQUIREMENT_AI_ANALYSIS' },
  { label: '缺陷 AI 分析', value: 'DEFECT_AI_ANALYSIS' },
  { label: '通用', value: 'AGENT_COMMON' },
  { label: '主控', value: 'AGENT_MAIN' },
  { label: '开发', value: 'AGENT_DEVELOPER' },
  { label: '测试', value: 'AGENT_TESTER' },
  { label: '运维', value: 'AGENT_OPS' },
  { label: '评审', value: 'AGENT_REVIEWER' },
]

export const projectDocumentUsageLabelMap = Object.fromEntries(
  projectDocumentUsageOptions.map((item) => [item.value, item.label]),
)

export const projectKnowledgeTypeOptions = [
  { label: '项目常见问题', value: 'COMMON_ISSUE' },
  { label: '流程说明', value: 'PROCESS_GUIDE' },
]

export const projectKnowledgeTypeLabelMap = Object.fromEntries(
  projectKnowledgeTypeOptions.map((item) => [item.value, item.label]),
)

export const projectKnowledgeStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: 'AI整理中', value: 'AI_ORGANIZING' },
  { label: '已整理', value: 'AI_READY' },
  { label: '已确认', value: 'CONFIRMED' },
]

export const projectKnowledgeStatusLabelMap = Object.fromEntries(
  projectKnowledgeStatusOptions.map((item) => [item.value, item.label]),
)

export const projectKnowledgeVectorStatusOptions = [
  { label: '待生成', value: 'PENDING' },
  { label: '已入库', value: 'READY' },
  { label: '需更新', value: 'STALE' },
  { label: '失败', value: 'FAILED' },
]

export const projectKnowledgeVectorStatusLabelMap = Object.fromEntries(
  projectKnowledgeVectorStatusOptions.map((item) => [item.value, item.label]),
)

export const requirementStatusOptions = [
  { label: '待分发', value: 'PENDING' },
  { label: 'AI分析中', value: 'ANALYZING' },
  { label: '开发中', value: 'IN_PROGRESS' },
  { label: '测试中', value: 'TESTING' },
  { label: '阻塞', value: 'BLOCKED' },
  { label: '已完成', value: 'DONE' },
  { label: '已关闭', value: 'CLOSED' },
]

export const requirementTypeOptions = [
  { label: '总需求', value: 'MASTER' },
  { label: '子模块', value: 'SUB' },
]

export const requirementExecutionModeOptions = [
  { label: '正常模式', value: 'NORMAL' },
  { label: '工作流模式', value: 'WORKFLOW' },
]

export const requirementExecutionModeLabelMap = Object.fromEntries(
  requirementExecutionModeOptions.map((item) => [item.value, item.label]),
)

export const sessionStrategyOptions = [
  { label: '新建会话', value: 'NEW' },
  { label: '沿用指定会话', value: 'REUSE_SELECTED' },
  { label: '沿用最近会话', value: 'REUSE_LATEST' },
]

export const linkStatusOptions = [
  { label: '待处理', value: 'TODO' },
  { label: '处理中', value: 'DOING' },
  { label: '已完成', value: 'DONE' },
  { label: '阻塞', value: 'BLOCKED' },
  { label: '跳过', value: 'SKIPPED' },
]

export const linkTypeOptions = [
  { label: '后端', value: 'BACKEND' },
  { label: '前端', value: 'FRONTEND' },
  { label: '接口', value: 'API' },
  { label: '数据库', value: 'DB' },
  { label: '测试', value: 'TEST' },
  { label: '文档', value: 'DOCS' },
  { label: '部署', value: 'DEPLOYMENT' },
  { label: '评审', value: 'REVIEW' },
]

export const agentRoleOptions = [
  { label: '主控', value: 'MAIN' },
  { label: '开发', value: 'DEVELOPER' },
  { label: '测试', value: 'TESTER' },
  { label: '评审', value: 'REVIEWER' },
  { label: '运维', value: 'OPS' },
]

export const agentStatusOptions = [
  { label: '在线', value: 'ONLINE' },
  { label: '离线', value: 'OFFLINE' },
  { label: '禁用', value: 'DISABLED' },
]

export const agentEngineTypeOptions = [
  { label: 'codex', value: 'CODEX' },
  { label: 'qoder', value: 'QODER' },
  { label: 'claude', value: 'CLAUDE' },
]

export const agentEngineTypeLabelMap = Object.fromEntries(
  agentEngineTypeOptions.map((item) => [item.value, item.label]),
)

export const clientNodeStatusOptions = [
  { label: '在线', value: 'ONLINE' },
  { label: '离线', value: 'OFFLINE' },
  { label: '禁用', value: 'DISABLED' },
]

export const testResultOptions = [
  { label: '通过', value: 'PASS' },
  { label: '失败', value: 'FAIL' },
  { label: '阻塞', value: 'BLOCKED' },
]

export const callbackModeOptions = [
  { label: '轮询拉取', value: 'PULL' },
  { label: '主动回传', value: 'CALLBACK' },
]

export const mcpTransportProtocolOptions = [
  { label: 'SSE协议', value: 'SSE' },
  { label: 'REST接口', value: 'REST' },
  { label: '不注入', value: 'NONE' },
]

export const mcpTransportProtocolLabelMap = Object.fromEntries(
  mcpTransportProtocolOptions.map((item) => [item.value, item.label]),
)

export const imagePromptTemplateTypeOptions = [
  { label: '正面', value: 'POSITIVE' },
  { label: '负面', value: 'NEGATIVE' },
]

export const imagePromptTemplateTypeLabelMap = Object.fromEntries(
  imagePromptTemplateTypeOptions.map((item) => [item.value, item.label]),
)

export const imageGenerationTypeOptions = [
  { label: '文生图', value: 'TEXT_TO_IMAGE' },
  { label: '图生图', value: 'IMAGE_EDIT' },
]

export const imageGenerationTypeLabelMap = Object.fromEntries(
  imageGenerationTypeOptions.map((item) => [item.value, item.label]),
)

export const imageGenerationStatusOptions = [
  { label: '排队中', value: 'PENDING' },
  { label: '生成中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

export const imageGenerationStatusLabelMap = Object.fromEntries(
  imageGenerationStatusOptions.map((item) => [item.value, item.label]),
)

export const imageSizeOptions = [
  { label: '1024x1024', value: '1024x1024' },
  { label: '1536x1024', value: '1536x1024' },
  { label: '1024x1536', value: '1024x1536' },
  { label: '1536x864', value: '1536x864' },
  { label: '3840x2160', value: '3840x2160' },
  { label: '自动', value: 'auto' },
]

export const imageQualityOptions = [
  { label: '高', value: 'high' },
  { label: '中', value: 'medium' },
  { label: '低', value: 'low' },
  { label: '自动', value: 'auto' },
]

export const imageOutputFormatOptions = [
  { label: 'PNG', value: 'png' },
  { label: 'JPEG', value: 'jpeg' },
]

export const imageBackgroundOptions = [
  { label: '不透明', value: 'opaque' },
  { label: '自动', value: 'auto' },
]

export const imageModerationOptions = [
  { label: '自动', value: 'auto' },
  { label: '低', value: 'low' },
]

export const imageResponseFormatOptions = [
  { label: 'b64_json', value: 'b64_json' },
]

export const imageInputFidelityOptions = [
  { label: '高保真', value: 'high' },
  { label: '自动', value: 'auto' },
]

export const defectPlatformOptions = [
  { label: '禅道', value: 'ZENTAO' },
  { label: '云效', value: 'YUNXIAO' },
]

export const defectPushStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '分析中', value: 'ANALYZING' },
  { label: '已分析', value: 'ANALYZED' },
  { label: '已推送', value: 'PUSHED' },
]
