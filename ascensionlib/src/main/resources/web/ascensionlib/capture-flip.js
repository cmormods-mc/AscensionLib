/* Prism Rupture, 1.8 s. Only presentation: the server has already committed the Pokémon's profile. */
(function () {
  'use strict';
  var A = window.A;
  A.captureFlip = function (card, rotator, front) {
    var animations = [], finished = false;
    var back = A.el('div', 'capture-back'), inner = A.el('div', 'capture-back-inner');
    var crest = A.el('div', 'capture-back-crest'), icon = new Image(); icon.src = 'icons/ascension_core.png'; icon.alt = '';
    crest.appendChild(icon); inner.appendChild(crest); inner.appendChild(A.el('div', 'capture-back-brand', 'ASCENSION'));
    inner.appendChild(A.el('div', 'capture-back-label', 'A NEW BOND AWAITS')); back.appendChild(inner); back.setAttribute('aria-hidden', 'true'); rotator.appendChild(back);
    var layer = A.el('div', 'capture-flip-fx'); layer.setAttribute('aria-hidden', 'true'); card.insertBefore(layer, card.firstChild);
    var bloom = A.el('div', 'capture-flip-bloom'), shock = A.el('div', 'capture-flip-shock'); layer.appendChild(bloom); layer.appendChild(shock);
    front.inert = true;
    function done() {
      if (finished) return;
      finished = true;
      rotator.style.transform = 'none';
      animations.forEach(function (a) { a.cancel(); });
      front.inert = false;
      back.remove(); layer.remove(); card.classList.remove('capture-flipping');
    }
    function animate(el, frames, options) { var a = el.animate(frames, options); animations.push(a); return a; }
    if (document.documentElement.classList.contains('calm') || window.matchMedia('(prefers-reduced-motion: reduce)').matches || !rotator.animate) {
      done(); return { cancel: done, finished: Promise.resolve() };
    }
    card.classList.add('capture-flipping');
    rotator.style.transform = 'translateY(14px) scale(.94) rotateY(180deg) rotateZ(-4deg)'; // the first pose, so the front face is never painted before the animation starts
    var rarity = Number(card.dataset.rarityIndex) || 0, power = .25 + rarity * .15, duration = 1800;
    var main = animate(rotator, [
      { transform: 'translateY(14px) scale(.94) rotateY(180deg) rotateZ(-4deg)', offset: 0 },
      { transform: 'translateY(-14px) scale(1.02) rotateY(180deg) rotateZ(4deg)', offset: .3 },
      { transform: 'translateY(-10px) scale(1.04) rotateY(94deg)', offset: .52 },
      { transform: 'translateY(2px) scale(1.04) rotateY(-8deg)', offset: .7 },
      { transform: 'translateY(0) scale(1) rotateY(0deg)', offset: 1 }
    ], { duration: duration, easing: 'cubic-bezier(.24,.7,.25,1)', fill: 'forwards' });
    animate(bloom, [{ opacity: 0, transform: 'scale(.6)' }, { opacity: .75 * power, transform: 'scale(1.1)', offset: .45 }, { opacity: 0, transform: 'scale(1.3)' }], { duration: 1350, delay: 360, easing: 'ease-out' });
    animate(shock, [{ opacity: 0, transform: 'scale(.5) rotate(45deg)' }, { opacity: power, transform: 'scale(.8) rotate(45deg)', offset: .1 }, { opacity: 0, transform: 'scale(2.1) rotate(60deg)' }], { duration: 720, delay: 1044, easing: 'ease-out' });
    for (var i = 0; i < 28; i++) {
      var el = A.el('i', i < 10 ? 'capture-flip-shard' : 'capture-flip-spark'); layer.appendChild(el);
      var angle = i * 2.39996, distance = (230 + i % 5 * 45) * (.55 + rarity * .09);
      animate(el, [{ opacity: 0, transform: 'translate(-50%,-50%) scale(.2) rotate(' + i * 37 + 'deg)' },
        { opacity: power * (i < 10 ? .7 : 1), offset: .12 },
        { opacity: 0, transform: 'translate(' + Math.cos(angle) * distance + 'px,' + Math.sin(angle) * distance * .95 + 'px) scale(.8) rotate(' + (i * 37 + 70) + 'deg)' }],
        { duration: 540 + i % 3 * 56, delay: 1044 + i % 4 * 29, easing: 'cubic-bezier(.12,.72,.3,1)' });
    }
    return { cancel: done, finished: main.finished.then(done, done) };
  };
})();
