<script setup lang="ts">
import { computed, ref } from 'vue'
import { fetchHealth, fetchInfo, fetchMemory, fetchSandboxAllowlist, fetchTools } from '@/api/oryxos'
import type { ProfileSummary } from '@/api/types'
import type { BootStatus } from '@/state/useConsole'
import AgentManagement from './AgentManagement.vue'
import ManagementQuery from './ManagementQuery.vue'
import ProviderManagement from './ProviderManagement.vue'
import { consoleSections, type ConsoleSection } from './consoleSections'

const props = defineProps<{
  section: Exclude<ConsoleSection, 'sessions'>
  profiles: ProfileSummary[]
  bootStatus: BootStatus
}>()
defineEmits<{ chat: [name: string]; refreshProfiles: [] }>()

const toolAgent = ref('')
const memoryAgent = ref('')
const selectedMemory = computed(() =>
  props.profiles.some(profile => profile.name === memoryAgent.value)
    ? memoryAgent.value : props.profiles[0]?.name ?? '',
)
const loadTools = computed(() => fetchTools.bind(null, toolAgent.value))
const loadMemory = computed(() => fetchMemory.bind(null, selectedMemory.value))
const title = computed(() => consoleSections.find(item => item.key === props.section)?.label)

async function loadRuntime() {
  const [health, info] = await Promise.all([fetchHealth(), fetchInfo()])
  return { health, info }
}
</script>

<template>
  <main class="management-panel">
    <h1>{{ title }}</h1>
    <template v-if="section === 'tools' || section === 'memory'">
      <p v-if="bootStatus === 'loading'" role="status">正在加载 Agent 列表…</p>
      <div v-else-if="bootStatus === 'offline'" role="alert">
        <p>Agent 列表加载失败。</p>
        <button type="button" class="ghost-button" @click="$emit('refreshProfiles')">重试 Agent 列表</button>
      </div>
    </template>
    <AgentManagement v-if="section === 'agents'" @chat="$emit('chat', $event)" />
    <ProviderManagement v-else-if="section === 'providers'" />

    <template v-else-if="section === 'tools'">
      <label>Agent
        <select v-model="toolAgent">
          <option value="">全局工具</option>
          <option v-for="profile in profiles" :key="profile.name" :value="profile.name">{{ profile.name }}</option>
        </select>
      </label>
      <ManagementQuery :load="loadTools" v-slot="{ data }">
        <p v-if="!data.length">没有可显示的工具。</p>
        <article v-for="tool in data" :key="tool.name" class="management-card">
          <h2>{{ tool.name }}</h2>
          <p>{{ tool.description }}</p>
          <p>{{ tool.source }} · {{ tool.sourceName }}</p>
        </article>
      </ManagementQuery>
    </template>

    <ManagementQuery v-else-if="section === 'sandbox'" :load="fetchSandboxAllowlist" v-slot="{ data }">
      <dl>
        <dt>HTTP 域名</dt><dd>{{ data.httpDomains.join('、') || '无' }}</dd>
        <dt>全局文件根数量</dt><dd>{{ data.fileRootCount }}</dd>
        <dt>命令别名</dt><dd>{{ data.commandAliases.join('、') || '无' }}</dd>
      </dl>
    </ManagementQuery>

    <template v-else-if="section === 'memory'">
      <p v-if="bootStatus === 'empty'">工作区没有可查询记忆的 Agent。</p>
      <template v-else-if="selectedMemory">
        <label>Agent
          <select :value="selectedMemory" @change="memoryAgent = ($event.target as HTMLSelectElement).value">
            <option v-for="profile in profiles" :key="profile.name" :value="profile.name">{{ profile.name }}</option>
          </select>
        </label>
        <ManagementQuery :load="loadMemory" v-slot="{ data }">
          <p>记忆后端：{{ data.backend }}</p>
          <section v-for="partition in [{ label: '核心记忆', entries: data.core }, { label: '归档记忆', entries: data.archival }]"
            :key="partition.label" class="management-card">
            <h2>{{ partition.label }}</h2>
            <p v-if="!partition.entries.length">暂无记录。</p>
            <ul v-else><li v-for="entry in partition.entries" :key="entry.id">{{ entry.content }}</li></ul>
          </section>
          <p v-if="data.archivalTruncated">归档结果已截断。</p>
        </ManagementQuery>
      </template>
    </template>

    <ManagementQuery v-else :load="loadRuntime" v-slot="{ data }">
      <dl>
        <dt>服务健康</dt><dd>{{ data.health.status }}</dd>
        <dt>应用版本</dt><dd>{{ data.info.implementationVersion }}</dd>
        <dt>Java 版本</dt><dd>{{ data.info.javaVersion }}</dd>
        <dt>Agent 数量</dt><dd>{{ data.info.profileCount }}</dd>
        <dt>Tool 数量</dt><dd>{{ data.info.toolCount }}</dd>
      </dl>
    </ManagementQuery>
  </main>
</template>
