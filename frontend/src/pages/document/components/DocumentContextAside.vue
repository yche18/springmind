<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'inline'
  workspaceName: string
  workspaceMeta: string
  relationLabel: string
  availableActions: string[]
  filterContext: string[]
}>(), {
  variant: 'default',
})
</script>

<template>
  <section class="document-context-aside" :class="`document-context-aside--${variant}`">
    <section class="document-context-aside__section">
      <p class="panel__eyebrow">{{ $t('qa.knowledgeBase') }}</p>
      <h2>{{ $t('qa.currentRange') }}</h2>
      <strong>{{ workspaceName }}</strong>
      <p>{{ workspaceMeta }}</p>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">{{ $t('qa.role') }}</p>
      <h2>{{ $t('documents.currentIdentity') }}</h2>
      <div class="document-context-aside__badge">{{ relationLabel }}</div>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">{{ $t('common.actions') }}</p>
      <h2>{{ $t('documents.availableActions') }}</h2>
      <ul class="document-context-aside__list">
        <li v-for="item in availableActions" :key="item">{{ item }}</li>
      </ul>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">{{ $t('documents.filter') }}</p>
      <h2>{{ $t('documents.currentFilters') }}</h2>
      <ul class="document-context-aside__list">
        <li v-for="item in filterContext" :key="item">{{ item }}</li>
      </ul>
    </section>
  </section>
</template>
