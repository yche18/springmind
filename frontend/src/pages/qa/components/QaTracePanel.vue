<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchEvidenceSource, type AnswerTrace, type TraceEvidence, type SourceChunk } from '../../../api/qa'

const props = defineProps<{ trace: AnswerTrace; groupId: number }>()
const { t } = useI18n({ useScope: 'global' })
const sources = ref<Record<string, SourceChunk[]>>({})
const loading = ref<Record<string, boolean>>({})
const errors = ref<Record<string, string>>({})
let contextVersion = 0
watch(() => [props.trace.traceId, props.groupId], () => {
  contextVersion++
  sources.value = {}
  errors.value = {}
  loading.value = {}
})

async function showSource(evidence: TraceEvidence) {
  const version = contextVersion
  const id = evidence.evidenceId
  loading.value[id] = true
  errors.value[id] = ''
  try {
    const chunks = await fetchEvidenceSource(props.groupId, evidence)
    if (version === contextVersion) sources.value[id] = chunks
  } catch {
    if (version === contextVersion) errors.value[id] = t('trace.sourceError')
  } finally {
    if (version === contextVersion) loading.value[id] = false
  }
}
</script>

<template>
  <details class="qa-trace">
    <summary>{{ t('trace.title') }}</summary>
    <p class="qa-trace__note">{{ t('trace.note') }}</p>
    <dl class="qa-trace__metrics">
      <div><dt>{{ t('trace.evidenceLevel') }}</dt><dd>{{ t(`trace.levels.${trace.evidenceLevel}`) }}</dd></div>
      <div><dt>{{ t('trace.retrieval') }}</dt><dd>{{ trace.retrievalMs }} ms</dd></div>
      <div><dt>{{ t('trace.generation') }}</dt><dd>{{ trace.generationMs }} ms</dd></div>
      <div><dt>{{ t('trace.total') }}</dt><dd>{{ trace.totalMs }} ms</dd></div>
    </dl>
    <template v-if="trace.retrieval">
      <h4>{{ t('trace.queries') }} · {{ t(`trace.strategies.${trace.retrieval.queryPlan.strategy}`) }}</h4>
      <ul><li v-for="query in trace.retrieval.queryPlan.queries" :key="query">{{ query }}</li></ul>
      <p>{{ t('trace.hits', { vector: trace.retrieval.vectorHits, keyword: trace.retrieval.keywordHits, candidates: trace.retrieval.candidateCount }) }}</p>
      <template v-if="trace.retrieval.selection">
        <p>{{ t('trace.reranking') }}: {{ t(`trace.rerankStatuses.${trace.retrieval.selection.rerankStatus}`) }}</p>
        <p>{{ t('trace.budget', { input: trace.retrieval.selection.inputTokens, selected: trace.retrieval.selection.selectedTokens, max: trace.retrieval.selection.maxTokens }) }}</p>
      </template>
      <dl class="qa-trace__metrics">
        <div v-for="(ms, stage) in trace.retrieval.timingsMs" :key="stage">
          <dt>{{ t(`trace.stages.${stage}`) }}</dt><dd>{{ ms }} ms</dd>
        </div>
      </dl>
    </template>
    <p v-if="trace.evidence.length === 0">{{ t('trace.noEvidence') }}</p>
    <article v-for="evidence in trace.evidence" :key="evidence.evidenceId" class="qa-trace__evidence">
      <h4>{{ evidence.evidenceId }} · {{ evidence.fileName }}</h4>
      <p>{{ t(`trace.sources.${evidence.source}`) }} · {{ t('trace.score') }} {{ evidence.score.toFixed(4) }}</p>
      <p v-if="evidence.rrfRank && evidence.selectionRank">{{ t('trace.rankChange', { before: evidence.rrfRank, after: evidence.selectionRank }) }}</p>
      <p v-if="evidence.rerankScore != null">{{ t('trace.rerankScore') }}: {{ evidence.rerankScore.toFixed(4) }}</p>
      <p v-if="evidence.startChunkIndex !== null && evidence.endChunkIndex !== null">
        {{ t('trace.range', { start: evidence.startChunkIndex + 1, end: evidence.endChunkIndex + 1 }) }}
      </p>
      <blockquote>{{ evidence.excerpt }}{{ evidence.truncated ? '…' : '' }}</blockquote>
      <button v-if="evidence.documentId !== null && evidence.startChunkIndex !== null && evidence.endChunkIndex !== null"
        type="button" :disabled="loading[evidence.evidenceId]" @click="showSource(evidence)">
        {{ loading[evidence.evidenceId] ? t('trace.loading') : t('trace.openSource') }}
      </button>
      <p v-if="errors[evidence.evidenceId]" role="alert">{{ errors[evidence.evidenceId] }}</p>
      <div v-if="sources[evidence.evidenceId]" aria-live="polite">
        <p v-if="sources[evidence.evidenceId]?.length === 0">{{ t('trace.sourceUnavailable') }}</p>
        <section v-for="chunk in sources[evidence.evidenceId]" :key="chunk.chunkId">
          <h5>{{ t('trace.chunk', { index: chunk.chunkIndex + 1 }) }}</h5>
          <pre>{{ chunk.text }}</pre>
        </section>
      </div>
    </article>
    <p class="qa-trace__id">{{ t('trace.requestId') }}: {{ trace.traceId }}</p>
  </details>
</template>

<style scoped>
.qa-trace { border: 1px solid #cbd8e4; border-radius: 16px; padding: 1rem; background: #f7fafc; color: #22384c; }
summary { cursor: pointer; font-weight: 700; }
.qa-trace__note, .qa-trace__id { color: #60758c; font-size: .85rem; overflow-wrap: anywhere; }
.qa-trace__metrics { display: flex; flex-wrap: wrap; gap: 1rem 1.5rem; }
dt { font-size: .85rem; color: #60758c; }
dd { margin: .3rem 0 0; font-weight: 600; }
.qa-trace__evidence { border-top: 1px solid #dbe4ed; margin-top: 1rem; padding-top: .5rem; }
blockquote, pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: .7rem 0; padding: .8rem; background: white; border-radius: 8px; line-height: 1.65; font-family: inherit; }
pre { max-height: 24rem; overflow-y: auto; }
button { cursor: pointer; border: 1px solid #a9bdcf; border-radius: 8px; background: white; color: #244965; padding: .5rem .8rem; }
button:disabled { opacity: .6; cursor: wait; }
h4, h5 { margin: .7rem 0; overflow-wrap: anywhere; }
</style>
