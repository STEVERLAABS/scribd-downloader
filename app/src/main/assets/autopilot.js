/*
 * Scribd Premium Downloader — steVeR Edition (Android port)
 * autopilot.js — port of background.js/injectAutopilot (v2.8.0), adapted for
 * an in-app WebView (no chrome.* APIs; no injected HUD; events posted to
 * window.SteVeRBridge.post(json)).
 *
 * Message vocabulary (JS -> Kotlin):
 *   {"event":"status","step":1..4,"key": "init|verify|verify_manual|verify_done|wait|redirect|analyzing|validating|success"}
 *   {"event":"captcha"}   // turnstile visible on page
 *   {"event":"pdf","url":"..."}        // direct PDF link found and looks like a PDF
 *   {"event":"failed","key":"error_detected|error_damaged"}
 *   {"event":"timeout","key":"error_timeout|wait_timeout|captcha_timeout"}
 *
 * Injection contract: before this script runs, the host sets
 *   window.__spdPaste  = the document URL to paste into the service form
 *   window.__spdCancel = true to stop every pending loop
 * The host re-injects this script on every onPageFinished (fresh document =>
 * guard is undefined => engine starts). On an already-initialized document,
 * window.__spdEngineV1 prevents stacking duplicate intervals.
 *
 * Rules kept 1:1 from the extension: same selectors, same tick delays, same
 * attempt budgets, same phase priority (ilide -> wait -> form -> download).
 * Cloudflare Turnstile is NEVER solved automatically: when visible, the app
 * shows the engine so the user can tap the checkbox; the flow then resumes.
 */

(function () {
  'use strict';
  if (window.__spdEngineV1) { return; }
  window.__spdEngineV1 = true;

  var MAX_ILIDE_ATTEMPTS = 45;    // 45 x 1000ms = 45s
  var MAX_WAIT_TICKS = 180;       // 180 x 500ms = 90s
  var MAX_CAPTCHA_TICKS = Math.ceil((180 * 1000) / 800); // 180s watchdog at 800ms ticks
  var MAX_CF_TICKS_BEFORE_MANUAL = 56;  // ~45s before the "action required" notice

  var bridge = null;
  try { bridge = window.SteVeRBridge; } catch (e) { bridge = null; }

  function post(obj) {
    try { if (bridge && bridge.post) { bridge.post(JSON.stringify(obj)); } } catch (e) { /* never break the page */ }
  }
  function status(step, key) { post({ event: 'status', step: step, key: key }); }
  function failed(key) { post({ event: 'failed', key: key }); }
  function timeout(key) { post({ event: 'timeout', key: key }); }

  function isCanceled() {
    try { return window.__spdCancel === true; } catch (e) { return false; }
  }
  function startLoop(fn, delay, onDone) {
    var ticks = 0;
    var id = setInterval(function () {
      if (isCanceled()) { clearInterval(id); if (onDone) { onDone('canceled'); } return; }
      var verdict = fn(ticks++);
      if (verdict === 'stop') { clearInterval(id); if (onDone) { onDone('done'); } return; }
      if (verdict === 'stop-no-done') { clearInterval(id); return; }
    }, delay);
    return id;
  }

  function visibleErrorBlock() {
    var el = document.querySelector('#errorContainer, .error-container, [class*="error"]');
    if (el && el.offsetParent !== null && el.innerText && el.innerText.trim()) { return el; }
    return null;
  }

  function looksPdf(url) {
    return /\.pdf(\?|$)/i.test(url) || url.indexOf('download') !== -1;
  }

  function statusNow() {
    try { return (document.body && document.body.innerText) || ''; } catch (e) { return ''; }
  }

  // ── Phase: ilide.info final viewer ─────────────────────────────────────────
  function runIlidePhase() {
    status(3, 'analyzing');
    startLoop(function (attempts) {
      var err = visibleErrorBlock();
      if (err) { failed('error_detected'); return 'stop'; }

      var viewerFrame = document.querySelector('iframe[src*="viewer.html"]');
      if (viewerFrame) {
        try {
          var fileParam = new URL(viewerFrame.src).searchParams.get('file');
          if (fileParam) {
            var directPdfUrl = decodeURIComponent(fileParam);
            if (!looksPdf(directPdfUrl)) { failed('error_damaged'); return 'stop'; }
            status(3, 'validating');   // HEAD validation happens natively (Kotlin), then DownloadManager
            post({ event: 'pdf', url: directPdfUrl });
            return 'stop';
          }
        } catch (e) { /* malformed URL — keep waiting */ }
      }

      if (attempts >= MAX_ILIDE_ATTEMPTS) { timeout('error_timeout'); return 'stop-no-done'; }
      return 'continue';
    }, 1000);
  }

  // ── Phase: vdownloaders "please wait" page ────────────────────────────────
  function runWaitPhase() {
    status(3, 'wait');
    startLoop(function (ticks) {
      var link = document.getElementById('btn-download');
      if (link && link.href && link.href.indexOf('ilide') !== -1) {
        status(3, 'redirect');
        link.target = '_self';
        link.removeAttribute('rel');
        window.location.href = link.href;
        return 'stop';
      }
      if (ticks >= MAX_WAIT_TICKS) { timeout('wait_timeout'); return 'stop'; }
      return 'continue';
    }, 500);
  }

  // ── Phase: service form (fills + submits) ─────────────────────────────────
  function runFormPhase() {
    status(1, 'init');
    var pasteUrl = (window.__spdPaste || '') + '';
    var input = document.getElementById('url') || document.querySelector('input[name="url"]');
    var stage1Btn = document.querySelector('button[type="submit"].btn-primary');
    if (!input || !stage1Btn) { return; }

    setTimeout(function () {
      if (isCanceled()) { return; }
      try { input.focus(); } catch (e) { }
      try { input.click(); } catch (e) { }
      try {
        Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, pasteUrl);
      } catch (e) { input.value = pasteUrl; }
      try { input.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) { }
      setTimeout(function () {
        if (isCanceled()) { return; }
        try { stage1Btn.click(); } catch (e) { }
      }, 500);
    }, 800);
  }

  // ── Phase: turnstile challenge on a page that still has the form ──────────
  // Original extension deadlocked here by design; the app chains instead:
  // user completes the captcha, THEN the form phase re-runs (Task 7 delta 3).
  function runCaptchaBeforeFormPhase() {
    post({ event: 'captcha' });
    status(2, 'verify_manual');
    startLoop(function (ticks) {
      var resolved = document.querySelector('[name="cf-turnstile-response"]');
      if (resolved && resolved.value) { return 'stop'; }   // resolved: chain to form phase
      if (ticks >= MAX_CAPTCHA_TICKS) { timeout('captcha_timeout'); return 'stop'; }
      return 'continue';
    }, 800, function (why) {
      if (why === 'done') {
        status(2, 'verify_done');
        setTimeout(function () { if (!isCanceled()) { runFormPhase(); } }, 1000);
      }
    });
  }

  // ── Phase: download button page (+ turnstile watching) ────────────────────
  function runDownloadPhase() {
    var downloadBtn = document.querySelector('button.btn-primary');
    var input = document.getElementById('url') || document.querySelector('input[name="url"]');
    if (!downloadBtn || input) { return; }

    var cloudflareBox = document.querySelector('.cf-turnstile');
    if (cloudflareBox && cloudflareBox.offsetParent !== null) {
      post({ event: 'captcha' });
      status(2, 'verify');
      var manualShown = false;
      startLoop(function (ticks) {
        var responseInput = document.querySelector('[name="cf-turnstile-response"]');
        if (responseInput && responseInput.value) {
          status(2, 'verify_done');
          setTimeout(function () { if (!isCanceled()) { try { downloadBtn.click(); } catch (e) { } } }, 500);
          return 'stop';
        }
        if (ticks >= MAX_CF_TICKS_BEFORE_MANUAL && !manualShown) {
          manualShown = true;
          status(2, 'verify_manual');   // user completes it; loop keeps watching (as in the extension)
        }
        if (ticks >= MAX_CAPTCHA_TICKS) { timeout('captcha_timeout'); return 'stop'; }
        return 'continue';
      }, 800);
    } else {
      status(2, 'verify_done');
      setTimeout(function () { if (!isCanceled()) { try { downloadBtn.click(); } catch (e) { } } }, 1000);
    }
  }

  function bottomTextHas(patterns) {
    var t = statusNow();
    for (var i = 0; i < patterns.length; i++) {
      if (t.indexOf(patterns[i]) !== -1) { return true; }
    }
    return false;
  }

  function dispatch() {
    var href = window.location.href || '';

    // Phase 1 (same priority order as injectAutopilot).
    if (href.indexOf('ilide') !== -1) { runIlidePhase(); return; }

    var waitPage = bottomTextHas(['Please wait a moment', 'ready in']);
    var finalLink = document.getElementById('btn-download');
    if (waitPage || (finalLink && finalLink.href && finalLink.href.indexOf('ilide') !== -1)) { runWaitPhase(); return; }

    var formInput = document.getElementById('url') || document.querySelector('input[name="url"]');
    var stage1Btn = document.querySelector('button[type="submit"].btn-primary');

    if (formInput && stage1Btn) {
      var turnstileBox = document.querySelector('.cf-turnstile');
      if (turnstileBox && turnstileBox.offsetParent !== null) {
        runCaptchaBeforeFormPhase();     // Task 7 delta 3: chain instead of deadlock
      } else {
        runFormPhase();
      }
      return;
    }

    // Phase 2: download button (+ Cloudflare watch).
    runDownloadPhase();
  }

  // Sortie après un léger délai : laisse le DOM se stabiliser, comme l'injection
  // post-load de l'extension (chrome.tabs.onUpdated status=complete).
  setTimeout(function () {
    if (isCanceled()) { return; }
    dispatch();
  }, 400);
})();
