/**
 * Date utilities — kept timezone-safe.
 *
 * The browser's `Date.toISOString()` converts to UTC before formatting, which
 * shifts the calendar day for users east of UTC (e.g. WIB +07:00) when the local
 * time is near midnight. For data captured in *local* business time (transaction
 * date, sampling date), always format from the local fields.
 */

export function toLocalISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function toLocalISODateOrUndefined(d: Date | null | undefined): string | undefined {
  return d ? toLocalISODate(d) : undefined;
}

/**
 * Format `yyyy-MM-dd HH:mm:ss` dari komponen waktu LOKAL — bentuk yang diminta
 * backend (`DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`).
 *
 * Jangan diganti dengan `toISOString()`: di WITA (+08:00) sebuah tenggat yang
 * dipilih pukul 00:00 tanggal 30 akan terkirim sebagai tanggal 29 pukul 16:00 —
 * tenggat mundur sehari, dan pengingat H-3/H+3 ikut salah hari.
 *
 * Jam default 17:00 dipakai bila pengguna hanya memilih tanggal (Calendar tanpa
 * showTime mengembalikan tengah malam), supaya tenggat berarti "akhir hari kerja"
 * dan bukan "sudah lewat begitu hari itu dimulai".
 */
export function toLocalDateTimeString(d: Date, defaultEndOfDay = true): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  const midnight = d.getHours() === 0 && d.getMinutes() === 0 && d.getSeconds() === 0;
  const jam = defaultEndOfDay && midnight ? 17 : d.getHours();
  const menit = defaultEndOfDay && midnight ? 0 : d.getMinutes();
  const detik = defaultEndOfDay && midnight ? 0 : d.getSeconds();
  return `${toLocalISODate(d)} ${pad(jam)}:${pad(menit)}:${pad(detik)}`;
}
