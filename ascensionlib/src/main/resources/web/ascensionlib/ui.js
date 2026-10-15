/* Shared helpers for AscensionLib's pages. Server text is only ever put in with textContent, never as HTML. */
(function () {
  'use strict';
  var g = window.webgui;
  var A = window.A = {};
  A.g = g;
  A.$ = function (id) { return document.getElementById(id); };
  A.send = function (channel, body) { if (!g) return; var o = body || {}; o.channel = channel; g.postToGame(o); };
  A.on = function (event, fn) {
    if (!g) return;
    g.on(event, function (data) { try { fn(typeof data === 'string' ? JSON.parse(data) : data); } catch (e) { if (window.console) console.error(event, e); } });
  };
  A.text = function (el, value) { el.textContent = value == null ? '' : String(value); };
  A.cap = function (s) { return s ? s.charAt(0).toUpperCase() + s.slice(1).toLowerCase() : ''; };
  A.rarityName = function (id) { return id ? (id === 'mythical' ? 'Mythic' : A.cap(id)) : ''; };
  A.roman = ['', 'I', 'II', 'III', 'IV', 'V'];
  A.num = function (n) { n = Number(n) || 0; return n >= 10000000 ? (n / 1000000).toFixed(1) + 'M' : n.toLocaleString('en-US'); };
  A.speciesPath = function (speciesId) { var i = (speciesId || '').indexOf(':'); return i >= 0 ? speciesId.slice(i + 1) : (speciesId || ''); };
  A.speciesName = function (speciesId) { return A.speciesPath(speciesId).replace(/_/g, ' '); };
  /** An element: tag, class, text. */
  A.el = function (tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };
  var sprites = {}, waiting = {};
  /** Pictures arrive from the server as data URLs, in batches; an image waits for its species and shows the name until then. */
  A.on('sprites', function (batch) {
    Object.keys(batch).forEach(function (path) {
      sprites[path] = batch[path];
      (waiting[path] || []).forEach(function (fill) { fill(batch[path]); });
      delete waiting[path];
    });
  });
  /** The species' card sprite (from CobblemonCards on the server), or its name when the server has none. */
  A.sprite = function (speciesId, size) {
    var box = A.el('div', 'sprbox');
    var path = A.speciesPath(speciesId);
    var label = function () { box.innerHTML = ''; box.appendChild(A.el('div', 'sprfb', A.speciesName(speciesId))); };
    label();
    if (!/^[a-z0-9_]+$/.test(path)) return box;
    var fill = function (url) {
      var img = new Image();
      img.className = 'spr'; img.alt = '';
      img.style.width = (48 * (size || 2)) + 'px'; img.style.height = (32 * (size || 2)) + 'px';
      img.onload = function () { box.innerHTML = ''; box.appendChild(img); };
      img.src = url;
    };
    if (sprites[path]) fill(sprites[path]);
    else (waiting[path] = waiting[path] || []).push(fill);
    return box;
  };
  A.newId = function () {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) { var r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 3 | 8)).toString(16); });
  };
  if (g && g.isHud) document.body.classList.add('hud');
})();
