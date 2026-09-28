<script setup lang="ts">
import type { DocumentFailureItem, DocumentStatusSummaryItem } from '../documentPageView'

defineProps<{
  summaryItems: DocumentStatusSummaryItem[]
  recentFailures: DocumentFailureItem[]
  resultsSummary: string
  matchedCount: number
}>()
</script>

<template>
  <section class="document-status-board">
    <div class="document-status-board__summary">
      <article
        v-for="item in summaryItems"
        :key="item.key"
        class="document-status-board__card"
        :data-tone="item.tone"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.description }}</p>
      </article>
    </div>

    <div class="document-status-board__details">
      <article class="document-status-board__panel">
        <div class="document-status-board__panel-header">
          <div>
            <p class="panel__eyebrow">{{ $t('documents.filter') }}</p>
            <h2>{{ $t('documents.currentMatches') }}</h2>
          </div>
          <strong class="document-status-board__highlight">{{ matchedCount }}</strong>
        </div>
        <p class="document-status-board__summary-text">{{ resultsSummary }}</p>
      </article>

      <article class="document-status-board__panel">
        <div class="document-status-board__panel-header">
          <div>
            <p class="panel__eyebrow">{{ $t('documents.issues') }}</p>
            <h2>{{ $t('documents.recentFailures') }}</h2>
          </div>
        </div>

        <ul v-if="recentFailures.length > 0" class="document-status-board__failure-list">
          <li v-for="item in recentFailures" :key="item.documentId">
            <strong>{{ item.fileName }}</strong>
            <span>{{ item.reason }}</span>
            <small>{{ item.uploadedAt }}</small>
          </li>
        </ul>
        <p v-else class="document-status-board__summary-text">{{ $t('documents.noFailuresInResults') }}</p>
      </article>
    </div>
  </section>
</template>
