/**
 * Menambal `localStorage` / `sessionStorage` untuk lingkungan test.
 *
 * Node 25 menyediakan Web Storage bawaan, dan runner menjalankan Node dengan
 * `--localstorage-file` tanpa path yang valid. Akibatnya global `localStorage`
 * tetap ada tetapi berupa objek rusak — `getItem`/`setItem`/`clear` bukan fungsi —
 * dan objek itu ikut membayangi milik jsdom, jadi `window.localStorage` sama
 * rusaknya. Gejalanya menyesatkan: seluruh test gagal dengan
 * "localStorage.getItem is not a function", termasuk test yang tidak
 * menyentuh storage sama sekali.
 *
 * Penggantinya sengaja in-memory: state tidak bocor antar file test, dan tidak
 * ada file yang perlu dibersihkan di disk.
 */

function createMemoryStorage(): Storage {
  const store = new Map<string, string>();

  return {
    get length() {
      return store.size;
    },
    clear() {
      store.clear();
    },
    getItem(key: string) {
      const k = String(key);
      return store.has(k) ? (store.get(k) as string) : null;
    },
    key(index: number) {
      return Array.from(store.keys())[index] ?? null;
    },
    removeItem(key: string) {
      store.delete(String(key));
    },
    setItem(key: string, value: string) {
      store.set(String(key), String(value));
    },
  } as Storage;
}

function installStorage(name: 'localStorage' | 'sessionStorage'): void {
  const targets: object[] = [globalThis];
  if (typeof window !== 'undefined' && window !== (globalThis as unknown)) {
    targets.push(window);
  }

  // Kalau suatu saat runner/Node sudah menyediakan Storage yang benar, jangan
  // ditimpa — biarkan implementasi asli yang dipakai.
  const current = (globalThis as Record<string, unknown>)[name] as Storage | undefined;
  if (current && typeof current.getItem === 'function') return;

  const storage = createMemoryStorage();
  for (const target of targets) {
    Object.defineProperty(target, name, {
      value: storage,
      configurable: true,
      writable: true,
    });
  }
}

installStorage('localStorage');
installStorage('sessionStorage');
