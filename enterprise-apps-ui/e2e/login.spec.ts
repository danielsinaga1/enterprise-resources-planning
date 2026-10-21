import { expect, test } from '@playwright/test';

/**
 * PrimeReact `<Password>` memasang id pada <div> pembungkus, bukan pada <input>,
 * sehingga `#password` saja mengembalikan elemen yang tidak bisa diisi dan
 * `getByLabel('Password')` pun tidak menemukan input-nya.
 */
const passwordInput = '#password input';

test.describe('Halaman login', () => {
  test('menampilkan form login beserta field NPK dan password', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByLabel('NPK / Username')).toBeVisible();
    await expect(page.locator(passwordInput)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
  });

  test('menerima input NPK dan password', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel('NPK / Username').fill('12345');
    await page.locator(passwordInput).fill('rahasia');

    await expect(page.getByLabel('NPK / Username')).toHaveValue('12345');
    await expect(page.locator(passwordInput)).toHaveValue('rahasia');
  });
});
