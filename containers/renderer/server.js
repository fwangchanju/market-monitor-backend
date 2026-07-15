const express = require('express')
const { chromium } = require('playwright')

const app = express()
app.use(express.json())

const PORT = process.env.PORT || 3000
const DASHBOARD_URL = process.env.DASHBOARD_URL || 'http://localhost:5173'
const DASHBOARD_USER = process.env.DASHBOARD_USER || ''
const DASHBOARD_PASS = process.env.DASHBOARD_PASS || ''

app.get('/health', (req, res) => {
  res.json({ status: 'ok' })
})

app.post('/capture', async (req, res) => {
  let browser
  try {
    browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'],
    })
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
    })

    if (DASHBOARD_USER && DASHBOARD_PASS) {
      const credentials = Buffer.from(`${DASHBOARD_USER}:${DASHBOARD_PASS}`).toString('base64')
      await context.setExtraHTTPHeaders({ Authorization: `Basic ${credentials}` })
    }

    const page = await context.newPage()
    await page.goto(DASHBOARD_URL, { waitUntil: 'networkidle', timeout: 30000 })
    await page.waitForTimeout(2000)

    const sections = await page.$$('section.section')
    const images = []

    for (let i = 0; i < sections.length; i++) {
      const buffer = await sections[i].screenshot({ type: 'png' })
      images.push({
        name: `section-${i}`,
        data: buffer.toString('base64'),
      })
    }

    res.json({ images })
  } catch (err) {
    console.error('캡처 오류:', err.message)
    res.status(500).json({ error: err.message })
  } finally {
    if (browser) await browser.close()
  }
})

app.listen(PORT, () => {
  console.log(`market-monitor-renderer 시작 — port ${PORT}, dashboard: ${DASHBOARD_URL}`)
})
