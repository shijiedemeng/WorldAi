import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { RouteLocationNormalizedLoaded, RouteLocationRaw, Router } from 'vue-router'
import type { ConsolePushEvent } from '@/composables/useConsolePush'

export interface RouteTab {
  key: string
  title: string
  fullPath: string
  closable: boolean
}

export interface ConsoleNotification {
  id: string
  type: string
  title: string
  message: string
  tone: 'info' | 'success' | 'warning' | 'danger'
  createdAt: string
}

export const useAppStore = defineStore('app', () => {
  const menuRouteNames = new Set([
    'dashboard',
    'projects',
    'project-knowledge',
    'markdown-documents',
    'requirements',
    'agents',
    'skills',
    'clients',
    'image-settings',
    'image-prompts',
    'image-records',
    'defects',
    'defect-records',
    'ai-analysis-logs',
    'mcp-test-tool',
    'system-settings',
  ])
  const secondaryRouteParentMap: Record<string, string> = {
    'project-create': 'projects',
    'project-edit': 'projects',
    'project-markdown-config': 'projects',
    'requirement-create': 'requirements',
    'requirement-child-create': 'requirements',
    'requirement-edit': 'requirements',
    'requirement-detail': 'requirements',
    'inspection-detail': 'requirements',
    'agent-create': 'agents',
    'agent-edit': 'agents',
    'agent-tasks': 'agents',
    'defect-record-detail': 'defect-records',
    'defect-record-analysis': 'defect-records',
  }

  const title = ref('AI API 管理台')
  const pageTitle = ref('仪表盘')
  const tabs = ref<RouteTab[]>([])
  const activeTabKey = ref('')
  const tabCacheVersions = ref<Record<string, number>>({})
  const consolePushStatus = ref<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED'>('DISCONNECTED')
  const consoleNotifications = ref<ConsoleNotification[]>([])
  const consoleToasts = ref<ConsoleNotification[]>([])

  const fullTitle = computed(() => `${pageTitle.value} - ${title.value}`)

  function setPageTitle(value: string) {
    pageTitle.value = value
    document.title = fullTitle.value
  }

  function syncRouteTab(route: RouteLocationNormalizedLoaded) {
    const key = buildTabKey(route)
    const nextTab: RouteTab = {
      key,
      title: buildRouteTitle(route),
      fullPath: route.fullPath,
      closable: route.name !== 'dashboard',
    }
    const index = tabs.value.findIndex((item) => item.key === key)
    if (index >= 0) {
      tabs.value[index] = nextTab
    } else {
      tabs.value.push(nextTab)
    }
    activeTabKey.value = key
    setPageTitle(nextTab.title)
  }

  function openTab(tab: RouteTab, router: Router) {
    if (tab.key === activeTabKey.value) {
      return
    }
    router.push(tab.fullPath)
  }

  function closeTab(key: string, router: Router) {
    const index = tabs.value.findIndex((item) => item.key === key)
    if (index < 0 || !tabs.value[index].closable) {
      return
    }
    const closingActive = activeTabKey.value === key
    tabs.value.splice(index, 1)
    if (!closingActive) {
      dropTabCache(key)
      return
    }
    const next = tabs.value[index] || tabs.value[index - 1] || tabs.value[0]
    if (next) {
      router.push(next.fullPath).finally(() => dropTabCache(key))
    } else {
      router.push('/dashboard').finally(() => dropTabCache(key))
    }
  }

  function closeActiveTabAndOpen(to: RouteLocationRaw, router: Router) {
    const key = activeTabKey.value
    const index = tabs.value.findIndex((item) => item.key === key)
    if (index < 0 || !tabs.value[index].closable) {
      router.push(to)
      return
    }
    tabs.value.splice(index, 1)
    router.push(to).finally(() => dropTabCache(key))
  }

  function getRouteCacheKey(route: RouteLocationNormalizedLoaded) {
    const key = buildTabKey(route)
    return `${key}:${buildRouteInstanceKey(route)}:${tabCacheVersions.value[key] || 0}`
  }

  function dropTabCache(key: string) {
    tabCacheVersions.value[key] = (tabCacheVersions.value[key] || 0) + 1
  }

  function setConsolePushStatus(status: 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED') {
    consolePushStatus.value = status
  }

  function recordConsolePush(event: ConsolePushEvent) {
    const notification = buildNotification(event)
    if (!notification) {
      return
    }
    consoleNotifications.value = [notification, ...consoleNotifications.value].slice(0, 80)
    consoleToasts.value = [notification, ...consoleToasts.value]
    window.setTimeout(() => {
      consoleToasts.value = consoleToasts.value.filter((item) => item.id !== notification.id)
    }, 3000)
  }

  function buildTabKey(route: RouteLocationNormalizedLoaded) {
    const name = String(route.name || route.path)
    if (menuRouteNames.has(name)) {
      return `level1:${name}`
    }
    const parentName = secondaryRouteParentMap[name]
    if (parentName) {
      return `level2:${parentName}`
    }
    return `level2:${parentFromPath(route.path)}`
  }

  function buildRouteInstanceKey(route: RouteLocationNormalizedLoaded) {
    const name = String(route.name || route.path)
    const entries = Object.keys(route.params)
      .sort()
      .map((key) => [key, route.params[key]])
    return `${name}:${JSON.stringify(entries)}`
  }

  function parentFromPath(path: string) {
    if (path.startsWith('/projects')) {
      return 'projects'
    }
    if (path.startsWith('/project-knowledge')) {
      return 'project-knowledge'
    }
    if (path.startsWith('/markdown-documents')) {
      return 'markdown-documents'
    }
    if (path.startsWith('/requirements') || path.startsWith('/inspection')) {
      return 'requirements'
    }
    if (path.startsWith('/agents')) {
      return 'agents'
    }
    if (path.startsWith('/skills')) {
      return 'skills'
    }
    if (path.startsWith('/clients')) {
      return 'clients'
    }
    if (path.startsWith('/image-settings')) {
      return 'image-settings'
    }
    if (path.startsWith('/image-prompts')) {
      return 'image-prompts'
    }
    if (path.startsWith('/image-records')) {
      return 'image-records'
    }
    if (path.startsWith('/defects/records')) {
      return 'defect-records'
    }
    if (path.startsWith('/defects')) {
      return 'defects'
    }
    if (path.startsWith('/logs/ai-analysis')) {
      return 'ai-analysis-logs'
    }
    if (path.startsWith('/system-tools/mcp-test')) {
      return 'mcp-test-tool'
    }
    if (path.startsWith('/system-settings')) {
      return 'system-settings'
    }
    return 'dashboard'
  }

  function buildRouteTitle(route: RouteLocationNormalizedLoaded) {
    const requirementNo = textParam(route.params.requirementNo)
    const parentRequirementNo = textParam(route.params.parentRequirementNo)
    const projectCode = textParam(route.params.projectCode)
    const agentCode = textParam(route.params.agentCode)
    const externalDefectId = textParam(route.params.externalDefectId)
    switch (route.name) {
      case 'projects':
        return '项目管理'
      case 'project-create':
        return '新建项目'
      case 'project-edit':
        return projectCode ? `编辑项目 ${projectCode}` : '项目编辑'
      case 'project-markdown-config':
        return projectCode ? `Markdown ${projectCode}` : '项目 Markdown'
      case 'project-knowledge':
        return '项目储备库'
      case 'requirements':
        return '需求管理'
      case 'requirement-create':
        return '新建总模块'
      case 'requirement-child-create':
        return parentRequirementNo ? `新建子模块 ${parentRequirementNo}` : '新建子模块'
      case 'requirement-edit':
        return requirementNo ? `编辑需求 ${requirementNo}` : '需求编辑'
      case 'requirement-detail':
        return requirementNo ? `需求 ${requirementNo}` : '需求详情'
      case 'inspection-detail':
        return requirementNo ? `检查 ${requirementNo}` : '需求检查'
      case 'agents':
        return 'Agent 管理'
      case 'agent-create':
        return '新建 Agent'
      case 'agent-edit':
        return agentCode ? `编辑 Agent ${agentCode}` : 'Agent 编辑'
      case 'agent-tasks':
        return agentCode ? `任务 ${agentCode}` : 'Agent 待办'
      case 'skills':
        return '技能管理'
      case 'clients':
        return '在线客户端'
      case 'image-settings':
        return '生图接口'
      case 'image-prompts':
        return '提示词模板'
      case 'image-records':
        return '生图记录'
      case 'defects':
        return '缺陷源配置'
      case 'defect-records':
        return '缺陷列表'
      case 'defect-record-detail':
        return externalDefectId ? `缺陷详情 ${externalDefectId}` : '缺陷详情'
      case 'defect-record-analysis':
        return externalDefectId ? `AI处理 ${externalDefectId}` : '缺陷 AI 处理'
      case 'ai-analysis-logs':
        return 'AI分析记录'
      case 'mcp-test-tool':
        return 'MCP协议测试'
      case 'system-settings':
        return '系统设置'
      default:
        return '仪表盘'
    }
  }

  function textParam(value: unknown) {
    if (Array.isArray(value)) {
      return value[0] || ''
    }
    return typeof value === 'string' ? value : ''
  }

  function buildNotification(event: ConsolePushEvent): ConsoleNotification | null {
    const data = asRecord(event.data)
    if (event.type === 'CLIENT_STATUS_CHANGED') {
      const status = readText(data, 'status')
      const clientCode = readText(data, 'clientCode') || '-'
      const clientName = readText(data, 'clientName')
      return {
        id: event.eventId || `${event.type}-${Date.now()}-${Math.random()}`,
        type: event.type,
        title: status === 'ONLINE' ? '客户端上线' : status === 'OFFLINE' ? '客户端离线' : '客户端状态变化',
        message: `${clientName || clientCode}（${clientCode}）状态：${status || '-'}`,
        tone: status === 'ONLINE' ? 'success' : status === 'OFFLINE' ? 'warning' : 'info',
        createdAt: event.createdAt || new Date().toISOString(),
      }
    }
    if (event.type === 'REQUIREMENT_TASK_CHANGED') {
      const action = readText(data, 'action')
      if (action !== 'STARTED' && action !== 'FINISHED') {
        return null
      }
      const requirementNo = readText(data, 'requirementNo') || '-'
      const agentCode = readText(data, 'agentCode')
      const titleMap: Record<string, string> = {
        STARTED: '子任务开始执行',
        FINISHED: '子任务执行完成',
      }
      return {
        id: event.eventId || `${event.type}-${Date.now()}-${Math.random()}`,
        type: event.type,
        title: titleMap[action || ''] || '需求任务变化',
        message: [
          `子任务：${requirementNo}`,
          agentCode ? `Agent：${agentCode}` : '',
        ].filter(Boolean).join('，'),
        tone: action === 'STARTED'
          ? 'success'
          : 'info',
        createdAt: event.createdAt || new Date().toISOString(),
      }
    }
    return {
      id: event.eventId || `${event.type}-${Date.now()}-${Math.random()}`,
      type: event.type,
      title: '系统推送',
      message: event.type,
      tone: 'info',
      createdAt: event.createdAt || new Date().toISOString(),
    }
  }

  function asRecord(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
  }

  function readText(record: Record<string, unknown>, key: string) {
    const value = record[key]
    if (typeof value === 'string') {
      return value.trim()
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
      return String(value)
    }
    return ''
  }

  return {
    title,
    pageTitle,
    fullTitle,
    tabs,
    activeTabKey,
    consolePushStatus,
    consoleNotifications,
    consoleToasts,
    setPageTitle,
    syncRouteTab,
    openTab,
    closeTab,
    closeActiveTabAndOpen,
    getRouteCacheKey,
    setConsolePushStatus,
    recordConsolePush,
  }
})
