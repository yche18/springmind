import { createI18n } from 'vue-i18n'
import en from './locales/en'
import zh from './locales/zh'

export type AppLocale = 'en' | 'zh'

const STORAGE_KEY = 'springmind.locale'

function isAppLocale(value: string | null): value is AppLocale {
  return value === 'en' || value === 'zh'
}

function resolveInitialLocale(): AppLocale {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (isAppLocale(saved)) return saved
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en'
}

const initialLocale = resolveInitialLocale()

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale,
  fallbackLocale: 'en',
  messages: { en, zh },
})

export function setAppLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en'
}

export function translate(key: string, named?: Record<string, unknown>): string {
  return named ? i18n.global.t(key, named) : i18n.global.t(key)
}

export function getAppLocale(): AppLocale {
  return i18n.global.locale.value
}

setAppLocale(initialLocale)
