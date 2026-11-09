<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ConfirmDialog from './ConfirmDialog.vue'
import type { ProfileSummary, SessionSummary } from '@/api/types'
import { formatRelativeTime } from '@/utils/time'
import type { UiError } from '@/utils/errors'

const props = defineProps<{
  sessions: SessionSummary[]
  profiles?: ProfileSummary[]
  status: 'loading' | 'ready' | 'empty' | 'error'
  error: UiError | null
  activeId: string | null
  activeAgent?: string | null
  /** Agent → 默认会话名模板（支持 {date} 占位符），由 App 层维护。 */
  titleTemplates?: Record<string, string> | null
}>()

const emit = defineEmits<{
  select: [session: SessionSummary]
  archive: [session: SessionSummary]
  restore: [session: SessionSummary]
  rename: [session: SessionSummary, title: string]
  titleTemplate: [agent: string, template: string | null]
  retry: []
}>()

interface SessionGroup {
  key: string
  label: string
  sessions: SessionSummary[]
  archivedCount: number
}

// 后端归档约束：仅 web 渠道 + ACTIVE 状态可归档，其余会话不显示删除按钮
function canArchive(session: SessionSummary): boolean {
  return session.channel === 'web' && session.status === 'ACTIVE'
}

// 已归档会话默认隐藏，开关打开后显示并带徽标
const showArchived = ref(false)

/** 会话按 profileName 归组：已知 Agent 按侧边栏顺序排列，未知 Agent 按名称排在后面。 */
const groupedSessions = computed<SessionGroup[]>(() => {
  // HTTP envelope 的 data 运行时不校验类型，非数组时按空处理避免渲染崩溃
  const sessions = Array.isArray(props.sessions) ? props.sessions : []
  const byAgent = new Map<string, { visible: SessionSummary[]; archived: number }>()
  for (const session of sessions) {
    const bucket = byAgent.get(session.profileName) ?? { visible: [], archived: 0 }
    if (session.status === 'ARCHIVED' && !showArchived.value) {
      bucket.archived += 1
    } else {
      bucket.visible.push(session)
    }
    byAgent.set(session.profileName, bucket)
  }
  const profiles = props.profiles ?? []
  const order = new Map(profiles.map((profile, index) => [profile.name, index]))
  return [...byAgent.entries()]
    .sort(([nameA], [nameB]) => {
      const rankA = order.get(nameA) ?? Number.MAX_SAFE_INTEGER
      const rankB = order.get(nameB) ?? Number.MAX_SAFE_INTEGER
      return rankA === rankB ? nameA.localeCompare(nameB) : rankA - rankB
    })
    .map(([profileName, bucket]) => ({
      key: profileName,
      label: profiles.find((profile) => profile.name === profileName)?.description || profileName,
      sessions: bucket.visible,
      archivedCount: bucket.archived,
    }))
})

function time(value: string): string {
  return formatRelativeTime(value)
}

/** 会话显示名：优先会话名，无会话名回退渠道 + 消息数。 */
function sessionTitle(session: SessionSummary): string {
  if (session.title && session.title.trim().length > 0) {
    return session.title
  }
  return `${session.channel} · ${session.messageCount} 条消息`
}

// 重命名：弹窗填写新名字，确认后提交（避免行内输入框挤压变窄）
const pendingRename = ref<SessionSummary | null>(null)
const renameValue = ref('')

function startRename(session: SessionSummary) {
  pendingRename.value = session
  renameValue.value = session.title ?? ''
}

function cancelRename() {
  pendingRename.value = null
  renameValue.value = ''
}

function confirmRename() {
  const target = pendingRename.value
  if (!target) {
    return
  }
  const title = renameValue.value.trim()
  if (title.length === 0 || title === target.title) {
    cancelRename()
    return
  }
  pendingRename.value = null
  renameValue.value = ''
  emit('rename', target, title)
}

// 按 Agent 定制默认会话名：弹窗编辑模板，{date} 渲染为当天日期
const templateAgent = ref<string | null>(null)
const templateValue = ref('')

function openTemplate(agent: string) {
  templateAgent.value = agent
  templateValue.value = props.titleTemplates?.[agent] ?? ''
}

function cancelTemplate() {
  templateAgent.value = null
  templateValue.value = ''
}

function confirmTemplate() {
  if (templateAgent.value === null) {
    return
  }
  const agent = templateAgent.value
  const template = templateValue.value.trim()
  templateAgent.value = null
  templateValue.value = ''
  emit('titleTemplate', agent, template.length > 0 ? template : null)
}

// 手风琴：同一时刻只展开一个 Agent 的会话组
const expandedKey = ref<string | null>(props.activeAgent ?? null)

// 点击 Agent（或通过会话切换 Agent）时，自动展开它的组并收起其他组
watch(
  () => props.activeAgent,
  (agent) => {
    expandedKey.value = agent ?? null
  },
  { immediate: true },
)

function toggleGroup(key: string) {
  expandedKey.value = expandedKey.value === key ? null : key
}

function isExpanded(key: string): boolean {
  return expandedKey.value === key
}

// D3：归档前确认弹窗，确认后才真正触发归档
const pendingArchive = ref<SessionSummary | null>(null)

function requestArchive(session: SessionSummary) {
  pendingArchive.value = session
}

function confirmArchive() {
  if (pendingArchive.value) {
    const target = pendingArchive.value
    pendingArchive.value = null
    emit('archive', target)
  }
}

function cancelArchive() {
  pendingArchive.value = null
}
</script>

<template>
  <section class="session-history">
    <div class="sidebar-head">最近会话</div>
    <p v-if="status === 'loading'" class="sidebar-hint">正在加载会话…</p>
    <div v-else-if="status === 'error'" class="sidebar-hint" role="alert">
      <p>{{ error?.message ?? '会话加载失败' }}</p>
      <button type="button" class="text-button" @click="$emit('retry')">重试</button>
    </div>
    <p v-else-if="status === 'empty'" class="sidebar-hint">暂无会话。</p>
    <template v-else>
      <label class="archived-toggle">
        <input type="checkbox" v-model="showArchived" />
        显示已归档会话
      </label>
      <ul class="session-groups">
        <li v-for="group in groupedSessions" :key="group.key" class="session-group">
          <div class="session-group-row">
            <button type="button" class="session-group-head"
              :class="{ expanded: isExpanded(group.key) }"
              :aria-expanded="isExpanded(group.key)"
              @click="toggleGroup(group.key)">
              <span class="session-chevron" aria-hidden="true">▸</span>
              <span class="session-group-title">{{ group.label }}</span>
              <span class="session-group-count">{{ group.sessions.length }}</span>
            </button>
            <button type="button" class="session-group-config"
              :aria-label="`设置 ${group.label} 的默认会话名`"
              title="设置默认会话名（支持 {date} 占位符）"
              @click="openTemplate(group.key)">⚙️</button>
          </div>
          <ul v-show="isExpanded(group.key)" class="agent-list">
            <li v-for="session in group.sessions" :key="session.id" class="session-entry"
              :class="{ archived: session.status === 'ARCHIVED' }">
              <button type="button" class="agent-item" :class="{ active: session.id === activeId }"
                @click="$emit('select', session)">
                <span class="agent-title" :title="sessionTitle(session)">{{ sessionTitle(session) }}</span>
                <span class="agent-name">
                  <span v-if="session.status === 'ARCHIVED'" class="archived-badge">已归档</span>
                  {{ session.channel }} · {{ session.messageCount }} 条
                  {{ time(session.lastActiveAt) }}
                </span>
              </button>
              <button type="button" class="session-rename-button"
                :aria-label="`重命名 ${group.label} 的 ${session.channel} 会话`"
                title="重命名会话" @click="startRename(session)">✏️</button>
              <button v-if="canArchive(session)" type="button" class="session-archive-button"
                :aria-label="`归档 ${group.label} 的 ${session.channel} 会话`"
                title="归档会话" @click="requestArchive(session)">🗑️</button>
              <button v-if="session.status === 'ARCHIVED'" type="button" class="session-restore-button"
                :aria-label="`恢复 ${group.label} 的 ${session.channel} 会话`"
                title="撤销归档，恢复会话" @click="emit('restore', session)">↩️</button>
            </li>
          </ul>
        </li>
      </ul>
    </template>
  </section>
  <ConfirmDialog :open="pendingArchive !== null" title="归档会话"
    :description="pendingArchive ? `确定归档该会话（${pendingArchive.channel} · ${pendingArchive.messageCount} 条消息）？归档后将不再出现在默认列表中。` : ''"
    confirm-label="归档" danger @confirm="confirmArchive" @cancel="cancelArchive" />

  <div v-if="pendingRename !== null" class="dialog-overlay" role="presentation" @click.self="cancelRename">
    <div class="dialog-card" role="dialog" aria-modal="true" aria-label="重命名会话">
      <h3 class="dialog-title">重命名会话</h3>
      <p class="dialog-description">
        为「{{ pendingRename ? sessionTitle(pendingRename) : '' }}」输入新的会话名（最多 200 字符）。
        留空或与当前一致将不做更改。
      </p>
      <form @submit.prevent="confirmRename">
        <input v-model="renameValue" class="session-rename-input dialog-input" type="text"
          placeholder="新的会话名" maxlength="200" aria-label="新的会话名" />
        <div class="dialog-actions">
          <button type="button" class="ghost-button" @click="cancelRename">取消</button>
          <button type="submit" class="dialog-confirm" @click="confirmRename">保存</button>
        </div>
      </form>
    </div>
  </div>

  <div v-if="templateAgent !== null" class="dialog-overlay" role="presentation" @click.self="cancelTemplate">
    <div class="dialog-card" role="dialog" aria-modal="true" aria-label="设置默认会话名">
      <h3 class="dialog-title">设置默认会话名</h3>
      <p class="dialog-description">
        为该 Agent 的新会话设置默认会话名。支持 <code>{date}</code> 占位符（自动替换为当天日期，如 2026-11-09）；
        留空表示清除，新会话将默认使用第一条提问。
      </p>
      <form @submit.prevent="confirmTemplate">
        <input v-model="templateValue" class="session-rename-input dialog-input" type="text"
          placeholder="如：{date} 每日简报" maxlength="200" aria-label="默认会话名模板" />
        <div class="dialog-actions">
          <button type="button" class="ghost-button" @click="cancelTemplate">取消</button>
          <button type="submit" class="dialog-confirm" @click="confirmTemplate">保存</button>
        </div>
      </form>
    </div>
  </div>
</template>
