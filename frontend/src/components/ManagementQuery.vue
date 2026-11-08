<script setup lang="ts" generic="T">
import { onScopeDispose, shallowRef, watch } from 'vue'

const props = defineProps<{ load: () => Promise<T> }>()
const result = shallowRef<T>()
const status = shallowRef<'loading' | 'ready' | 'error'>('loading')
let request = 0

async function refresh(): Promise<void> {
  const current = ++request
  status.value = 'loading'
  result.value = undefined
  try {
    const data = await props.load()
    if (request === current) {
      result.value = data
      status.value = 'ready'
    }
  } catch {
    if (request === current) status.value = 'error'
  }
}

watch(() => props.load, refresh, { immediate: true })
onScopeDispose(() => { request++ })
</script>

<template>
  <p v-if="status === 'loading'" role="status">正在加载…</p>
  <div v-else-if="status === 'error'" role="alert">
    <p>加载失败，请检查服务连接。</p>
    <button type="button" class="ghost-button" @click="refresh">重试</button>
  </div>
  <slot v-else-if="result !== undefined" :data="result" />
</template>
