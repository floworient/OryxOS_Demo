<script setup lang="ts">
import { fetchInfo } from '@/api/oryxos'
import ManagementQuery from './ManagementQuery.vue'
</script>

<template>
  <ManagementQuery :load="fetchInfo" v-slot="{ data }">
    <p v-if="!data.providers.length">当前没有已配置的 Provider。</p>
    <ul v-else>
      <li v-for="name in data.providers" :key="name">{{ name }}</li>
    </ul>
  </ManagementQuery>
</template>
