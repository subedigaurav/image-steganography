/**
 * Decode page: copy message, submit button loading state.
 */
const submitBtn = document.getElementById('submitBtn');
const form = document.getElementById('decodeForm');

const copyMessage = () => {
  const el = document.getElementById('extractedMessage');
  if (!el) return;
  navigator.clipboard.writeText(el.textContent).then(() => {
    const btn = document.querySelector('[onclick="copyMessage()"]');
    if (btn) {
      const original = btn.textContent;
      btn.textContent = 'Copied!';
      setTimeout(() => { btn.textContent = original; }, 2000);
    }
  });
};

window.copyMessage = copyMessage;

const setButtonLoading = (loading) => {
  submitBtn.disabled = loading;
  submitBtn.classList.toggle('btn-loading', loading);
};

form.addEventListener('submit', () => setButtonLoading(true));
