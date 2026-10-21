/**
 * i18next khusus ERP. Terjemahan ada di src/erp/locales/<lang>/<namespace>.json.
 *
 * Instance TERPISAH dari i18n platform (createInstance, tanpa initReactI18next) supaya
 * namespace & bahasa ERP tidak menimpa terjemahan IRMS/LIMS. Pohon komponen ERP dibungkus
 * <I18nextProvider i18n={erpI18n}> (lihat ErpShell). Bahasa mengikuti pemilih bahasa platform.
 * Namespace EAGER dikemas langsung; sisanya dimuat saat pertama dipakai (komponen suspend).
 */
import i18next, { type BackendModule, type ResourceKey } from 'i18next';
import { setFormatLocale } from '@/lib/format';
import { localeFor, normalizeLanguage, readStoredLanguage, writeStoredLanguage, type Language } from './language';

export const EAGER_NAMESPACES = ['common', 'shell'] as const;

const eagerFiles = import.meta.glob<{ default: ResourceKey }>('../locales/*/{common,shell}.json', { eager: true });
const lazyFiles = import.meta.glob<{ default: ResourceKey }>('../locales/*/*.json');

function fileKey(lng: string, ns: string): string {
  return `../locales/${lng}/${ns}.json`;
}

const resources: Record<string, Record<string, ResourceKey>> = {};
for (const [path, mod] of Object.entries(eagerFiles)) {
  const [, lng, ns] = /\/locales\/([^/]+)\/([^/]+)\.json$/.exec(path) ?? [];
  if (lng && ns) (resources[lng] ??= {})[ns] = mod.default;
}

const lazyBackend: BackendModule = {
  type: 'backend',
  init() {},
  read(lng, ns, callback) {
    const load = lazyFiles[fileKey(lng, ns)];
    if (!load) return callback(null, {});
    load().then(
      (mod) => callback(null, mod.default),
      (error: unknown) => callback(error instanceof Error ? error : new Error(String(error)), null),
    );
  },
};

const initial = readStoredLanguage();
setFormatLocale(localeFor(initial));

const i18n = i18next.createInstance();

void i18n.use(lazyBackend).init({
  lng: initial,
  fallbackLng: 'id',
  supportedLngs: ['id', 'en'],
  ns: [...EAGER_NAMESPACES],
  defaultNS: 'common',
  resources,
  partialBundledLanguages: true,
  interpolation: { escapeValue: false },
  returnNull: false,
  react: { useSuspense: true },
});

i18n.on('languageChanged', (lng) => setFormatLocale(localeFor(normalizeLanguage(lng))));

/** Ganti bahasa UI ERP sekarang dan ingat di perangkat ini. */
export function setUiLanguage(language: unknown): void {
  const next: Language = normalizeLanguage(language);
  writeStoredLanguage(next);
  if (i18n.language !== next) void i18n.changeLanguage(next);
}

export default i18n;
