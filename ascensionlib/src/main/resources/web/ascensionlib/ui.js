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
  /** The tablet's shell colour follows the Pokemon's rarity (none: the classic red). Hard-edged 9-slice, stepped corners. */
  var SHELLS = {
    common: ['#6c7585', '#aeb6c4', '#3f4755'], uncommon: ['#14877a', '#4fd3c1', '#0a4a42'], rare: ['#1d6db0', '#5bb7ee', '#0f3f6e'],
    epic: ['#6a35b8', '#af69ff', '#341a66'], legendary: ['#b88a12', '#ffd437', '#6b4f08'], mythical: ['#8e1426', '#d0404f', '#620512']
  };
  A.setBezel = function (rarityId) {
    var c = SHELLS[rarityId] || SHELLS.mythical;
    var cols = ['#1b120d', c[1], c[0], c[0], c[2], '#1b120d', '#cbd3df', '#eef2f7'], parts = '';
    cols.forEach(function (col, i) {
      var a = i + 0.5, k = Math.max(0, 3 - i);
      var p = [[a + k, a], [24 - a - k, a], [24 - a, a + k], [24 - a, 24 - a - k], [24 - a - k, 24 - a], [a + k, 24 - a], [a, 24 - a - k], [a, a + k]];
      parts += "<path d='M" + p.map(function (q) { return q[0] + ',' + q[1]; }).join(' L') + " Z' fill='none' stroke='" + col + "' stroke-width='1'/>";
    });
    var svg = "<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' shape-rendering='crispEdges'>" + parts + '</svg>';
    document.documentElement.style.setProperty('--bezel', 'url("data:image/svg+xml,' + encodeURIComponent(svg).replace(/'/g, '%27') + '")');
  };
  A.setBezel('');
  A.newId = function () {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) { var r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 3 | 8)).toString(16); });
  };
  if (g && g.isHud) document.body.classList.add('hud');
})();
