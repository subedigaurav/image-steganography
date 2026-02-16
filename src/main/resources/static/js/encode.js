/**
 * Encode page: capacity calculation, form validation, fetch-based download.
 */
const BITS_PER_MCU = 16;
const LOADER_MIN_MS = 600;

const form = document.getElementById('encodeForm');
const submitBtn = document.getElementById('submitBtn');
const errCont = document.getElementById('errorContainer');

let capacityBytes = null;

const setButtonLoading = (loading) => {
  submitBtn.disabled = loading;
  submitBtn.classList.toggle('btn-loading', loading);
};

const getMessageBytes = (str) => new TextEncoder().encode(str).length;

const updateMessageStatus = () => {
  const msg = document.getElementById('message').value;
  const bytes = getMessageBytes(msg);
  document.getElementById('byteCount').textContent = bytes;
  document.getElementById('capacityDisplay').textContent = capacityBytes ?? '—';
  const over = document.getElementById('overCapacity');
  const overLimit = capacityBytes != null && bytes > capacityBytes;
  over.style.display = overLimit ? 'inline' : 'none';
  if (!submitBtn.classList.contains('btn-loading')) {
    submitBtn.disabled = overLimit;
  }
};

const hideLoaderAfterMinDelay = () => {
  const elapsed = Date.now() - (window._loaderStart ?? 0);
  const remaining = Math.max(0, LOADER_MIN_MS - elapsed);
  setTimeout(() => setButtonLoading(false), remaining);
};

const showErr = (msg) => {
  errCont.innerHTML = `<div class="alert alert-danger mb-3">${msg}</div>`;
};

const resetForm = () => {
  form.reset();
  capacityBytes = null;
  document.getElementById('qualityValue').textContent = '80';
  const preview = document.getElementById('imagePreview');
  preview.src = '';
  preview.style.display = 'none';
  updateMessageStatus();
};

const triggerDownload = (blob, filename) => {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

document.getElementById('quality').addEventListener('input', (e) => {
  document.getElementById('qualityValue').textContent = e.target.value;
});

document.getElementById('message').addEventListener('input', updateMessageStatus);

document.getElementById('coverImage').addEventListener('change', (e) => {
  const file = e.target.files[0];
  const preview = document.getElementById('imagePreview');
  capacityBytes = null;
  updateMessageStatus();

  if (file?.type.startsWith('image/')) {
    const reader = new FileReader();
    reader.onload = (ev) => {
      preview.src = ev.target.result;
      preview.style.display = 'block';
      const img = new Image();
      img.onload = () => {
        const w = Math.floor(img.width / 8) * 8;
        const h = Math.floor(img.height / 8) * 8;
        capacityBytes = (w >= 8 && h >= 8) ? (w / 8) * (h / 8) * BITS_PER_MCU / 8 : 0;
        updateMessageStatus();
      };
      img.src = ev.target.result;
    };
    reader.readAsDataURL(file);
  } else {
    preview.style.display = 'none';
  }
});

form.addEventListener('submit', async (ev) => {
  ev.preventDefault();

  if (capacityBytes != null) {
    const bytes = getMessageBytes(document.getElementById('message').value);
    if (bytes > capacityBytes) {
      showErr(`Message is too long (${bytes} bytes). Max capacity: ${capacityBytes} bytes.`);
      return;
    }
  }

  errCont.innerHTML = '';
  window._loaderStart = Date.now();
  setButtonLoading(true);

  try {
    const formData = new FormData(form);
    const response = await fetch(form.action, {
      method: 'POST',
      body: formData,
    });

    const contentType = response.headers.get('content-type') || '';
    if (response.ok && contentType.includes('image/jpeg')) {
      const blob = await response.blob();
      const filename = response.headers.get('content-disposition')
        ?.match(/filename="(.+?)"/)?.[1] ?? 'stego-image.jpg';
      triggerDownload(blob, filename);
      resetForm();
    } else {
      const html = await response.text();
      const parser = new DOMParser();
      const doc = parser.parseFromString(html, 'text/html');
      const errEl = doc.querySelector('.alert-danger');
      showErr(errEl?.textContent?.trim() ?? 'Encoding failed.');
    }
  } catch (e) {
    showErr('Network error: ' + (e.message ?? 'Unknown error'));
  } finally {
    hideLoaderAfterMinDelay();
  }
});
