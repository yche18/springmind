<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'default' | 'inline'
    workspaceName: string
    workspaceMeta: string
    relationLabel: string
    questionContext: string
    evidenceSummary: string
    usageHints: string[]
  }>(),
  {
    variant: 'default',
  },
)
</script>

<template>
  <section class="qa-context-aside" :class="`qa-context-aside--${variant}`">
    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">{{ $t('qa.knowledgeBase') }}</p>
      <h2>{{ $t('qa.currentRange') }}</h2>
      <strong>{{ workspaceName }}</strong>
      <p>{{ workspaceMeta }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">{{ $t('qa.role') }}</p>
      <h2>{{ $t('qa.permissionBoundary') }}</h2>
      <div class="qa-context-aside__badge">{{ relationLabel }}</div>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">{{ $t('qa.enterQuestion') }}</p>
      <h2>{{ $t('qa.currentContext') }}</h2>
      <p>{{ questionContext }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">{{ $t('qa.evidence') }}</p>
      <h2>{{ $t('qa.retrievalSummary') }}</h2>
      <p>{{ evidenceSummary }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">{{ $t('qa.hints') }}</p>
      <h2>{{ $t('qa.usageAdvice') }}</h2>
      <ul class="qa-context-aside__list">
        <li v-for="item in usageHints" :key="item">{{ item }}</li>
      </ul>
    </section>
  </section>
</template>
