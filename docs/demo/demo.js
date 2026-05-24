// Minimal interactive 88-key piano demo. Click a key or press Play to hear a C-major scale.
// Visual model mirrors the real PianoLearner app: yellow = expected, green = heard.

(() => {
  const canvas = document.getElementById('piano-canvas');
  const ctx = canvas.getContext('2d');
  const status = document.getElementById('status');
  const playBtn = document.getElementById('play-scale');
  const stopBtn = document.getElementById('stop');

  // Piano: 88 keys, A0 (MIDI 21) to C8 (MIDI 108). 52 white keys, 36 black keys.
  const FIRST_MIDI = 21;
  const LAST_MIDI = 108;
  const WHITE_KEY_COUNT = 52;
  const WHITE_PCS = new Set([0, 2, 4, 5, 7, 9, 11]);
  const NAMES = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];

  const isWhite = (midi) => WHITE_PCS.has(((midi % 12) + 12) % 12);
  const noteName = (midi) => NAMES[((midi % 12) + 12) % 12] + (Math.floor(midi / 12) - 1);

  // Precompute "whites-before" lookup so per-frame redraws are O(1) per key.
  const WHITES_BEFORE = new Int16Array(129);
  {
    let c = 0;
    for (let m = 0; m <= 128; m++) {
      WHITES_BEFORE[m] = c;
      if (m >= FIRST_MIDI && m <= LAST_MIDI && isWhite(m)) c++;
    }
  }
  const whitesBefore = (midi) => WHITES_BEFORE[Math.max(0, Math.min(128, midi))];

  // State: which keys are visually held (by note → release timestamp ms)
  const held = new Map();          // midi -> {color, releaseAt}

  // ---- audio ----
  let audio = null;
  function getAudio() {
    if (!audio) audio = new (window.AudioContext || window.webkitAudioContext)();
    return audio;
  }
  function midiToHz(midi) { return 440 * Math.pow(2, (midi - 69) / 12); }

  function playNote(midi, durationSec = 0.4, color = '#fbbf24') {
    const ac = getAudio();
    const now = ac.currentTime;
    const osc = ac.createOscillator();
    const gain = ac.createGain();
    osc.type = 'sine';
    osc.frequency.value = midiToHz(midi);
    // tiny attack + decay so it doesn't click
    gain.gain.setValueAtTime(0, now);
    gain.gain.linearRampToValueAtTime(0.25, now + 0.01);
    gain.gain.setTargetAtTime(0, now + durationSec * 0.6, 0.08);
    osc.connect(gain).connect(ac.destination);
    osc.start(now);
    osc.stop(now + durationSec + 0.3);

    held.set(midi, { color, releaseAt: performance.now() + durationSec * 1000 });
    needsRedraw = true;
  }

  // ---- layout / drawing ----
  let dpr = window.devicePixelRatio || 1;
  function resize() {
    const r = canvas.getBoundingClientRect();
    dpr = window.devicePixelRatio || 1;
    canvas.width = Math.floor(r.width * dpr);
    canvas.height = Math.floor(r.height * dpr);
    needsRedraw = true;
  }
  window.addEventListener('resize', resize);

  function whiteKeyRect(midi, w, h) {
    const whiteW = w / WHITE_KEY_COUNT;
    const idx = whitesBefore(midi);
    return { x: idx * whiteW, y: 0, w: whiteW, h };
  }
  function blackKeyRect(midi, w, h) {
    const whiteW = w / WHITE_KEY_COUNT;
    const blackW = whiteW * 0.6;
    const blackH = h * 0.6;
    const cx = whitesBefore(midi) * whiteW; // boundary between adjacent whites = center of black
    return { x: cx - blackW / 2, y: 0, w: blackW, h: blackH };
  }

  function draw() {
    needsRedraw = false;
    const w = canvas.width;
    const h = canvas.height;

    // background (falling-notes lane area; for this demo we just show pressed-key indicators)
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, w, h);

    // keyboard takes the bottom 40% of the canvas
    const kbH = h * 0.4;
    const kbY = h - kbH;

    // currently-held key colors (decay 'held' map)
    const now = performance.now();
    for (const [midi, info] of held) {
      if (info.releaseAt <= now) held.delete(midi);
    }

    // 1) above-keyboard region: show "falling note" rectangles approaching the keys
    //    for each held note, draw a vertical column above the corresponding key
    for (const [midi, info] of held) {
      const r = isWhite(midi) ? whiteKeyRect(midi, w, kbH) : blackKeyRect(midi, w, kbH);
      const cx = r.x + r.w / 2;
      // a "trail" pulse — alpha decays
      const remaining = (info.releaseAt - now) / 400; // ~normalize
      const a = Math.max(0, Math.min(1, remaining));
      ctx.fillStyle = info.color + Math.floor(a * 200 + 30).toString(16).padStart(2, '0');
      ctx.fillRect(cx - r.w * 0.45, kbY - 60 * (1 - a), r.w * 0.9, 60 * (1 - a));
    }

    // 2) keyboard — draw white keys first
    for (let m = FIRST_MIDI; m <= LAST_MIDI; m++) {
      if (!isWhite(m)) continue;
      const r = whiteKeyRect(m, w, kbH);
      const heldHere = held.has(m);
      ctx.fillStyle = heldHere ? held.get(m).color : '#fafafa';
      ctx.fillRect(r.x, kbY, r.w, r.h);
      ctx.strokeStyle = '#222';
      ctx.lineWidth = 1 * dpr;
      ctx.strokeRect(r.x + 0.5, kbY + 0.5, r.w - 1, r.h - 1);
      // label C notes for orientation
      if (m % 12 === 0) {
        ctx.fillStyle = '#888';
        ctx.font = `${12 * dpr}px ui-monospace, monospace`;
        ctx.textAlign = 'center';
        ctx.fillText(noteName(m), r.x + r.w / 2, kbY + r.h - 6 * dpr);
      }
    }
    // 3) black keys on top
    for (let m = FIRST_MIDI; m <= LAST_MIDI; m++) {
      if (isWhite(m)) continue;
      const r = blackKeyRect(m, w, kbH);
      const heldHere = held.has(m);
      ctx.fillStyle = heldHere ? darkenColor(held.get(m).color) : '#111';
      ctx.fillRect(r.x, kbY, r.w, r.h);
    }
  }

  function darkenColor(hex) {
    // shift hex by -30 lightness
    if (!hex || hex[0] !== '#') return '#444';
    const n = parseInt(hex.slice(1, 7), 16);
    const r = Math.max(0, ((n >> 16) & 0xff) - 60);
    const g = Math.max(0, ((n >> 8) & 0xff) - 60);
    const b = Math.max(0, (n & 0xff) - 60);
    return '#' + ((r << 16) | (g << 8) | b).toString(16).padStart(6, '0');
  }

  // ---- click handling ----
  canvas.addEventListener('pointerdown', (ev) => {
    const r = canvas.getBoundingClientRect();
    const cx = (ev.clientX - r.left) * dpr;
    const cy = (ev.clientY - r.top) * dpr;
    const kbH = canvas.height * 0.4;
    const kbY = canvas.height - kbH;
    if (cy < kbY) return;
    // hit-test: black keys first (they overlap whites)
    for (let m = FIRST_MIDI; m <= LAST_MIDI; m++) {
      if (isWhite(m)) continue;
      const rr = blackKeyRect(m, canvas.width, kbH);
      if (cx >= rr.x && cx < rr.x + rr.w && cy >= kbY && cy < kbY + rr.h) {
        playNote(m, 0.5, '#66bb6a');
        status.textContent = `Played ${noteName(m)} (MIDI ${m})`;
        return;
      }
    }
    for (let m = FIRST_MIDI; m <= LAST_MIDI; m++) {
      if (!isWhite(m)) continue;
      const rr = whiteKeyRect(m, canvas.width, kbH);
      if (cx >= rr.x && cx < rr.x + rr.w && cy >= kbY && cy < kbY + kbH) {
        playNote(m, 0.5, '#66bb6a');
        status.textContent = `Played ${noteName(m)} (MIDI ${m})`;
        return;
      }
    }
  });

  // ---- scale playback ----
  let scaleTimer = null;
  playBtn.addEventListener('click', () => {
    stopScale();
    const cmajor = [60, 62, 64, 65, 67, 69, 71, 72]; // C4..C5
    let i = 0;
    status.textContent = 'Playing C-major scale (yellow = "expected", green = "heard").';
    // first the yellow "this is the next note" pulse, then the green "you played it" pulse
    function step() {
      if (i >= cmajor.length) {
        status.textContent = 'Done. Click any key to play it.';
        scaleTimer = null;
        return;
      }
      const m = cmajor[i++];
      // expected (yellow) on the keyboard for ~150ms
      held.set(m, { color: '#fbbf24', releaseAt: performance.now() + 200 });
      needsRedraw = true;
      setTimeout(() => playNote(m, 0.35, '#66bb6a'), 180);
      scaleTimer = setTimeout(step, 480);
    }
    step();
  });
  function stopScale() {
    if (scaleTimer) { clearTimeout(scaleTimer); scaleTimer = null; }
  }
  stopBtn.addEventListener('click', () => {
    stopScale();
    held.clear();
    needsRedraw = true;
    status.textContent = 'Stopped. Click any key to play it.';
  });

  // ---- animation loop ----
  let needsRedraw = true;
  function loop() {
    if (needsRedraw || held.size > 0) {
      draw();
      if (held.size > 0) needsRedraw = true;
    }
    requestAnimationFrame(loop);
  }
  resize();
  loop();
})();
