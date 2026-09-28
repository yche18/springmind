import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  gfm: true,
  breaks: true,
})

/**
 * Normalizes model output so structures such as ATX headings parse reliably.
 * Chinese-language models commonly emit headings without spaces, full-width # characters, CRLF, or indentation.
 */
function normalizeMarkdownSource(source: string): string {
  let text = source
    .replace(/\uFEFF/g, '')
    .replace(/\r\n?/g, '\n')
    // Convert full-width hash characters to ASCII.
    .replace(/＃/g, '#')

  // CommonMark permits up to three leading spaces; also insert the missing space in headings such as ###Title.
  text = text.replace(/^[ \t]{0,3}(#{1,6})(?=[^\s#])/gm, '$1 ')

  // Remove trailing whitespace that can produce odd tokens while streaming.
  text = text.replace(/[ \t]+$/gm, '')

  // Temporarily close an unfinished code fence so a partial stream remains renderable.
  const fenceCount = (text.match(/^```/gm) ?? []).length
  if (fenceCount % 2 === 1) {
    text = `${text}\n\`\`\``
  }

  return text
}

/**
 * Safely renders model-generated Markdown as HTML.
 * Streaming fragments may be incomplete, so parsing is best-effort and DOMPurify provides the XSS boundary.
 */
export function renderMarkdown(source: string): string {
  const text = normalizeMarkdownSource(source ?? '')
  if (text.trim().length === 0) {
    return ''
  }

  const rawHtml = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(rawHtml, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
  })
}
