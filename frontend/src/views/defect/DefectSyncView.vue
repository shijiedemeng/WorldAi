<template>
  <div class="admin-page defect-source-page">
    <PageHeader
      title="缺陷同步"
    >
      <el-button type="primary" @click="openSourceDialog()">新增缺陷源</el-button>
    </PageHeader>

    <el-card class="filter-card" shadow="never">
      <el-form class="filter-form" label-position="top">
        <el-form-item label="本系统项目">
          <el-select v-model="filterProjectCode" clearable filterable placeholder="按项目过滤" class="w-full" @change="handleFilterProjectChange">
            <el-option v-for="project in projects" :key="project.projectCode" :label="project.projectName" :value="project.projectCode" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-form__actions">
          <el-button @click="resetSourceFilters">重置</el-button>
          <el-button type="primary" @click="handleFilterProjectChange">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div class="table-toolbar__title">
          <strong>缺陷源列表</strong>
          <span>共 {{ sourceConfigs.length }} 条缺陷源配置</span>
        </div>
      </div>

      <el-table :data="pagedSourceConfigs" stripe>
        <el-table-column prop="sourceCode" label="来源编码" width="170" />
        <el-table-column prop="sourceName" label="来源名称" width="180" />
        <el-table-column label="平台" width="110">
          <template #default="{ row }">
            {{ defectPlatformLabelMap[row.platformType] || row.platformType }}
          </template>
        </el-table-column>
        <el-table-column prop="projectCode" label="项目编号" width="140" />
        <el-table-column prop="externalProjectName" label="平台项目" min-width="180" />
        <el-table-column prop="username" label="账号" width="140" />
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.enabledFlag ? 'ENABLED' : 'DISABLED'" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSourceDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="handleViewDefects(row)">
              查看缺陷
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="sourcePage"
          v-model:page-size="sourcePageSize"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="sourceConfigs.length"
        />
      </div>
    </el-card>

    <el-dialog v-model="sourceDialogVisible" :title="sourceDialogTitle" width="860px">
      <el-form :model="sourceForm" label-width="110px">
        <el-row :gutter="16">
          <el-col :md="12" :xs="24">
            <el-form-item label="来源编码" required>
              <el-input v-model="sourceForm.sourceCode" :placeholder="sourceCodePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="来源名称" required>
              <el-input v-model="sourceForm.sourceName" :placeholder="sourceNamePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="平台" required>
              <el-select v-model="sourceForm.platformType" class="w-full">
                <el-option v-for="item in defectPlatformOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="本系统项目" required>
              <el-select v-model="sourceForm.projectCode" filterable class="w-full">
                <el-option v-for="project in projects" :key="project.projectCode" :label="project.projectName" :value="project.projectCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="服务地址" required>
              <el-input v-model="sourceForm.baseUrl" :placeholder="baseUrlPlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item :label="externalProjectKeyLabel" required>
              <template v-if="isYunxiaoSource">
                <div class="defect-source-page__yunxiao-project-picker">
                  <el-select
                    v-model="sourceForm.externalProjectKey"
                    filterable
                    clearable
                    class="w-full"
                    :loading="loadingYunxiaoProjects"
                    :placeholder="externalProjectKeyPlaceholder"
                    @change="handleSelectYunxiaoProject"
                  >
                    <el-option
                      v-for="project in yunxiaoProjects"
                      :key="project.id"
                      :label="formatYunxiaoProjectLabel(project)"
                      :value="project.id"
                    />
                  </el-select>
                  <el-button :loading="loadingYunxiaoProjects" @click="loadYunxiaoProjects(true)">
                    刷新项目列表
                  </el-button>
                </div>
                <div class="defect-source-page__form-hint">
                  选择组织后会自动加载项目列表，选中后自动回填项目名称。
                </div>
              </template>
              <el-input v-else v-model="sourceForm.externalProjectKey" :placeholder="externalProjectKeyPlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="平台项目名">
              <el-input
                v-model="sourceForm.externalProjectName"
                :placeholder="isYunxiaoSource ? '选择项目后自动回填' : '平台项目名，可手动修改'"
                :readonly="isYunxiaoSource"
              />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item :label="usernameLabel">
              <el-input v-model="sourceForm.username" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="密码">
              <el-input v-model="sourceForm.passwordValue" type="password" show-password />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item :label="accessTokenLabel">
              <el-input v-model="sourceForm.accessToken" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="启用">
              <el-switch v-model="sourceForm.enabledFlag" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="扩展配置">
              <el-input v-model="sourceForm.extraConfig" type="textarea" :rows="4" :placeholder="extraConfigPlaceholder" />
            </el-form-item>
          </el-col>
          <el-col v-if="isYunxiaoSource" :span="24">
            <el-form-item label="云效组织">
              <div class="defect-source-page__organization-select">
                <el-select v-model="yunxiaoOrganizationId" filterable clearable placeholder="先获取组织，再选择组织 ID" class="w-full" @change="handleSelectYunxiaoOrganization">
                  <el-option v-for="organization in yunxiaoOrganizations" :key="organization.id" :label="formatYunxiaoOrganizationLabel(organization)" :value="organization.id" />
                </el-select>
                <el-button :loading="loadingYunxiaoOrganizations" @click="handleFetchYunxiaoOrganizations">获取组织</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col v-if="isYunxiaoSource" :span="24">
            <el-form-item label="云效项目">
              <div class="defect-source-page__yunxiao-project">
                <div class="defect-source-page__yunxiao-project-status">
                  <el-tag :type="sourceForm.externalProjectKey ? 'success' : 'warning'">
                    {{ sourceForm.externalProjectKey ? `当前项目：${sourceForm.externalProjectName || sourceForm.externalProjectKey}` : '未选择项目' }}
                  </el-tag>
                  <span>组织和令牌确定后，系统会自动拉取项目列表。</span>
                </div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="sourceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSource" @click="handleSaveSource">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { fetchProjects } from '@/api/project'
import {
  fetchDefectSources,
  fetchYunxiaoOrganizations,
  fetchYunxiaoProjects,
  saveDefectSource,
} from '@/api/defect'
import type { DefectSourceConfig, SaveDefectSourceConfigPayload, YunxiaoOrganization, YunxiaoProject } from '@/types/defect'
import type { Project } from '@/types/project'
import { defectPlatformOptions } from '@/types/options'

const defectPlatformLabelMap = Object.fromEntries(defectPlatformOptions.map((item) => [item.value, item.label]))
const router = useRouter()

const sourceConfigs = ref<DefectSourceConfig[]>([])
const projects = ref<Project[]>([])
const yunxiaoOrganizations = ref<YunxiaoOrganization[]>([])
const yunxiaoProjects = ref<YunxiaoProject[]>([])
const savingSource = ref(false)
const loadingYunxiaoOrganizations = ref(false)
const loadingYunxiaoProjects = ref(false)
const filterProjectCode = ref('')
const sourceDialogVisible = ref(false)
const yunxiaoOrganizationId = ref('')
const sourcePage = ref(1)
const sourcePageSize = ref(10)

const emptySourceForm = (): SaveDefectSourceConfigPayload => ({
  sourceCode: '',
  sourceName: '',
  projectCode: '',
  platformType: 'ZENTAO',
  baseUrl: '',
  username: '',
  passwordValue: '',
  accessToken: '',
  enabledFlag: true,
  extraConfig: '',
  externalProjectKey: '',
  externalProjectName: '',
})

const sourceForm = ref<SaveDefectSourceConfigPayload>(emptySourceForm())
const sourceDialogTitle = computed(() => (sourceForm.value.sourceCode ? '编辑缺陷源' : '新增缺陷源'))
const isYunxiaoSource = computed(() => sourceForm.value.platformType === 'YUNXIAO')
const sourceCodePlaceholder = computed(() => (isYunxiaoSource.value ? 'yunxiao-prod-a' : 'zentao-prod-a'))
const sourceNamePlaceholder = computed(() => (isYunxiaoSource.value ? '云效生产A' : '禅道生产A'))
const baseUrlPlaceholder = computed(() => (isYunxiaoSource.value ? 'https://openapi-rdc.aliyuncs.com' : 'http://zentao.example.com'))
const externalProjectKeyLabel = computed(() => (isYunxiaoSource.value ? '云效项目' : '平台项目ID'))
const externalProjectKeyPlaceholder = computed(() => (isYunxiaoSource.value ? '选择组织后自动加载项目列表' : '例如产品ID'))
const usernameLabel = computed(() => (isYunxiaoSource.value ? '用户名(可空)' : '用户名'))
const accessTokenLabel = computed(() => (isYunxiaoSource.value ? '云效令牌' : '令牌'))
const extraConfigPlaceholder = computed(() => (isYunxiaoSource.value
  ? '{"organizationId":"组织ID","category":"Bug","spaceType":"Project","regionMode":false}'
  : '预留给多平台扩展'))
const pagedSourceConfigs = computed(() => sourceConfigs.value.slice((sourcePage.value - 1) * sourcePageSize.value, sourcePage.value * sourcePageSize.value))

watch(
  () => sourceForm.value.platformType,
  (platformType) => {
    if (platformType === 'YUNXIAO' && !sourceForm.value.extraConfig) {
      sourceForm.value.extraConfig = '{"organizationId":"","category":"Bug","spaceType":"Project","regionMode":false}'
    }
    yunxiaoOrganizationId.value = platformType === 'YUNXIAO' ? readYunxiaoOrganizationId(sourceForm.value.extraConfig) : ''
  },
)

watch(
  () => sourceForm.value.extraConfig,
  (extraConfig) => {
    if (isYunxiaoSource.value) {
      const previousOrganizationId = yunxiaoOrganizationId.value
      const nextOrganizationId = readYunxiaoOrganizationId(extraConfig)
      yunxiaoOrganizationId.value = nextOrganizationId
      if (nextOrganizationId !== previousOrganizationId) {
        sourceForm.value.externalProjectKey = ''
        sourceForm.value.externalProjectName = ''
      }
    }
  },
)

onMounted(async () => {
  const projectList = await fetchProjects()
  projects.value = projectList
  await loadSourceConfigs()
})

async function loadSourceConfigs() {
  sourceConfigs.value = await fetchDefectSources(filterProjectCode.value || undefined)
}

async function handleFilterProjectChange() {
  sourcePage.value = 1
  await loadSourceConfigs()
}

async function resetSourceFilters() {
  filterProjectCode.value = ''
  sourcePage.value = 1
  await loadSourceConfigs()
}

function openSourceDialog(row?: DefectSourceConfig) {
  sourceForm.value = row
    ? {
        sourceCode: row.sourceCode,
        sourceName: row.sourceName,
        projectCode: row.projectCode,
        platformType: row.platformType,
        baseUrl: row.baseUrl,
        username: row.username || '',
        passwordValue: row.passwordValue || '',
        accessToken: row.accessToken || '',
        enabledFlag: row.enabledFlag,
        extraConfig: row.extraConfig || '',
        externalProjectKey: row.externalProjectKey,
        externalProjectName: row.externalProjectName,
      }
    : emptySourceForm()
  yunxiaoOrganizationId.value = sourceForm.value.platformType === 'YUNXIAO' ? readYunxiaoOrganizationId(sourceForm.value.extraConfig) : ''
  yunxiaoProjects.value = []
  sourceDialogVisible.value = true
  if (sourceForm.value.platformType === 'YUNXIAO' && yunxiaoOrganizationId.value) {
    void loadYunxiaoProjects(false)
  }
}

function formatYunxiaoOrganizationLabel(organization: YunxiaoOrganization) {
  return organization.name ? `${organization.name} / ${organization.id}` : organization.id
}

function formatYunxiaoProjectLabel(project: YunxiaoProject) {
  return project.name ? `${project.name} / ${project.id}` : project.id
}

function readYunxiaoOrganizationId(extraConfig?: string) {
  const config = parseYunxiaoExtraConfig(extraConfig)
  return typeof config.organizationId === 'string' ? config.organizationId : ''
}

function parseYunxiaoExtraConfig(extraConfig?: string): Record<string, unknown> {
  if (!extraConfig?.trim()) {
    return { organizationId: '', category: 'Bug', spaceType: 'Project', regionMode: false }
  }
  try {
    const parsed = JSON.parse(extraConfig)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function writeYunxiaoOrganizationId(organizationId: string) {
  const config = parseYunxiaoExtraConfig(sourceForm.value.extraConfig)
  sourceForm.value.extraConfig = JSON.stringify({
    category: 'Bug',
    spaceType: 'Project',
    regionMode: false,
    ...config,
    organizationId,
  })
}

function handleSelectYunxiaoOrganization(value: string) {
  const previousOrganizationId = yunxiaoOrganizationId.value
  writeYunxiaoOrganizationId(value)
  if (value !== previousOrganizationId) {
    sourceForm.value.externalProjectKey = ''
    sourceForm.value.externalProjectName = ''
  }
  void loadYunxiaoProjects(false)
}

async function handleFetchYunxiaoOrganizations() {
  if (!sourceForm.value.baseUrl || !sourceForm.value.accessToken) {
    ElMessage.warning('请先填写云效服务地址和令牌')
    return
  }
  loadingYunxiaoOrganizations.value = true
  try {
    yunxiaoOrganizations.value = await fetchYunxiaoOrganizations({
      baseUrl: sourceForm.value.baseUrl,
      accessToken: sourceForm.value.accessToken,
      userId: sourceForm.value.username || undefined,
    })
    if (yunxiaoOrganizations.value.length === 0) {
      ElMessage.warning('未查询到云效组织')
      return
    }
    const matched = yunxiaoOrganizations.value.find((item) => item.id === yunxiaoOrganizationId.value)
    if (!matched && !yunxiaoOrganizationId.value) {
      yunxiaoOrganizationId.value = yunxiaoOrganizations.value[0].id
      writeYunxiaoOrganizationId(yunxiaoOrganizationId.value)
      sourceForm.value.externalProjectKey = ''
      sourceForm.value.externalProjectName = ''
    }
    if (yunxiaoOrganizationId.value) {
      await loadYunxiaoProjects(false)
    }
    ElMessage.success('云效组织已加载')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '云效组织加载失败')
  } finally {
    loadingYunxiaoOrganizations.value = false
  }
}

async function persistSource(closeDialog: boolean) {
  const saved = await saveDefectSource(sourceForm.value)
  sourceForm.value = {
    sourceCode: saved.sourceCode,
    sourceName: saved.sourceName,
    projectCode: saved.projectCode,
    platformType: saved.platformType,
    baseUrl: saved.baseUrl,
    username: saved.username || '',
    passwordValue: saved.passwordValue || '',
    accessToken: saved.accessToken || '',
    enabledFlag: saved.enabledFlag,
    extraConfig: saved.extraConfig || '',
    externalProjectKey: saved.externalProjectKey || '',
    externalProjectName: saved.externalProjectName || '',
  }
  yunxiaoOrganizationId.value = sourceForm.value.platformType === 'YUNXIAO' ? readYunxiaoOrganizationId(sourceForm.value.extraConfig) : ''
  await loadSourceConfigs()
  if (closeDialog) {
    sourceDialogVisible.value = false
  }
  return saved
}

async function handleSaveSource() {
  if (isYunxiaoSource.value && !sourceForm.value.externalProjectKey?.trim()) {
    ElMessage.warning('请先从项目列表中选择云效项目')
    return
  }
  savingSource.value = true
  try {
    await persistSource(true)
    ElMessage.success('缺陷源已保存')
  } finally {
    savingSource.value = false
  }
}

async function loadYunxiaoProjects(showTips = true) {
  if (!isYunxiaoSource.value) {
    return
  }
  const organizationId = yunxiaoOrganizationId.value || readYunxiaoOrganizationId(sourceForm.value.extraConfig)
  if (!sourceForm.value.baseUrl?.trim() || !sourceForm.value.accessToken?.trim()) {
    if (showTips) {
      ElMessage.warning('请先填写云效服务地址和令牌')
    }
    return
  }
  if (!organizationId) {
    if (showTips) {
      ElMessage.warning('请先获取并选择云效组织')
    }
    return
  }
  loadingYunxiaoProjects.value = true
  try {
    yunxiaoProjects.value = await fetchYunxiaoProjects({
      baseUrl: sourceForm.value.baseUrl,
      accessToken: sourceForm.value.accessToken,
      organizationId,
    })
    const matched = yunxiaoProjects.value.find((item) => item.id === sourceForm.value.externalProjectKey)
    if (matched) {
      sourceForm.value.externalProjectName = matched.name || matched.id
    }
    if (showTips) {
      ElMessage.success(yunxiaoProjects.value.length ? '云效项目列表已加载' : '未查询到云效项目')
    }
  } catch (error) {
    yunxiaoProjects.value = []
    ElMessage.error(error instanceof Error ? error.message : '云效项目列表加载失败')
  } finally {
    loadingYunxiaoProjects.value = false
  }
}

function handleSelectYunxiaoProject(value: string) {
  const matched = yunxiaoProjects.value.find((item) => item.id === value)
  sourceForm.value.externalProjectName = matched?.name || matched?.id || ''
}

function handleViewDefects(row: DefectSourceConfig) {
  router.push({
    name: 'defect-records',
    query: {
      sourceCode: row.sourceCode,
    },
  })
}
</script>

<style scoped>
.defect-source-page__project-filter {
  width: 220px;
}

.defect-source-page__organization-select {
  display: flex;
  gap: 10px;
  width: 100%;
}

.defect-source-page__yunxiao-project-picker {
  display: flex;
  gap: 10px;
  width: 100%;
}

.defect-source-page__yunxiao-project {
  display: grid;
  gap: 12px;
  width: 100%;
}

.defect-source-page__yunxiao-project-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.defect-source-page__form-hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 960px) {
  .defect-source-page__project-filter {
    width: 100%;
  }

  .defect-source-page__organization-select {
    flex-direction: column;
  }

  .defect-source-page__yunxiao-project-picker {
    flex-direction: column;
  }

}
</style>
