import { test, expect } from '@playwright/test'

const ADMIN_USERNAME = 'admin'
const ADMIN_PASSWORD = 'changeme123'

test.describe('authentication', () => {
  test('redirect-when-logged-out, login, nav, logout, session really ends', async ({ page }) => {
    // Visiting a protected route while logged out redirects to /login.
    await page.goto('/dashboard/tickets')
    await expect(page).toHaveURL(/\/login$/)

    // Logging in with valid admin credentials lands on /dashboard/home.
    await page.getByLabel('Username').fill(ADMIN_USERNAME)
    await page.getByLabel('Password').fill(ADMIN_PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page).toHaveURL(/\/dashboard\/home$/)
    await expect(page.getByRole('link', { name: 'Home' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Tickets' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible()

    // Clicking Tickets shows the tickets page.
    await page.getByRole('link', { name: 'Tickets' }).click()
    await expect(page).toHaveURL(/\/dashboard\/tickets$/)
    await expect(page.getByRole('heading', { name: 'Tickets' })).toBeVisible()

    // Clicking Logout redirects back to /login.
    await page.getByRole('button', { name: 'Logout' }).click()
    await expect(page).toHaveURL(/\/login$/)

    // Direct re-visit to the protected route redirects to /login again.
    await page.goto('/dashboard/tickets')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('wrong password shows the inline error and stays on /login', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Username').fill(ADMIN_USERNAME)
    await page.getByLabel('Password').fill('wrong-password')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByText('Invalid username or password')).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
  })
})
