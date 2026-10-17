// Use the editor's own export pipeline; never reconstruct or redraw the app UI.
import { chromium } from "playwright";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import JSZip from "jszip";

const output = path.resolve("../../docs/store/screenshots");
await mkdir(output, { recursive: true });
await mkdir(path.join(output, "en"), { recursive: true });
await mkdir("qa", { recursive: true });
const browser = await chromium.launch({ channel: "msedge", headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 }, acceptDownloads: true });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("http://127.0.0.1:3000", { waitUntil: "networkidle", timeout: 120000 });
  await page.getByRole("button", { name: "Export bundle", exact: true }).waitFor({ timeout: 120000 });
  const picker = page.getByRole("combobox").filter({ hasText: /Android Phone|Feature Graphic/ }).first();
  if (!(await picker.innerText()).includes("Android Phone")) {
    await picker.click();
    await page.getByRole("option", { name: "Android Phone", exact: true }).click();
  }
  await page.evaluate(async () => {
    await document.fonts.load('400 190px "Athletic Display"');
    await document.fonts.load('italic 700 28px "Athletic Label"');
    await document.fonts.ready;
  });
  const headlineAudit = await page.evaluate(() => {
    const root = document.querySelector('[aria-hidden="true"][style*="-99999"]');
    const headlines = [...root.querySelectorAll("[data-neon-headline]")];
    return headlines.map((headline) => [...headline.querySelectorAll("[data-headline-line]")].map((line) => {
      const range = document.createRange();
      range.selectNodeContents(line);
      const bounds = range.getBoundingClientRect();
      return { text: line.innerText, width: bounds.width, font: getComputedStyle(headline).fontFamily };
    }));
  });
  if (headlineAudit.length !== 6 || headlineAudit.flat().some((line) => line.width > 1080 * 0.88)) {
    throw new Error("Headline width audit failed");
  }
  await writeFile("qa/headline-audit.json", JSON.stringify(headlineAudit, null, 2));
  const downloadPromise = page.waitForEvent("download", { timeout: 180000 });
  await page.getByRole("button", { name: "Export bundle", exact: true }).click();
  const download = await downloadPromise;
  const bundle = path.join(output, "interval-coach-google-play-en.zip");
  await download.saveAs(bundle);
  const exported = await JSZip.loadAsync(await readFile(bundle));
  const files = Object.values(exported.files).filter((file) => file.name.endsWith(".png")).sort((a,b) => a.name.localeCompare(b.name));
  if (files.length !== 6) throw new Error(`Expected six screenshots; got ${files.length}`);
  const names = ["01-live-timer", "02-workout-builder", "03-workout-library", "04-run-your-5k", "05-weekly-goals", "06-your-plan-your-pace"];
  const flatBundle = new JSZip();
  for (let i = 0; i < files.length; i++) {
    const bytes = await files[i].async("nodebuffer");
    const name = `${names[i]}.png`;
    await writeFile(path.join(output, "en", name), bytes);
    flatBundle.file(name, bytes);
  }
  await writeFile(bundle, await flatBundle.generateAsync({ type: "nodebuffer" }));
  await page.screenshot({ path: "qa/editor-preview.png" });
  console.log(JSON.stringify({ output, errors, headlineAudit }, null, 2));
  if (errors.length) process.exitCode = 1;
} finally {
  await browser.close();
}
