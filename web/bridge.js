// JavaScript halves of the native methods in ratman.WebBridge (browser version only).
// CheerpJ calls these from the Java game: sound goes through Web Audio, controllers through
// the browser's Gamepad API. Each function is named Java_<package>_<Class>_<method>.
(function () {
  "use strict";

  // ---------------------------------------------------------------- sound
  let ctx = null;
  const buffers = new Map();   // path -> Promise<AudioBuffer>
  const lastSfx = new Map();   // path -> playing source (restart a sound instead of stacking it)
  const music = { path: null, buffer: null, source: null, gain: 1, startedAt: 0, offset: 0, paused: false };

  /** Must run inside the Play button's click: browsers only allow sound after a click. */
  window.ratAudioUnlock = function () {
    if (!ctx) ctx = new (window.AudioContext || window.webkitAudioContext)();
    if (ctx.state === "suspended") ctx.resume();
    for (const p of window.RAT_SOUNDS || []) load(p); // preload so the first munch isn't late
  };

  function load(path) {
    if (!ctx) return Promise.reject(new Error("audio not unlocked"));
    if (!buffers.has(path)) {
      buffers.set(path, fetch(path)
        .then(r => { if (!r.ok) throw new Error(path + ": " + r.status); return r.arrayBuffer(); })
        .then(data => ctx.decodeAudioData(data))
        .catch(e => { console.warn("Sound failed to load", e); buffers.delete(path); throw e; }));
    }
    return buffers.get(path);
  }

  const dbToGain = db => Math.pow(10, db / 20);

  function startSource(buffer, gain, loop, offset) {
    const src = ctx.createBufferSource();
    const g = ctx.createGain();
    src.buffer = buffer;
    src.loop = loop;
    g.gain.value = gain;
    src.connect(g).connect(ctx.destination);
    src.start(0, offset || 0);
    return src;
  }

  function stopSource(src) {
    if (!src) return;
    try { src.stop(); } catch (e) { /* already stopped */ }
  }

  window.Java_ratman_WebBridge_playSound = async function (lib, path, gainDb) {
    if (!ctx) return;
    load(path).then(buf => {
      stopSource(lastSfx.get(path));
      lastSfx.set(path, startSource(buf, dbToGain(gainDb), false));
    }).catch(() => {});
  };

  window.Java_ratman_WebBridge_playMusic = async function (lib, path, gainDb) {
    if (!ctx) return;
    stopSource(music.source);
    Object.assign(music, { path, buffer: null, source: null, gain: dbToGain(gainDb), offset: 0, paused: false });
    load(path).then(buf => {
      if (music.path !== path || music.paused) return;  // changed while loading
      music.buffer = buf;
      music.startedAt = ctx.currentTime;
      music.source = startSource(buf, music.gain, true, 0);
    }).catch(() => {});
  };

  window.Java_ratman_WebBridge_stopMusic = async function () {
    stopSource(music.source);
    Object.assign(music, { path: null, buffer: null, source: null, paused: false });
  };

  window.Java_ratman_WebBridge_pauseMusic = async function () {
    if (!music.source || !music.buffer) { music.paused = true; return; }
    music.offset = (music.offset + ctx.currentTime - music.startedAt) % music.buffer.duration;
    stopSource(music.source);
    music.source = null;
    music.paused = true;
  };

  window.Java_ratman_WebBridge_resumeMusic = async function () {
    music.paused = false;
    if (!music.buffer) {                     // paused before it finished loading
      if (music.path) window.Java_ratman_WebBridge_playMusic(null, music.path, 20 * Math.log10(music.gain));
      return;
    }
    music.startedAt = ctx.currentTime;
    music.source = startSource(music.buffer, music.gain, true, music.offset);
  };

  // ---------------------------------------------------------------- controllers
  function firstPad() {
    const pads = navigator.getGamepads ? navigator.getGamepads() : [];
    let fallback = null;
    for (const p of pads) {
      if (!p || !p.connected) continue;
      if (p.mapping === "standard") return p;
      fallback = fallback || p;
    }
    return fallback;
  }

  // out is the Java float[] (shared, so we fill it in place): connected, buttons, stick x, stick y
  window.Java_ratman_WebBridge_padState = async function (lib, out) {
    const p = firstPad();
    if (!p) { out[0] = 0; out[1] = 0; out[2] = 0; out[3] = 0; return; }
    let bits = 0;
    for (let i = 0; i < Math.min(p.buttons.length, 17); i++) {
      if (p.buttons[i] && p.buttons[i].pressed) bits |= (1 << i);
    }
    out[0] = 1;
    out[1] = bits;
    out[2] = p.axes[0] || 0;
    out[3] = p.axes[1] || 0;
  };

  window.Java_ratman_WebBridge_padName = async function () {
    const p = firstPad();
    return p ? p.id : "";
  };

  window.Java_ratman_WebBridge_padRumble = async function (lib, strong, weak, millis) {
    const p = firstPad();
    try {
      if (p && p.vibrationActuator && p.vibrationActuator.playEffect) {
        p.vibrationActuator.playEffect("dual-rumble",
          { duration: millis, strongMagnitude: strong, weakMagnitude: weak });
      }
    } catch (e) { /* this browser can't rumble */ }
  };

  window.RAT_NATIVES = {};
  for (const k of Object.keys(window)) {
    if (k.startsWith("Java_ratman_WebBridge_")) window.RAT_NATIVES[k] = window[k];
  }
})();
