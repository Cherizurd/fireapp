// Injected into every page so the remote's media buttons can control whatever audio the site is playing.
// Records media elements (including ones never attached to the DOM), Media Session handlers and
// AudioContexts as the page creates them.
(function () {
  if (window.__tvMedia) return;

  var elements = [];
  var contexts = [];
  var handlers = {};

  function track(el) {
    var i = elements.indexOf(el);
    if (i >= 0) elements.splice(i, 1);
    elements.push(el); // most recently used last
  }

  var proto = HTMLMediaElement.prototype;
  var originalPlay = proto.play;
  proto.play = function () {
    track(this);
    return originalPlay.apply(this, arguments);
  };
  document.addEventListener('play', function (e) {
    if (e.target instanceof HTMLMediaElement) track(e.target);
  }, true);

  var ms = navigator.mediaSession;
  if (ms && ms.setActionHandler) {
    var originalSet = ms.setActionHandler.bind(ms);
    ms.setActionHandler = function (action, handler) {
      handlers[action] = handler;
      return originalSet(action, handler);
    };
  }

  var AC = window.AudioContext || window.webkitAudioContext;
  if (AC) {
    var Tracked = class extends AC {
      constructor() {
        super(...arguments);
        contexts.push(this);
      }
    };
    window.AudioContext = Tracked;
    if (window.webkitAudioContext) window.webkitAudioContext = Tracked;
  }

  function allElements() {
    var list = elements.slice();
    document.querySelectorAll('audio,video').forEach(function (el) {
      if (list.indexOf(el) < 0) list.unshift(el);
    });
    return list;
  }

  function playingElement() {
    var list = allElements();
    for (var i = list.length - 1; i >= 0; i--) {
      if (!list[i].paused && !list[i].ended) return list[i];
    }
    return null;
  }

  function lastElement() {
    var list = allElements();
    return list.length ? list[list.length - 1] : null;
  }

  function isPlaying() {
    if (ms && ms.playbackState === 'playing') return true;
    if (ms && ms.playbackState === 'paused') return false;
    return !!playingElement();
  }

  function callHandler(action, details) {
    var h = handlers[action];
    if (!h) return false;
    var d = details || {};
    d.action = action;
    h(d);
    return true;
  }

  function clickButton(pattern) {
    var buttons = document.querySelectorAll('button,[role="button"]');
    for (var i = 0; i < buttons.length; i++) {
      var b = buttons[i];
      var label = (b.getAttribute('aria-label') || b.getAttribute('title') || '').trim();
      if (pattern.test(label) && b.offsetParent !== null && !b.disabled) {
        b.click();
        return true;
      }
    }
    return false;
  }

  function toggle() {
    if (isPlaying()) {
      if (callHandler('pause')) return 'session';
      var el = playingElement();
      if (el) { el.pause(); return 'element'; }
      if (clickButton(/^pause\b/i)) return 'button';
      var running = contexts.filter(function (c) { return c.state === 'running'; });
      if (running.length) { running.forEach(function (c) { c.suspend(); }); return 'context'; }
    } else {
      if (callHandler('play')) return 'session';
      var last = lastElement();
      if (last) { last.play(); return 'element'; }
      if (clickButton(/^(play|resume)\b/i)) return 'button';
      var suspended = contexts.filter(function (c) { return c.state === 'suspended'; });
      if (suspended.length) { suspended.forEach(function (c) { c.resume(); }); return 'context'; }
    }
    return 'none';
  }

  function seek(seconds) {
    var action = seconds > 0 ? 'seekforward' : 'seekbackward';
    if (callHandler(action, { seekOffset: Math.abs(seconds) })) return 'session';
    var el = playingElement() || lastElement();
    if (el && isFinite(el.currentTime)) {
      var end = isFinite(el.duration) ? el.duration : el.currentTime + Math.abs(seconds);
      el.currentTime = Math.max(0, Math.min(end, el.currentTime + seconds));
      return 'element';
    }
    var pattern = seconds > 0 ? /(forward|ahead|skip\s*\d*\s*s)/i : /(back|rewind|replay)/i;
    if (clickButton(pattern)) return 'button';
    return 'none';
  }

  window.__tvMedia = {
    command: function (name) {
      try {
        if (name === 'toggle') return toggle();
        if (name === 'play') return isPlaying() ? 'session' : toggle();
        if (name === 'pause') return isPlaying() ? toggle() : 'session';
        if (name === 'forward') return seek(15);
        if (name === 'back') return seek(-15);
      } catch (e) {
        return 'error: ' + e;
      }
      return 'none';
    }
  };
})();
