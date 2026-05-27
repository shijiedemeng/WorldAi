<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>Inspection 摘要</span>
        <slot name="actions" />
      </div>
    </template>
    <div v-if="inspection" class="inspection-summary">
      <div class="inspection-summary__block">
        <strong>已存在链路：</strong>
        <el-tag v-for="item in inspection.existingLinks" :key="item" class="tag-gap">{{ item }}</el-tag>
      </div>
      <div class="inspection-summary__block">
        <strong>缺失项：</strong>
        <el-tag v-if="!inspection.missingItems.length" type="success">无</el-tag>
        <el-tag v-for="item in inspection.missingItems" :key="item" type="danger" class="tag-gap">{{ item }}</el-tag>
      </div>
      <div class="inspection-summary__block">
        <strong>未完成链路：</strong>
        <span>{{ inspection.incompleteLinks.length }}</span>
      </div>
      <div class="inspection-summary__block">
        <strong>阻塞链路：</strong>
        <span>{{ inspection.blockedLinks.length }}</span>
      </div>
    </div>
    <EmptyBlock v-else description="暂无 inspection 数据" />
  </el-card>
</template>

<script setup lang="ts">
import EmptyBlock from '@/components/common/EmptyBlock.vue'
import type { RequirementInspection } from '@/types/inspection'

defineProps<{
  inspection?: RequirementInspection | null
}>()
</script>
