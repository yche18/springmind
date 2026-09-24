/** Map QA and AI configuration error codes to user-facing Chinese hints. */
const MODEL_ERROR_HINTS: Record<string, string> = {
  AI_CHAT_NOT_CONFIGURED:
    '问答模型尚未配置。请在启动环境中设置 AI_API_KEY，并确认模型地址和名称。',
  AI_CHAT_CONFIGURATION_INVALID:
    '问答模型配置不完整。请检查 AI_BASE_URL、AI_API_KEY 和 AI_CHAT_MODEL。',
  ANSWER_FORMAT_ERROR:
    '模型返回内容无法解析。请稍后重试；若持续出现，请联系管理员检查问答模型与提示配置。',
  INSUFFICIENT_EVIDENCE:
    '检索到的有效证据不足，暂不回答。可尝试换一种问法，或确认知识库中已有相关文档。',
}

const REASON_CODE_HINTS: Record<string, string> = {
  ANSWER_FORMAT_ERROR: MODEL_ERROR_HINTS.ANSWER_FORMAT_ERROR,
  INSUFFICIENT_EVIDENCE: MODEL_ERROR_HINTS.INSUFFICIENT_EVIDENCE,
}

/** Map API / exception message (often a code) to a friendly Chinese hint. */
export function humanizeModelErrorMessage(raw: string | null | undefined, fallback = '请求失败'): string {
  if (raw == null) return fallback
  const text = raw.trim()
  if (!text) return fallback

  if (MODEL_ERROR_HINTS[text]) {
    return MODEL_ERROR_HINTS[text]
  }

  // Some gateways may embed the code in a longer string.
  for (const [code, hint] of Object.entries(MODEL_ERROR_HINTS)) {
    if (text === code || text.includes(code)) {
      return hint
    }
  }

  return text
}

/** Map QA refusal reasonCode / reasonMessage for display. */
export function humanizeQaRefusalMessage(
  reasonCode: string | null | undefined,
  reasonMessage: string | null | undefined,
): string {
  if (reasonCode && REASON_CODE_HINTS[reasonCode]) {
    return REASON_CODE_HINTS[reasonCode]
  }
  if (reasonCode && MODEL_ERROR_HINTS[reasonCode]) {
    return MODEL_ERROR_HINTS[reasonCode]
  }
  if (reasonMessage && reasonMessage.trim()) {
    return humanizeModelErrorMessage(reasonMessage, reasonMessage)
  }
  return '当前证据不足，无法给出可靠回答。'
}
