<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '@/api/http'
import { fetchSchedules, runSchedule, setScheduleEnabled } from '@/api/oryxos'
import type { ScheduledTaskSummary } from '@/api/types'
import { mapError } from '@/utils/errors'

const tasks = ref<ScheduledTaskSummary[]>([])
const loading = ref(true)
const loadError = ref('')
const pending = reactive(new Set<string>())
const errors = reactive(new Map<string, string>())
let loadSequence = 0

async function refresh() {
  const sequence = ++loadSequence
  loadError.value = ''
  try {
    const result = await fetchSchedules()
    if (sequence === loadSequence) tasks.value = result
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

onMounted(refresh)
</script>

<template>
  <p v-if="loading" role="status">正在加载定时任务…</p>
  <div v-else-if="loadError" role="alert">
    <p>定时任务加载失败。{{ loadError }}</p>
    <button type="button" class="ghost-button" @click="refresh">重试</button>
  </div>
  <p v-else-if="!tasks.length">暂无定时任务。</p>
  <div v-else class="schedule-table">
    <table aria-label="定时任务">
      <thead>
        <tr>
          <th scope="col">任务</th><th scope="col">Agent</th><th scope="col">Cron</th>
          <th scope="col">下次触发</th><th scope="col">上次结果</th><th scope="col">次数</th>
          <th scope="col">状态</th><th scope="col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.scheduleId">
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
            </div>
            <p v-if="pending.has(task.scheduleId)" role="status">正在处理…</p>
            <p v-if="errors.has(task.scheduleId)" role="alert">{{ errors.get(task.scheduleId) }}</p>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.schedule-table { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 0.65rem; text-align: left; vertical-align: top; }
thead { border-bottom: 1px solid currentColor; }
.schedule-actions { display: flex; gap: 0.4rem; }
</style>
