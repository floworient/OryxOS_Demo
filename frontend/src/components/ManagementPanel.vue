<script setup lang="ts">
import { computed, ref } from 'vue'
import { fetchHealth, fetchInfo, fetchMemory, fetchSandboxAllowlist, fetchTools } from '@/api/oryxos'
import type { ProfileSummary } from '@/api/types'
import type { BootStatus } from '@/state/useConsole'
import AgentManagement from './AgentManagement.vue'
import ManagementQuery from './ManagementQuery.vue'
import ProviderManagement from './ProviderManagement.vue'
import ScheduleManagement from './ScheduleManagement.vue'
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
    <ScheduleManagement v-else-if="section === 'schedules'" />

    <template v-else-if="section === 'tools'">
      <section class="tools-overview">
        <header class="tools-head">
          <h2>工具管理</h2>
          <label class="tools-filter">
            <span>筛选 Agent</span>
            <select v-model="toolAgent">
              <option value="">全局工具</option>
              <option v-for="profile in profiles" :key="profile.name" :value="profile.name">{{ profile.name }}</option>
            </select>
          </label>
        </header>

        <ManagementQuery :load="loadTools" v-slot="{ data }">
          <p v-if="!data.length" class="tools-empty">没有可显示的工具。</p>
          <ul v-else class="tools-grid">
            <li v-for="tool in data" :key="tool.name" class="tool-card">
              <header class="tool-head">
                <span class="tool-icon">🔧</span>
                <h3>{{ tool.name }}</h3>
              </header>
              <p class="tool-desc">{{ tool.description }}</p>
              <footer class="tool-meta">
                <span class="tool-source">{{ tool.source }}</span>
                <span class="tool-sep">·</span>
                <span class="tool-source-name">{{ tool.sourceName }}</span>
              </footer>
            </li>
          </ul>
        </ManagementQuery>
      </section>
    </template>

    <ManagementQuery v-else-if="section === 'sandbox'" :load="fetchSandboxAllowlist" v-slot="{ data }">
      <section class="sandbox-overview">
        <header class="sandbox-head">
          <h2>安全沙箱白名单</h2>
          <p class="sandbox-hint">以下配置定义了 Agent 可访问的外部资源边界</p>
        </header>

        <ul class="sandbox-categories">
          <li class="sandbox-card">
            <header class="card-head">
              <span class="card-icon">🌐</span>
              <h3>HTTP 域名</h3>
              <span class="card-count">{{ data.httpDomains.length }}</span>
            </header>
            <ul v-if="data.httpDomains.length" class="card-list">
              <li v-for="domain in data.httpDomains" :key="domain" class="card-item">{{ domain }}</li>
            </ul>
            <p v-else class="card-empty">暂无配置</p>
            <button type="button" class="text-button card-action">+ 添加域名</button>
          </li>

          <li class="sandbox-card">
            <header class="card-head">
              <span class="card-icon">📁</span>
              <h3>文件根目录</h3>
              <span class="card-count">{{ data.fileRootCount }}</span>
            </header>
            <p class="card-summary">已配置 <strong>{{ data.fileRootCount }}</strong> 个可访问的文件根目录</p>
            <button type="button" class="text-button card-action">+ 添加目录</button>
          </li>

          <li class="sandbox-card">
            <header class="card-head">
              <span class="card-icon">⚡</span>
              <h3>命令别名</h3>
              <span class="card-count">{{ data.commandAliases.length }}</span>
            </header>
            <ul v-if="data.commandAliases.length" class="card-list">
              <li v-for="alias in data.commandAliases" :key="alias" class="card-item">
                <code>{{ alias }}</code>
              </li>
            </ul>
            <p v-else class="card-empty">暂无配置</p>
            <button type="button" class="text-button card-action">+ 添加命令</button>
          </li>
        </ul>
      </section>
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
      <section class="runtime-overview">
        <header class="runtime-head">
          <span class="runtime-badge" :class="`status-${data.health.status.toLowerCase()}`">
            <span class="status-dot" aria-hidden="true"></span>
            {{ data.health.status === 'UP' ? '服务健康' : '状态异常' }}
          </span>
          <span class="runtime-version">{{ data.info.applicationName }} v{{ data.info.implementationVersion }}</span>
        </header>

        <ul class="runtime-stats">
          <li class="stat-card">
            <span class="stat-value">{{ data.info.profileCount }}</span>
            <span class="stat-label">Agents</span>
          </li>
          <li class="stat-card">
            <span class="stat-value">{{ data.info.toolCount }}</span>
            <span class="stat-label">Tools</span>
          </li>
          <li class="stat-card">
            <span class="stat-value">{{ data.info.providers.length }}</span>
            <span class="stat-label">Providers</span>
          </li>
          <li class="stat-card">
            <span class="stat-value stat-text">{{ data.info.javaVersion?.split(' ').at(-1) || '未知' }}</span>
            <span class="stat-label">Java</span>
          </li>
        </ul>

        <section class="runtime-detail">
          <h2>环境信息</h2>
          <dl>
            <dt>Java 版本</dt>
            <dd>{{ data.info.javaVersion || '未知' }}</dd>
            <dt>Provider 列表</dt>
            <dd>{{ data.info.providers.join(', ') || '未配置' }}</dd>
          </dl>
        </section>
      </section>
    </ManagementQuery>
  </main>
</template>
