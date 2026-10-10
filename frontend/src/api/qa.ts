import http from './http'

export interface CitationItem {
  documentId: number | null
  chunkId: number | null
  chunkIndex: number | null
  fileName: string
  score: number
  snippet: string | null
}

export interface AskQuestionPayload {
  groupId: number
  question: string
}

export interface AskQuestionResponse {
  answered: boolean
  answer: string | null
  reasonCode: string | null
  reasonMessage: string | null
  citations: CitationItem[]
  trace?: AnswerTrace
}

export interface TraceEvidence {
  evidenceId: string
  documentId: number | null
  fileName: string
  startChunkIndex: number | null
  endChunkIndex: number | null
  source: string
  score: number
  excerpt: string
  truncated: boolean
}

export interface AnswerTrace {
  traceId: string
  evidenceLevel: string
  retrieval: {
    queryPlan: { strategy: string; queries: string[] }
    timingsMs: Record<string, number>
    vectorHits: number
    keywordHits: number
    candidateCount: number
  } | null
  retrievalMs: number
  generationMs: number
  totalMs: number
  evidence: TraceEvidence[]
}

export interface SourceChunk {
  chunkId: number
  chunkIndex: number
  text: string
}

export async function fetchEvidenceSource(groupId: number, evidence: TraceEvidence): Promise<SourceChunk[]> {
  const { data } = await http.get<SourceChunk[]>('/qa/evidence', {
    params: { groupId, documentId: evidence.documentId, start: evidence.startChunkIndex, end: evidence.endChunkIndex },
  })
  return data
}

export async function askQuestion(payload: AskQuestionPayload): Promise<AskQuestionResponse> {
  const { data } = await http.post<AskQuestionResponse>('/qa/ask', payload)

  return {
    answered: data.answered,
    answer: data.answer ?? null,
    reasonCode: data.reasonCode ?? null,
    reasonMessage: data.reasonMessage ?? null,
    citations: data.citations ?? [],
    trace: data.trace,
  }
}
