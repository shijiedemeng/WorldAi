<template>
  <el-form-item label="缺陷状态">
    <el-select
      v-model="statusValue"
      multiple
      clearable
      filterable
      collapse-tags
      collapse-tags-tooltip
      class="w-full"
      placeholder="可多选云效状态"
    >
      <el-option
        v-for="option in yunxiaoDefectStatusOptions"
        :key="option.value"
        :label="option.label"
        :value="option.value"
      />
    </el-select>
  </el-form-item>

  <el-form-item label="负责人">
    <el-select
      v-model="assignedToValue"
      clearable
      filterable
      class="w-full"
      :loading="loadingMembers"
      placeholder="选择负责人"
    >
      <el-option
        v-for="member in members"
        :key="member.userId"
        :label="formatMemberLabel(member)"
        :value="member.userId"
      />
    </el-select>
  </el-form-item>

  <el-form-item label="创建人">
    <el-select
      v-model="reporterNameValue"
      clearable
      filterable
      class="w-full"
      :loading="loadingMembers"
      placeholder="选择创建人"
    >
      <el-option
        v-for="member in members"
        :key="member.userId"
        :label="formatMemberLabel(member)"
        :value="member.userId"
      />
    </el-select>
  </el-form-item>

  <el-form-item label="标签">
    <el-input v-model="tagValue" clearable placeholder="按标签 ID 过滤" />
  </el-form-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { yunxiaoDefectStatusOptions } from '@/constants/yunxiaoDefect'
import type { DefectRemoteListQuery, YunxiaoProjectMember } from '@/types/defect'

const props = defineProps<{
  query: DefectRemoteListQuery
  members: YunxiaoProjectMember[]
  loadingMembers?: boolean
}>()

const statusValue = computed({
  get: () => splitCsv(props.query.status),
  set: (value: string[]) => {
    props.query.status = value.join(',')
  },
})

const assignedToValue = computed({
  get: () => props.query.assignedTo || '',
  set: (value: string) => {
    props.query.assignedTo = value
  },
})

const reporterNameValue = computed({
  get: () => props.query.reporterName || '',
  set: (value: string) => {
    props.query.reporterName = value
  },
})

const tagValue = computed({
  get: () => props.query.tag || '',
  set: (value: string) => {
    props.query.tag = value
  },
})

function formatMemberLabel(member: YunxiaoProjectMember) {
  return member.userName || '未命名用户'
}

function splitCsv(value?: string) {
  if (!value) {
    return []
  }
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}
</script>
