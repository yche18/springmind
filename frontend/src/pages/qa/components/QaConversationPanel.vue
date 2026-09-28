<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AskQuestionResponse } from '../../../api/qa'
import CitationList from '../../../components/CitationList.vue'
import MarkdownContent from '../../../components/MarkdownContent.vue'
import { humanizeQaRefusalMessage } from '../../../utils/model-error-message'

const props = defineProps<{
  currentGroupId: number | null
  currentGroupName: string
  currentQuestion: string
  result: AskQuestionResponse | null
  askError: string
  isGroupsLoading: boolean
  isSubmitting: boolean
}>()
const { t } = useI18n({ useScope: 'global' })

const refusalMessage = computed(() =>
  humanizeQaRefusalMessage(props.result?.reasonCode, props.result?.reasonMessage),
)

const refusalTitle = computed(() => {
  const code = props.result?.reasonCode
  if (code === 'INSUFFICIENT_EVIDENCE') return t('qa.insufficientEvidence')
  if (code === 'ANSWER_FORMAT_ERROR') return t('qa.modelOutputError')
  if (code && code.startsWith('MODEL_')) return t('qa.modelUnavailable')
  if (code === 'PROVIDER_ERROR' || code === 'PROVIDER_RATE_LIMITED') return t('qa.modelServiceError')
  return t('qa.notAnswered')
})
</script>

<template>
  <article class="panel panel--wide qa-conversation-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">{{ $t('common.result') }}</p>
        <h2>{{ $t('qa.answerAndEvidence') }}</h2>
      </div>
      <span v-if="result" class="panel__pill">
        {{ result.answered ? $t('qa.answered') : $t('qa.refused') }}
      </span>
    </div>

    <section class="qa-conversation-panel__question-card">
      <div class="qa-conversation-panel__question-meta">
        <span>{{ $t('qa.latestQuestion') }}</span>
        <strong>{{ currentGroupName }}</strong>
      </div>
      <p v-if="currentQuestion">{{ currentQuestion }}</p>
      <p v-else class="placeholder-text">{{ $t('qa.latestQuestionEmpty') }}</p>
    </section>

    <p v-if="askError" class="feedback feedback--error">{{ askError }}</p>
    <p v-if="isGroupsLoading" class="placeholder-text">{{ $t('qa.syncingKnowledgeBases') }}</p>
    <p v-else-if="currentGroupId === null" class="placeholder-text">{{ $t('qa.selectBeforeAsk') }}</p>
    <p v-else-if="isSubmitting" class="placeholder-text">{{ $t('qa.generating') }}</p>
    <p v-else-if="result === null" class="placeholder-text">{{ $t('qa.resultEmpty') }}</p>

    <template v-else>
      <section v-if="result.answered" class="qa-answer-card">
        <div class="qa-answer-card__header">
          <div>
            <p class="qa-answer-card__eyebrow">{{ $t('qa.evidenceBased') }}</p>
            <h3>{{ $t('qa.modelAnswer') }}</h3>
          </div>
          <span class="qa-answer-card__badge">{{ $t('qa.verifiable') }}</span>
        </div>
        <MarkdownContent
          class="qa-answer-card__body"
          :content="result.answer ?? ''"
          mode="markdown"
          show-copy
        />
        <p class="qa-answer-card__source">{{ $t('qa.answerSourceNote') }}</p>
      </section>

      <section v-else class="qa-refusal-card">
        <div class="qa-refusal-card__head">
          <strong>{{ refusalTitle }}</strong>
          <span v-if="result.reasonCode">{{ result.reasonCode }}</span>
        </div>
        <p>{{ refusalMessage }}</p>
      </section>

      <CitationList
        :citations="result.citations"
        :title="$t('qa.evidence')"
        :empty-text="result.answered ? $t('qa.noAnswerEvidence') : $t('qa.noRefusalEvidence')"
      />
    </template>
  </article>
</template>
