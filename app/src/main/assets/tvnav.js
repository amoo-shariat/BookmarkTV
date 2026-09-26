/*
 * Generic D-pad spatial navigation for arbitrary web pages.
 * Injected into every page loaded in BrowserActivity's WebView.
 *
 * Exposes window.__tvNavigate(direction) and window.__tvActivate(),
 * called from the native Activity in response to remote key events.
 * Also reports focus changes and any <video> elements found on the
 * page back to Android via the TvBridge JS interface, as a
 * best-effort fallback alongside native network-request sniffing.
 */
(function () {
  if (window.__tvNavInstalled) return;
  window.__tvNavInstalled = true;

  var FOCUSABLE_SELECTOR =
    'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"]), video, [role="button"], [role="link"]';

  function visible(el) {
    var rect = el.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return false;
    var style = window.getComputedStyle(el);
    return style.visibility !== 'hidden' && style.display !== 'none';
  }

  function focusableElements() {
    return Array.prototype.slice
      .call(document.querySelectorAll(FOCUSABLE_SELECTOR))
      .filter(visible);
  }

  function currentRect() {
    var active = document.activeElement;
    if (active && active !== document.body) {
      return active.getBoundingClientRect();
    }
    // Nothing focused yet: start from the top-left-most focusable element.
    return { top: 0, left: 0, bottom: 0, right: 0 };
  }

  // Injects a visible focus outline since many sites suppress the default one.
  function ensureFocusStyle() {
    if (document.getElementById('__tvnav_style')) return;
    var style = document.createElement('style');
    style.id = '__tvnav_style';
    style.innerHTML =
      ':focus { outline: 4px solid #4FC3F7 !important; outline-offset: 2px !important; }';
    document.head.appendChild(style);
  }

  window.__tvNavigate = function (direction) {
    ensureFocusStyle();
    var candidates = focusableElements();
    if (candidates.length === 0) return;

    var from = currentRect();
    var best = null;
    var bestScore = Infinity;

    candidates.forEach(function (el) {
      if (el === document.activeElement) return;
      var r = el.getBoundingClientRect();
      var dx = r.left - from.left;
      var dy = r.top - from.top;
      var primary, ok;

      if (direction === 'right') { ok = dx > 0; primary = dx; }
      else if (direction === 'left') { ok = dx < 0; primary = -dx; }
      else if (direction === 'down') { ok = dy > 0; primary = dy; }
      else if (direction === 'up') { ok = dy < 0; primary = -dy; }
      else { ok = false; }

      if (!ok) return;
      var secondary = direction === 'left' || direction === 'right'
        ? Math.abs(dy)
        : Math.abs(dx);
      var score = primary + secondary * 2;
      if (score < bestScore) {
        bestScore = score;
        best = el;
      }
    });

    if (best) {
      best.focus();
      best.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
      if (window.TvBridge) window.TvBridge.onFocusChanged(best.tagName, best.href || best.src || '');
    }
  };

  window.__tvActivate = function () {
    var active = document.activeElement;
    if (!active || active === document.body) return 'none';
    if (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA') return 'input';
    active.click();
    return 'clicked';
  };

  // Fallback: periodically report any <video> element's resolved src so the
  // native side can show status even when network sniffing hasn't matched
  // a manifest yet (e.g. the player briefly used a blob: URL).
  setInterval(function () {
    var videos = document.querySelectorAll('video');
    if (videos.length > 0 && window.TvBridge) {
      var v = videos[0];
      window.TvBridge.onVideoElementFound(v.currentSrc || v.src || '');
    }
  }, 2000);

  ensureFocusStyle();
  // Focus the first candidate so the user immediately sees where they are.
  var first = focusableElements()[0];
  if (first) first.focus();
})();
