<template>
  <div class="side-menu">
    <el-scrollbar class="side-menu__scroll">
      <div class="side-menu__logo">
        <span class="side-menu__logo-mark">A</span>
        <div>
          <strong>世界梦API</strong>
          <p>协同控制台</p>
        </div>
      </div>
      <el-menu
        :default-active="activePath"
        router
        class="side-menu__nav"
        background-color="transparent"
        text-color="#334155"
        active-text-color="#155bd4"
        :default-openeds="defaultOpeneds"
      >
        <el-sub-menu index="workspace">
          <template #title>工作台</template>
          <el-menu-item index="/dashboard">仪表盘</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="collaboration">
          <template #title>项目协作</template>
          <el-menu-item index="/projects">项目管理</el-menu-item>
          <el-menu-item index="/project-knowledge">项目储备库</el-menu-item>
          <el-menu-item index="/markdown-documents">文档库</el-menu-item>
          <el-menu-item index="/requirements">需求管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="agent-dispatch">
          <template #title>Agent 调度</template>
          <el-menu-item index="/agents">Agent 管理</el-menu-item>
          <el-menu-item index="/skills">技能管理</el-menu-item>
          <el-menu-item index="/clients">在线客户端</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="ai-image">
          <template #title>AI 生图</template>
          <el-menu-item index="/image-settings">接口配置</el-menu-item>
          <el-menu-item index="/image-prompts">提示词模板</el-menu-item>
          <el-menu-item index="/image-records">生图记录</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="operations">
          <template #title>运维配置</template>
          <el-sub-menu index="defect-management">
            <template #title>缺陷管理</template>
            <el-menu-item index="/defects">缺陷源配置</el-menu-item>
            <el-menu-item index="/defects/records">缺陷列表</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="log-management">
            <template #title>日志管理</template>
            <el-menu-item index="/logs/ai-analysis">AI分析记录</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="system-tools">
            <template #title>系统工具</template>
            <el-menu-item index="/system-tools/mcp-test">MCP协议测试</el-menu-item>
            <el-menu-item index="/system-settings">系统设置</el-menu-item>
          </el-sub-menu>
        </el-sub-menu>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const defaultOpeneds: string[] = []
const activePath = computed(() => {
  const path = route.path
  if (path.startsWith('/projects')) {
    return '/projects'
  }
  if (path.startsWith('/project-knowledge')) {
    return '/project-knowledge'
  }
  if (path.startsWith('/markdown-documents')) {
    return '/markdown-documents'
  }
  if (path.startsWith('/requirements') || path.startsWith('/inspection')) {
    return '/requirements'
  }
  if (path.startsWith('/agents')) {
    return '/agents'
  }
  if (path.startsWith('/skills')) {
    return '/skills'
  }
  if (path.startsWith('/clients')) {
    return '/clients'
  }
  if (path.startsWith('/image-settings')) {
    return '/image-settings'
  }
  if (path.startsWith('/image-prompts')) {
    return '/image-prompts'
  }
  if (path.startsWith('/image-records')) {
    return '/image-records'
  }
  if (path.startsWith('/defects/records')) {
    return '/defects/records'
  }
  if (path.startsWith('/defects')) {
    return '/defects'
  }
  if (path.startsWith('/logs/ai-analysis')) {
    return '/logs/ai-analysis'
  }
  if (path.startsWith('/system-settings')) {
    return '/system-settings'
  }
  if (path.startsWith('/system-tools/mcp-test')) {
    return '/system-tools/mcp-test'
  }
  return '/dashboard'
})
</script>

<style scoped>
.side-menu__scroll {
  height: 100vh;
}
</style>
