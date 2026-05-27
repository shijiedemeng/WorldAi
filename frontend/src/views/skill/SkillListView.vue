<template>
  <div class="admin-page skill-page">
    <PageHeader title="技能管理">
      <el-button :loading="loading" @click="loadList()">刷新</el-button>
      <el-button type="primary" @click="openCreate">上传技能包</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" :model="filters" label-position="top">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="技能编码 / 名称 / 描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.enabledFlag" clearable placeholder="全部状态" class="w-full">
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never" v-loading="loading || uploading">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>技能列表</strong>
          <span>共 {{ total }} 条技能记录</span>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="skillCode" label="编码" min-width="180" />
        <el-table-column prop="skillName" label="名称" min-width="160" />
        <el-table-column prop="enabledFlag" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabledFlag ? 'success' : 'info'" size="small">{{ row.enabledFlag ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hasArchive" label="压缩包" width="100">
          <template #default="{ row }">
            <el-tag :type="row.hasArchive ? 'success' : 'warning'" size="small">{{ row.hasArchive ? '已上传' : '未上传' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="archiveFileName" label="包文件" min-width="180" show-overflow-tooltip />
        <el-table-column prop="skillDesc" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openContent(row)">查看</el-button>
            <el-button link type="primary" @click="pickArchive(row)">替换包</el-button>
            <el-button link type="primary" :disabled="!row.hasArchive" @click="downloadArchive(row)">下载</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <input ref="archiveInput" type="file" accept=".zip,application/zip,application/x-zip-compressed" class="hidden-file-input" @change="handleArchiveSelected" />
    <input ref="packageInput" type="file" accept=".zip,application/zip,application/x-zip-compressed" class="hidden-file-input" @change="handlePackageSelected" />

    <el-drawer v-model="editorVisible" :title="editorTitle" size="68%" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <el-form-item label="技能编码" required>
          <el-input v-model="form.skillCode" :disabled="isEdit" placeholder="例如 ai-api-agent-integration" />
        </el-form-item>
        <el-form-item label="技能名称" required>
          <el-input v-model="form.skillName" placeholder="例如 AI API Agent Integration" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.skillDesc" type="textarea" :rows="3" placeholder="填写技能用途、适用范围或目录约定" />
        </el-form-item>
        <el-form-item :label="isEdit ? '替换技能包' : '技能包'" :required="!isEdit">
          <div class="package-picker">
            <el-button @click="pickPackage">{{ packageFile ? '重新选择压缩包' : '选择压缩包' }}</el-button>
            <span class="package-picker__name">{{ packageFile?.name || packageFileHint }}</span>
          </div>
          <div class="field-hint">支持 .zip。服务端会自动解压读取包内 SKILL.md / skill.md 作为技能主体内容；重新上传会覆盖。</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabledFlag" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="contentVisible" :title="contentTitle" width="880px" destroy-on-close>
      <pre class="skill-content">{{ contentDetailText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createSkillPackage,
  deleteSkill,
  fetchSkill,
  fetchSkillPage,
  updateSkill,
  uploadSkillArchive,
} from '@/api/skill'
import type { SaveSkillPayload, Skill } from '@/types/skill'

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const editorVisible = ref(false)
const contentVisible = ref(false)
const archiveInput = ref<HTMLInputElement | null>(null)
const packageInput = ref<HTMLInputElement | null>(null)
const packageFile = ref<File | null>(null)
const list = ref<Skill[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const selectedSkill = ref<Skill | null>(null)
const pendingArchiveSkill = ref<Skill | null>(null)
const filters = ref({ keyword: '', enabledFlag: '' as boolean | '' })
const isEdit = computed(() => Boolean(selectedSkill.value))
const editorTitle = computed(() => (isEdit.value ? `编辑技能 - ${selectedSkill.value?.skillCode}` : '上传技能包'))
const contentTitle = computed(() => (selectedSkill.value ? `SKILL.md - ${selectedSkill.value.skillCode}` : 'SKILL.md'))
const form = ref<SaveSkillPayload>({
  skillCode: '',
  skillName: '',
  skillDesc: '',
  enabledFlag: true,
})

const packageFileHint = computed(() => {
  if (!isEdit.value) {
    return '未选择文件'
  }
  return selectedSkill.value?.archiveFileName ? `当前包：${selectedSkill.value.archiveFileName}` : '当前未上传包'
})

const contentDetailText = computed(() => {
  const skill = selectedSkill.value
  if (!skill) {
    return '暂无内容'
  }
  return skill.contentText || '暂无 SKILL.md 内容'
})

onMounted(async () => {
  await loadList()
})

async function loadList(showLoading = true) {
  if (showLoading) {
    loading.value = true
  }
  try {
    const result = await fetchSkillPage({
      page: page.value,
      pageSize: pageSize.value,
      keyword: filters.value.keyword.trim() || undefined,
      enabledFlag: filters.value.enabledFlag === '' ? undefined : filters.value.enabledFlag,
    })
    list.value = result.items
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } finally {
    if (showLoading) {
      loading.value = false
    }
  }
}

function openCreate() {
  selectedSkill.value = null
  packageFile.value = null
  form.value = {
    skillCode: '',
    skillName: '',
    skillDesc: '',
    enabledFlag: true,
  }
  editorVisible.value = true
}

function openEdit(row: Skill) {
  selectedSkill.value = row
  packageFile.value = null
  form.value = {
    skillCode: row.skillCode,
    skillName: row.skillName,
    skillDesc: row.skillDesc || '',
    enabledFlag: row.enabledFlag,
  }
  editorVisible.value = true
}

async function openContent(row: Skill) {
  selectedSkill.value = row
  contentVisible.value = true
  try {
    selectedSkill.value = await fetchSkill(row.skillCode)
  } catch {
    // Keep row data visible if detail refresh fails.
  }
}

async function handleSubmit() {
  const skillCode = (form.value.skillCode || '').trim()
  const skillName = form.value.skillName.trim()
  const currentPackageFile = packageFile.value
  if (!skillName) {
    ElMessage.warning('请输入技能名称')
    return
  }
  if (!isEdit.value && !skillCode) {
    ElMessage.warning('请输入技能编码')
    return
  }
  if (!isEdit.value && !currentPackageFile) {
    ElMessage.warning('请选择技能压缩包')
    return
  }
  saving.value = true
  try {
    const editing = isEdit.value
    if (isEdit.value && selectedSkill.value) {
      await updateSkill(selectedSkill.value.skillCode, {
        skillCode,
        skillName,
        skillDesc: form.value.skillDesc?.trim(),
        enabledFlag: form.value.enabledFlag,
      })
      if (packageFile.value) {
        await uploadSkillArchive(selectedSkill.value.skillCode, packageFile.value)
      }
      ElMessage.success('更新成功')
    } else {
      const formData = new FormData()
      const packageFileForCreate = currentPackageFile as File
      formData.append('skillCode', skillCode)
      formData.append('skillName', skillName)
      if (form.value.skillDesc?.trim()) {
        formData.append('skillDesc', form.value.skillDesc.trim())
      }
      formData.append('enabledFlag', String(form.value.enabledFlag))
      formData.append('file', packageFileForCreate, packageFileForCreate.name)
      await createSkillPackage(formData)
      ElMessage.success('创建成功')
    }
    editorVisible.value = false
    packageFile.value = null
    if (!editing) {
      page.value = 1
    }
    await loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Skill) {
  try {
    await ElMessageBox.confirm(`确定删除技能 ${row.skillName} (${row.skillCode}) 吗？`, '删除技能', {
      type: 'warning',
    })
    await deleteSkill(row.skillCode)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
  }
}

function pickArchive(row: Skill) {
  pendingArchiveSkill.value = row
  archiveInput.value?.click()
}

function pickPackage() {
  packageInput.value?.click()
}

async function handleArchiveSelected(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file || !pendingArchiveSkill.value) {
    return
  }
  uploading.value = true
  try {
    const updated = await uploadSkillArchive(pendingArchiveSkill.value.skillCode, file)
    replaceSkillInList(updated)
    if (selectedSkill.value?.skillCode === updated.skillCode) {
      selectedSkill.value = updated
    }
    ElMessage.success('压缩包上传成功，已解析 SKILL.md')
    await loadList(false)
  } finally {
    uploading.value = false
    pendingArchiveSkill.value = null
  }
}

function replaceSkillInList(skill: Skill) {
  const index = list.value.findIndex((item) => item.skillCode === skill.skillCode)
  if (index >= 0) {
    list.value.splice(index, 1, skill)
  }
}

function handlePackageSelected(event: Event) {
  const target = event.target as HTMLInputElement
  packageFile.value = target.files?.[0] || null
  target.value = ''
}

function downloadArchive(row: Skill) {
  window.open(`/api/skills/${encodeURIComponent(row.skillCode)}/archive`, '_blank')
}

function resetFilters() {
  filters.value = { keyword: '', enabledFlag: '' }
  page.value = 1
  void loadList()
}

function handleSearch() {
  page.value = 1
  void loadList()
}

function handlePageChange() {
  void loadList()
}

function handlePageSizeChange() {
  page.value = 1
  void loadList()
}

</script>

<style scoped>
.skill-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 70vh;
  overflow: auto;
}

.hidden-file-input {
  display: none;
}

.package-picker {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.package-picker__name,
.field-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.field-hint {
  margin-top: 6px;
  line-height: 1.4;
}
</style>
