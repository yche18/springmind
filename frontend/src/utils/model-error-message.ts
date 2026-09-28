import { translate } from '../i18n'

const MODEL_ERROR_HINT_KEYS: Record<string, string> = {
  AI_CHAT_NOT_CONFIGURED: 'errors.aiNotConfigured',
  AI_CHAT_CONFIGURATION_INVALID: 'errors.aiInvalidConfiguration',
  ANSWER_FORMAT_ERROR: 'errors.answerFormat',
  INSUFFICIENT_EVIDENCE: 'errors.insufficientEvidence',
}

const REASON_CODE_HINT_KEYS: Record<string, string> = {
  ANSWER_FORMAT_ERROR: 'errors.answerFormat',
  INSUFFICIENT_EVIDENCE: 'errors.insufficientEvidence',
}

/** Map API or exception codes to locale-aware user messages. */
export function humanizeModelErrorMessage(raw: string | null | undefined, fallback = translate('errors.requestFailed')): string {
  if (raw == null) return fallback
  const text = raw.trim()
  if (!text) return fallback

  if (MODEL_ERROR_HINT_KEYS[text]) {
    return translate(MODEL_ERROR_HINT_KEYS[text])
  }

  // Some gateways may embed the code in a longer string.
  for (const [code, key] of Object.entries(MODEL_ERROR_HINT_KEYS)) {
    if (text === code || text.includes(code)) {
      return translate(key)
    }
  }

  return text
}

/** Map QA refusal reasonCode / reasonMessage for display. */
export function humanizeQaRefusalMessage(
  reasonCode: string | null | undefined,
  reasonMessage: string | null | undefined,
): string {
  if (reasonCode && REASON_CODE_HINT_KEYS[reasonCode]) {
    return translate(REASON_CODE_HINT_KEYS[reasonCode])
  }
  if (reasonCode && MODEL_ERROR_HINT_KEYS[reasonCode]) {
    return translate(MODEL_ERROR_HINT_KEYS[reasonCode])
  }
  if (reasonMessage && reasonMessage.trim()) {
    return humanizeModelErrorMessage(reasonMessage, reasonMessage)
  }
  return translate('errors.insufficientEvidence')
}
