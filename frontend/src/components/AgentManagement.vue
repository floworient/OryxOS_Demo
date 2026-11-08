<script setup lang="ts">
import { fetchProfiles } from '@/api/oryxos'
import ManagementQuery from './ManagementQuery.vue'

defineEmits<{ chat: [name: string] }>()
</script>

<template>
  <ManagementQuery :load="fetchProfiles" v-slot="{ data }">
    <p v-if="!data.length">工作区没有 Agent。</p>
    <article v-for="agent in data" :key="agent.name" class="management-card">
      <h2>{{ agent.name }}</h2>
      <p>{{ agent.description }}</p>
      <p>{{ agent.providerName }} / {{ agent.model }}</p>
      <p>可用工具配置：{{ agent.tools.join('、') || '无' }}</p>
      <button type="button" class="ghost-button" :data-agent="agent.name"
        @click="$emit('chat', agent.name)">进入会话</button>
    </article>
  </ManagementQuery>
</template>
