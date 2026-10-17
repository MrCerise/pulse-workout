// Export the 1024x500 banner through the editor's existing PNG pipeline.
import { chromium } from "playwright";
import { mkdir, writeFile } from "node:fs/promises";
import JSZip from "jszip";
import path from "node:path";

const browser = await chromium.launch({ channel: "msedge", headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1500, height: 1000 }, acceptDownloads: true });
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto("http://127.0.0.1:3000", { waitUntil: "networkidle", timeout: 120000 });
  await page.getByRole("button", { name: "Export bundle", exact: true }).waitFor();
  const picker = page.getByRole("combobox").filter({ hasText: /Android Phone|Feature Graphic/ }).first();
  if (!(await picker.innerText()).includes("Feature Graphic")) {
    await picker.click();
    await page.getByRole("option", { name: "Feature Graphic", exact: true }).click();
  }
  await page.waitForFunction(() => document.querySelector("[data-feature-graphic]"));
  await page.evaluate(async () => {
    await document.fonts.load('400 91px "Athletic Display"');
    await document.fonts.load('italic 700 30px "Athletic Label"');
    await document.fonts.ready;
  });
  const pending = page.waitForEvent("download", { timeout: 120000 });
  await page.getByRole("button", { name: "Export bundle", exact: true }).click();
  const download = await pending;
  const stream = await download.createReadStream();
  const buffers = [];
  for await (const buffer of stream) buffers.push(buffer);
  const zip = await JSZip.loadAsync(Buffer.concat(buffers));
  const files = Object.values(zip.files).filter(file => file.name.endsWith(".png"));
  if (files.length !== 1) throw new Error(`Expected one feature graphic, got ${files.length}`);
  const destination = path.resolve("../../docs/store/feature-graphic.png");
  await writeFile(destination, await files[0].async("nodebuffer"));
  await mkdir("qa", { recursive: true });
  await writeFile("qa/feature-export.json", JSON.stringify({ destination, errors }, null, 2));
  if (errors.length) throw new Error(errors.join("\n"));
  console.log(destination);
} finally {
  await browser.close();
}
