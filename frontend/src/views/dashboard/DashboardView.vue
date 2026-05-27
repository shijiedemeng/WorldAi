<template>
  <div class="admin-page dashboard-page">
    <PageHeader title="仪表盘" />
    <el-card class="content-card dashboard-page__overview" shadow="never">
      <template #header>
        <div class="card-header">
          <span>运营概览</span>
          <el-tag type="info">实时汇总</el-tag>
        </div>
      </template>
      <el-row :gutter="24">
        <el-col :md="8" :xs="24">
          <div class="dashboard-page__metric">
            <span>项目数</span>
            <strong>{{ stats.projects }}</strong>
            <small>当前维护项目总数</small>
          </div>
        </el-col>
        <el-col :md="8" :xs="24">
          <div class="dashboard-page__metric">
            <span>需求数</span>
            <strong>{{ stats.requirements }}</strong>
            <small>总需求与子任务汇总</small>
          </div>
        </el-col>
        <el-col :md="8" :xs="24">
          <div class="dashboard-page__metric">
            <span>Agent 数</span>
            <strong>{{ stats.agents }}</strong>
            <small>已登记执行 Agent</small>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card class="content-card dashboard-page__events" shadow="never">
      <template #header>
        <div class="card-header">
          <span>实时推送历史</span>
          <div class="dashboard-events__header-tags">
            <el-tag :type="pushStatusTagType">{{ pushStatusText }}</el-tag>
          </div>
        </div>
      </template>
      <div v-if="notificationRows.length" class="dashboard-events">
        <div class="dashboard-events__track">
          <article v-for="item in notificationRows" :key="item.id" class="dashboard-event" :class="`is-${item.tone}`">
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.message }}</span>
            </div>
            <time>{{ formatTime(item.createdAt) }}</time>
          </article>
        </div>
      </div>
      <div v-else class="dashboard-events__empty">暂无实时推送。页面刷新后历史会清空。</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { fetchProjects } from '@/api/project'
import { fetchRequirements } from '@/api/requirement'
import { fetchAgents } from '@/api/agent'
import { useAppStore } from '@/stores/app'

const stats = ref({ projects: 0, requirements: 0, agents: 0 })
const appStore = useAppStore()
const notificationRows = computed(() => appStore.consoleNotifications.slice(0, 10))
const pushStatusText = computed(() => {
  const map = {
    CONNECTING: '推送连接中',
    CONNECTED: '推送已连接',
    DISCONNECTED: '推送已断开',
  }
  return map[appStore.consolePushStatus]
})
const pushStatusTagType = computed(() => {
  if (appStore.consolePushStatus === 'CONNECTED') {
    return 'success'
  }
  if (appStore.consolePushStatus === 'CONNECTING') {
    return 'warning'
  }
  return 'danger'
})

onMounted(async () => {
  const [projects, requirements, agents] = await Promise.all([
    fetchProjects().catch(() => []),
    fetchRequirements().catch(() => []),
    fetchAgents().catch(() => []),
  ])
  stats.value = {
    projects: projects.length,
    requirements: requirements.length,
    agents: agents.length,
  }
})

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.dashboard-page__metric {
  display: grid;
  gap: 8px;
  min-height: 136px;
  padding: 22px;
  border: 1px solid #e5eaf3;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.dashboard-page__metric span {
  color: #64748b;
  font-weight: 600;
}

.dashboard-page__metric strong {
  color: #0f172a;
  font-size: 36px;
  line-height: 1;
}

.dashboard-page__metric small {
  color: #94a3b8;
}

.dashboard-page__events {
  margin-top: 18px;
  max-width: min(50%, 720px);
}

.dashboard-events__header-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard-events {
  max-height: 360px;
  overflow-y: auto;
  padding-right: 6px;
}

.dashboard-events__track {
  display: grid;
  gap: 10px;
}

.dashboard-event {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 14px;
  border: 1px solid #e5eaf3;
  border-left: 4px solid #2563eb;
  border-radius: 14px;
  background: #ffffff;
}

.dashboard-event.is-success {
  border-left-color: #16a34a;
}

.dashboard-event.is-warning {
  border-left-color: #f59e0b;
}

.dashboard-event.is-danger {
  border-left-color: #dc2626;
}

.dashboard-event div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.dashboard-event strong {
  color: #0f172a;
  font-size: 14px;
}

.dashboard-event span {
  color: #64748b;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.dashboard-event time {
  color: #94a3b8;
  font-size: 12px;
  white-space: nowrap;
}

.dashboard-events__empty {
  display: grid;
  min-height: 120px;
  place-items: center;
  color: #94a3b8;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
}

@media (max-width: 960px) {
  .dashboard-page__events {
    max-width: 100%;
  }
}
</style>
