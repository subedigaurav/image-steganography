/**
 * Visualize page: analyze stego image via API, render block overlay.
 *
 * @typedef {Object} StegoAnalysis
 * @property {number} imageWidth - Image width in pixels
 * @property {number} imageHeight - Image height in pixels
 * @property {number} mcuCols - Number of MCU columns (8×8 blocks)
 * @property {number} mcuRows - Number of MCU rows
 * @property {number} messageLengthBytes - Length of embedded message in bytes
 * @property {boolean} hasPassword - Whether the image is password-protected
 * @property {number} usedMcus - Number of MCUs containing embedded data
 * @property {number} totalCapacityBytes - Maximum message capacity in bytes
 */
const LOADER_MIN_MS = 500;

const form = document.getElementById('analyzeForm');
const submitBtn = document.getElementById('submitBtn');
const errorArea = document.getElementById('errorArea');
const results = document.getElementById('results');
const previewCanvas = document.getElementById('previewCanvas');
const previewWrapper = document.getElementById('previewWrapper');
const ctx = previewCanvas.getContext('2d');

/** @type {HTMLImageElement|null} */
let cachedImg = null;
/** @type {StegoAnalysis|null} */
let cachedData = null;

const setButtonLoading = (loading) => {
  submitBtn.disabled = loading;
  submitBtn.classList.toggle('btn-loading', loading);
};

const hideLoader = (showResults, errorHtml) => {
  const elapsed = Date.now() - (window._vizLoaderStart ?? 0);
  const remaining = Math.max(0, LOADER_MIN_MS - elapsed);
  setTimeout(() => {
    setButtonLoading(false);
    if (showResults) {
      results.style.display = 'block';
      previewWrapper.style.display = 'block';
      if (cachedImg && cachedData) renderVisualization(cachedData);
    }
    if (errorHtml) errorArea.innerHTML = errorHtml;
  }, remaining);
};

/** @param {StegoAnalysis} analysis */
const renderStats = (analysis) => {
  const totalMcus = analysis.mcuCols * analysis.mcuRows;
  document.getElementById('statDimensions').textContent =
    `${analysis.imageWidth} × ${analysis.imageHeight}`;
  document.getElementById('statMessageLen').textContent =
    `${analysis.messageLengthBytes} bytes`;
  document.getElementById('statUsedMcus').textContent =
    `${analysis.usedMcus} of ${totalMcus}`;
  document.getElementById('statCapacity').textContent =
    `${analysis.totalCapacityBytes} bytes max`;
  document.getElementById('statPassword').textContent =
    analysis.hasPassword ? 'Yes' : 'No';
};

/** @param {StegoAnalysis} analysis */
const renderVisualization = (analysis) => {
  if (!cachedImg || !analysis) return;
  const { mcuCols, mcuRows, usedMcus } = analysis;
  const img = cachedImg;

  const el = previewWrapper.closest('.card-body') ?? previewWrapper.closest('.container');
  const maxW = el?.clientWidth ?? 720;
  const baseScale = Math.min(maxW / img.width, 1);
  const w = Math.round(img.width * baseScale);
  const h = Math.round(img.height * baseScale);

  Object.assign(previewWrapper.style, {
    width: `${w}px`,
    height: `${h}px`,
    maxWidth: '100%',
  });

  previewCanvas.width = w;
  previewCanvas.height = h;
  ctx.drawImage(img, 0, 0, w, h);

  const blockW = w / mcuCols;
  const blockH = h / mcuRows;

  ctx.fillStyle = 'rgba(13, 110, 253, 0.45)';
  for (let i = 0; i < usedMcus; i++) {
    const row = Math.floor(i / mcuCols);
    const col = i % mcuCols;
    ctx.fillRect(col * blockW, row * blockH, blockW, blockH);
  }

  ctx.strokeStyle = 'rgba(0,0,0,0.2)';
  ctx.lineWidth = 0.5;
  for (let r = 0; r <= mcuRows; r++) {
    ctx.beginPath();
    ctx.moveTo(0, r * blockH);
    ctx.lineTo(w, r * blockH);
    ctx.stroke();
  }
  for (let c = 0; c <= mcuCols; c++) {
    ctx.beginPath();
    ctx.moveTo(c * blockW, 0);
    ctx.lineTo(c * blockW, h);
    ctx.stroke();
  }
};

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const fileInput = document.getElementById('stegoImage');
  const file = fileInput.files?.[0];

  if (!file) {
    errorArea.innerHTML = '<div class="alert alert-danger mb-0">Please select an image.</div>';
    return;
  }

  errorArea.innerHTML = '';
  results.style.display = 'none';
  window._vizLoaderStart = Date.now();
  setButtonLoading(true);

  const formData = new FormData();
  formData.append('stegoImage', file);

  try {
    const res = await fetch('/analyze', { method: 'POST', body: formData });
    const analysis = await res.json();

    if (!res.ok) {
      throw new Error(analysis.error ?? 'Analysis failed');
    }

    previewWrapper.style.display = 'block';
    const img = new Image();
    img.onload = () => {
      cachedImg = img;
      cachedData = analysis;
      renderVisualization(analysis);
    };
    img.src = URL.createObjectURL(file);

    renderStats(analysis);
    hideLoader(true);
  } catch (err) {
    hideLoader(false, `<div class="alert alert-danger mb-0">${err.message ?? 'Network error'}</div>`);
  }
});
