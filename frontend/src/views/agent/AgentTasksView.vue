<template>
  <div class="admin-page">
    <PageHeader :title="`Agent 任务 - ${agentCode}`" />
    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>任务数据</strong>
          <span>共 {{ tasks.length }} 条执行任务</span>
        </div>
      </div>
      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="requirementNo" label="需求编号" width="160" />
        <el-table-column prop="linkType" label="环节类型" width="100" />
        <el-table-column prop="taskTitle" label="任务标题" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column prop="finishedAt" label="完成时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { fetchAgentTasks } from '@/api/agent'
import type { RequirementLink } from '@/types/link'

const props = defineProps<{ agentCode: string }>()
const tasks = ref<RequirementLink[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    tasks.value = await fetchAgentTasks(props.agentCode)
  } finally {
    loading.value = false
  }
})
</script>
