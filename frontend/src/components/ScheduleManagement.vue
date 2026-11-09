<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError } from '@/api/http'
import {
  agentRunSchedule,
  fetchExecutionHistory,
  fetchProfiles,
  fetchSchedules,
  runSchedule,
  setScheduleEnabled,
} from '@/api/oryxos'
import type { ScheduledExecution, ScheduledTaskSummary } from '@/api/types'
import { mapError } from '@/utils/errors'

const tasks = ref<ScheduledTaskSummary[]>([])
const profiles = ref<{ name: string; description: string }[]>([])
const loading = ref(true)
const loadError = ref('')
const pending = reactive(new Set<string>())
const errors = reactive(new Map<string, string>())
let loadSequence = 0

const selectedAgent = ref('')
const agentSchedules = computed(() => {
  if (!selectedAgent.value) return []
  return tasks.value.filter(t => t.profileName === selectedAgent.value)
})
const scheduleKeys = computed(() => Array.from(new Set(tasks.value.map(t => t.scheduleKey))))

async function refresh() {
  const sequence = ++loadSequence
  loadError.value = ''
  try {
    const [taskResult, profileResult] = await Promise.all([
      fetchSchedules(),
      fetchProfiles(),
    ])
    if (sequence === loadSequence) {
      tasks.value = taskResult
      profiles.value = profileResult
      // Reset selection if current agent no longer available
      if (!profileResult.some((p: typeof profiles.value[number]) => p.name === selectedAgent.value)) {
        selectedAgent.value = ''
      }
    }
  } catch (error) {
    if (sequence === loadSequence) loadError.value = mapError(error).message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function actionError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'SCHEDULE_BUSY') return '该 Agent 的定时任务仍在运行，请稍后重试。'
    if (error.code === 'SCHEDULE_NOT_FOUND') return '定时任务已不存在，请刷新列表。'
  }
  return mapError(error).message
}

async function operate(task: ScheduledTaskSummary, action: 'run' | 'toggle') {
  const id = task.scheduleId
  if (pending.has(id)) return
  pending.add(id)
  errors.delete(id)
  try {
    if (action === 'run') {
      const result = await runSchedule(id)
      if (!result.success) errors.set(id, result.errorMessage ?? '定时任务执行失败。')
    } else {
      await setScheduleEnabled(id, !task.enabled)
    }
  } catch (error) {
    errors.set(id, actionError(error))
  } finally {
    await refresh()
    pending.delete(id)
  }
}

// ── Execution History (US1) ───────────────────────────────

const expandingTasks = reactive(new Set<string>())
const execHistories = reactive(new Map<string, ScheduledExecution[]>())
const execLoading = reactive(new Map<string, boolean>())

async function toggleHistory(task: ScheduledTaskSummary) {
  const id = task.scheduleId
  if (expandingTasks.has(id)) {
    expandingTasks.delete(id)
    return
  }
  expandingTasks.add(id)
  execLoading.set(id, true)
  try {
    const history = await fetchExecutionHistory(id)
    execHistories.set(id, history)
  } catch (error) {
    errors.set(id, mapError(error).message)
  } finally {
    execLoading.delete(id)
  }
}

// ── Agent + Key Run (US4) ────────────────────────────────

async function runByAgentAndKey() {
  if (!selectedAgent.value) return
  if (!agentSchedules.value.length) {
    errors.set('__agent_run__', '该 Agent 暂无定时任务可补跑。')
    return
  }
  const target = agentSchedules.value[0] // default to first scheduled key
  pending.add(`agent_${target.profileName}_${target.scheduleKey}`)
  errors.delete(`agent_${target.profileName}_${target.scheduleKey}`)
  try {
    const result = await agentRunSchedule(target.profileName, target.scheduleKey)
    if (!result.success) errors.set(`agent_${target.profileName}_${target.scheduleKey}`, result.errorMessage ?? '补跑失败。')
  } catch (error) {
    errors.set(`agent_${target.profileName}_${target.scheduleKey}`, actionError(error))
  } finally {
    pending.delete(`agent_${target.profileName}_${target.scheduleKey}`)
    await refresh()
  }
}

function displayTime(value: string | null, zone: string) {
  if (!value) return '—'
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: zone, dateStyle: 'short', timeStyle: 'short',
    }).format(new Date(value))
  } catch {
    return value
  }
}

function executionTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

// ── Pagination ───────────────────────────────────────────

const PAGE_SIZE = 10

const taskPage = ref(1)
const taskTotalPages = computed(() => Math.max(1, Math.ceil(tasks.value.length / PAGE_SIZE)))
const pagedTasks = computed(() => {
  const start = (taskPage.value - 1) * PAGE_SIZE
  return tasks.value.slice(start, start + PAGE_SIZE)
})
// 刷新后任务数减少时收回越界页码
watch(taskTotalPages, max => {
  if (taskPage.value > max) taskPage.value = max
})

const historyPages = reactive(new Map<string, number>())

function historyTotalPages(scheduleId: string): number {
  return Math.max(1, Math.ceil((execHistories.get(scheduleId)?.length ?? 0) / PAGE_SIZE))
}

function currentHistoryPage(scheduleId: string): number {
  return historyPages.get(scheduleId) ?? 1
}

function turnHistoryPage(scheduleId: string, delta: number) {
  const next = Math.min(Math.max(1, currentHistoryPage(scheduleId) + delta), historyTotalPages(scheduleId))
  historyPages.set(scheduleId, next)
}

function pagedHistory(scheduleId: string): ScheduledExecution[] {
  const history = execHistories.get(scheduleId) ?? []
  const start = (currentHistoryPage(scheduleId) - 1) * PAGE_SIZE
  return history.slice(start, start + PAGE_SIZE)
}

onMounted(refresh)
</script>

<template>
  <div class="schedule-container">
    <div v-if="loading" role="status">正在加载定时任务…</div>
    <div v-else-if="loadError" role="alert">
      <p>定时任务加载失败。{{ loadError }}</p>
      <button type="button" class="ghost-button" @click="refresh">重试</button>
    </div>
    <p v-else-if="!tasks.length">暂无定时任务。</p>
    <template v-else>
      <!-- Info banner: tasks are defined in Agent config -->
      <div class="info-banner" role="note">
        <span class="info-icon">ℹ️</span>
        <p>定时任务由 Agent 配置文件（<code>AGENT.md</code> 的 <code>schedules</code> 字段）定义，服务启动时自动加载。如需新增或修改任务，请编辑对应 Agent 的配置文件。</p>
      </div>

      <!-- Agent selector for US4 -->
      <div class="agent-selector">
        <label class="agent-select-label">
          <span>按 Agent 补跑：</span>
          <select v-model="selectedAgent">
            <option value="">全部 Agent</option>
            <option v-for="agent in profiles" :key="agent.name" :value="agent.name">{{ agent.name }}</option>
          </select>
        </label>
        <button type="button" class="ghost-button" :disabled="!selectedAgent || pending.size > 0"
          @click="runByAgentAndKey">补选中的任务</button>
      </div>

      <table aria-label="定时任务">
        <thead>
          <tr>
            <th scope="col">任务</th><th scope="col">Agent</th><th scope="col">Cron</th>
            <th scope="col">下次触发</th><th scope="col">上次结果</th><th scope="col">次数</th>
            <th scope="col">状态</th><th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in pagedTasks" :key="task.scheduleId">
            <th scope="row">{{ task.displayName }}</th>
            <td>{{ task.profileName }}</td>
            <td><code>{{ task.cron }}</code><br>{{ task.zone }}</td>
            <td>{{ displayTime(task.nextRunAt, task.zone) }}</td>
            <td>{{ task.lastStatus === 'success' ? '成功' : task.lastStatus === 'failed' ? '失败' : '尚未执行' }}</td>
            <td>{{ task.runCount }}</td>
            <td>{{ task.enabled ? '已启用' : '已停用' }}</td>
            <td>
              <div class="schedule-actions">
                <button type="button" class="ghost-button" :disabled="pending.has(task.scheduleId)"
                  @click="operate(task, 'run')">立即执行</button>
                <button type="button" class="ghost-button" :disabled="pending.has(task.scheduleId)"
                  @click="operate(task, 'toggle')">{{ task.enabled ? '停用' : '启用' }}</button>
                <button type="button" class="ghost-button"
                  @click="toggleHistory(task)">📋 历史</button>
              </div>
              <p v-if="pending.has(task.scheduleId)" role="status">正在处理…</p>
              <p v-if="errors.has(task.scheduleId)" role="alert">{{ errors.get(task.scheduleId) }}</p>
            </td>
          </tr>
        </tbody>
      </table>

      <nav v-if="tasks.length > PAGE_SIZE" class="pagination" aria-label="任务列表分页">
        <button type="button" class="ghost-button" :disabled="taskPage <= 1"
          @click="taskPage -= 1">上一页</button>
        <span class="page-indicator">第 {{ taskPage }} / {{ taskTotalPages }} 页 · 共 {{ tasks.length }} 条</span>
        <button type="button" class="ghost-button" :disabled="taskPage >= taskTotalPages"
          @click="taskPage += 1">下一页</button>
      </nav>

      <!-- Execution History Panel (US1) -->
      <div v-for="task in pagedTasks" :key="`hist-${task.scheduleId}`"
        v-show="expandingTasks.has(task.scheduleId)"
        class="history-panel">
        <details open>
          <summary>
            {{ task.displayName }} — 执行历史（共 {{ execHistories.get(task.scheduleId)?.length ?? 0 }} 条）
          </summary>
          <p v-if="execLoading.get(task.scheduleId)" role="status">加载历史中…</p>
          <p v-else-if="!execHistories.has(task.scheduleId) || !execHistories.get(task.scheduleId)?.length"
            class="empty-hint">暂无执行历史。</p>
          <table v-else class="history-table" aria-label="执行历史">
            <thead>
              <tr>
                <th scope="col">开始时间</th>
                <th scope="col">是否成功</th>
                <th scope="col">错误信息</th>
                <th scope="col">耗时 (ms)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="exec in pagedHistory(task.scheduleId)" :key="exec.startedAt">
                <td>{{ executionTime(exec.startedAt) }}</td>
                <td>{{ exec.success ? '✅ 成功' : '❌ 失败' }}</td>
                <td>{{ exec.errorMessage ?? '—' }}</td>
                <td>{{ exec.durationMs }}</td>
              </tr>
            </tbody>
          </table>
          <nav v-if="(execHistories.get(task.scheduleId)?.length ?? 0) > PAGE_SIZE"
            class="pagination history-pagination" :aria-label="`${task.displayName} 执行历史分页`">
            <button type="button" class="ghost-button" :disabled="currentHistoryPage(task.scheduleId) <= 1"
              @click="turnHistoryPage(task.scheduleId, -1)">上一页</button>
            <span class="page-indicator">第 {{ currentHistoryPage(task.scheduleId) }} / {{ historyTotalPages(task.scheduleId) }} 页</span>
            <button type="button" class="ghost-button"
              :disabled="currentHistoryPage(task.scheduleId) >= historyTotalPages(task.scheduleId)"
              @click="turnHistoryPage(task.scheduleId, 1)">下一页</button>
          </nav>
        </details>
      </div>
    </template>
  </div>
</template>

<style scoped>
.info-banner {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  background: var(--surface-info, #e8f4fd);
  border: 1px solid var(--border-info, #b3d9f2);
  border-radius: 4px;
  font-size: 0.9em;
  line-height: 1.5;
}
.info-banner .info-icon {
  flex-shrink: 0;
  margin-top: 0.1em;
}
.info-banner p {
  margin: 0;
}
.info-banner code {
  background: rgba(0,0,0,0.06);
  padding: 0.1em 0.3em;
  border-radius: 3px;
  font-size: 0.9em;
}
.agent-selector {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  padding: 0.5rem;
  background: var(--surface-secondary, #f5f5f5);
  border-radius: 4px;
}
.agent-select-label select {
  margin-left: 0.25rem;
  padding: 0.25rem 0.5rem;
  font-size: inherit;
}
.schedule-table { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 0.65rem; text-align: left; vertical-align: top; }
thead { border-bottom: 1px solid currentColor; }
.schedule-actions { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.history-panel { margin-top: 0.5rem; padding: 0.5rem; }
.history-panel details summary { cursor: pointer; user-select: none; }
.history-table th, .history-table td { padding: 0.4rem 0.5rem; font-size: 0.9em; }
.empty-hint { color: var(--muted-text, #888); font-style: italic; font-size: 0.9em; margin: 0; }
.pagination {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
.history-pagination { margin-top: 0.25rem; }
.page-indicator {
  font-size: 0.9em;
  color: var(--muted-text, #666);
  min-width: 8em;
  text-align: center;
}
</style>
