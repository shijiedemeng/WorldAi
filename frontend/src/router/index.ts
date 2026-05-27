import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'
import { useAppStore } from '@/stores/app'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
        { path: 'projects', name: 'projects', component: () => import('@/views/project/ProjectListView.vue') },
        { path: 'projects/create', name: 'project-create', component: () => import('@/views/project/ProjectCreateView.vue') },
        { path: 'projects/:projectCode/edit', name: 'project-edit', component: () => import('@/views/project/ProjectCreateView.vue') },
        { path: 'projects/:projectCode/markdowns', name: 'project-markdown-config', component: () => import('@/views/project/ProjectMarkdownConfigView.vue'), props: true },
        { path: 'project-knowledge', name: 'project-knowledge', component: () => import('@/views/project/ProjectKnowledgeReserveView.vue') },
        { path: 'markdown-documents', name: 'markdown-documents', component: () => import('@/views/markdown/MarkdownDocumentLibraryView.vue') },
        { path: 'requirements', name: 'requirements', component: () => import('@/views/requirement/RequirementListView.vue') },
        { path: 'requirements/create', name: 'requirement-create', component: () => import('@/views/requirement/RequirementCreateView.vue') },
        { path: 'requirements/:parentRequirementNo/children/create', name: 'requirement-child-create', component: () => import('@/views/requirement/RequirementCreateView.vue'), props: true },
        { path: 'requirements/:requirementNo/edit', name: 'requirement-edit', component: () => import('@/views/requirement/RequirementCreateView.vue'), props: true },
        { path: 'requirements/:requirementNo', name: 'requirement-detail', component: () => import('@/views/requirement/RequirementDetailView.vue'), props: true },
        { path: 'inspection/:requirementNo', name: 'inspection-detail', component: () => import('@/views/inspection/RequirementInspectionView.vue'), props: true },
        { path: 'agents', name: 'agents', component: () => import('@/views/agent/AgentListView.vue') },
        { path: 'agents/create', name: 'agent-create', component: () => import('@/views/agent/AgentCreateView.vue') },
        { path: 'agents/:agentCode/edit', name: 'agent-edit', component: () => import('@/views/agent/AgentCreateView.vue') },
        { path: 'agents/:agentCode/tasks', name: 'agent-tasks', component: () => import('@/views/agent/AgentTasksView.vue'), props: true },
        { path: 'skills', name: 'skills', component: () => import('@/views/skill/SkillListView.vue') },
        { path: 'clients', name: 'clients', component: () => import('@/views/client/ClientNodeListView.vue') },
        { path: 'image-settings', name: 'image-settings', component: () => import('@/views/image/ImageApiSettingView.vue') },
        { path: 'image-prompts', name: 'image-prompts', component: () => import('@/views/image/ImagePromptTemplateView.vue') },
        { path: 'image-records', name: 'image-records', component: () => import('@/views/image/ImageGenerationRecordView.vue') },
        { path: 'defects', name: 'defects', component: () => import('@/views/defect/DefectSyncView.vue') },
        { path: 'defects/records', name: 'defect-records', component: () => import('@/views/defect/DefectRecordListView.vue') },
        {
          path: 'defects/records/:sourceCode/:externalDefectId',
          name: 'defect-record-detail',
          component: () => import('@/views/defect/DefectRecordDetailView.vue'),
          props: true,
        },
        {
          path: 'defects/records/:sourceCode/:externalDefectId/analysis',
          name: 'defect-record-analysis',
          component: () => import('@/views/defect/DefectRecordAnalysisView.vue'),
          props: true,
        },
        { path: 'logs/ai-analysis', name: 'ai-analysis-logs', component: () => import('@/views/log/AiAnalysisLogView.vue') },
        { path: 'system-settings', name: 'system-settings', component: () => import('@/views/system/SystemSettingsView.vue') },
        { path: 'system-tools/mcp-test', name: 'mcp-test-tool', component: () => import('@/views/system/McpTestToolView.vue') },
      ],
    },
  ],
})

router.afterEach((to) => {
  useAppStore().syncRouteTab(to)
})

export default router
