// Synthesised sound effects (no audio files). playFx(name) returns the effect length in ms.
// Everything runs through a soft room reverb and a limiter so the chimes sound clean, not beepy.
let ac = null, out = null, wet = null;
function audio() {
  if (!ac) {
    ac = new (window.AudioContext || window.webkitAudioContext)();
    const comp = ac.createDynamicsCompressor();
    comp.threshold.value = -14; comp.ratio.value = 4;
    comp.connect(ac.destination);
    out = ac.createGain(); out.gain.value = 0.9; out.connect(comp);
    const verb = ac.createConvolver(), len = ac.sampleRate * 1.6, ir = ac.createBuffer(2, len, ac.sampleRate);
    for (let ch = 0; ch < 2; ch++) {
      const d = ir.getChannelData(ch);
      for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3.5);
    }
    verb.buffer = ir;
    wet = ac.createGain(); wet.gain.value = 0.28;
    wet.connect(verb).connect(comp);
  }
  if (ac.state === "suspended") ac.resume();
  return ac;
}
// While dance music plays, notes go through a bus that can be muted at once when the dance stops early.
let musicBus = null;
function startMusicBus() {
  audio();
  musicBus = ac.createGain();
  musicBus.connect(out); musicBus.connect(wet);
}
function stopMusicBus() {
  if (!musicBus) return;
  const b = musicBus; musicBus = null;
  b.gain.setTargetAtTime(0, ac.currentTime, 0.04);
  setTimeout(() => b.disconnect(), 400);
}
function route(node) { if (musicBus) node.connect(musicBus); else { node.connect(out); node.connect(wet); } }
// A glassy bell: a few inharmonic partials with fast attack and long exponential decay.
function bell(freq, t, vol = 0.18, dur = 0.9, bend = 0) {
  const c = audio(), s = c.currentTime + t;
  [[1, 1], [2.01, 0.35], [3.02, 0.12], [4.17, 0.05]].forEach(([mult, amp]) => {
    const o = c.createOscillator(), g = c.createGain();
    o.type = "sine";
    o.frequency.setValueAtTime(freq * mult, s);
    if (bend) o.frequency.exponentialRampToValueAtTime(freq * mult * bend, s + 0.12);
    const d = dur / Math.sqrt(mult);
    g.gain.setValueAtTime(0.0001, s);
    g.gain.exponentialRampToValueAtTime(vol * amp, s + 0.006);
    g.gain.exponentialRampToValueAtTime(0.0001, s + d);
    o.connect(g); route(g);
    o.start(s); o.stop(s + d + 0.05);
  });
}
function glide(f0, f1, t, dur, vol = 0.12) {
  const c = audio(), s = c.currentTime + t, o = c.createOscillator(), g = c.createGain(), lfo = c.createOscillator(), lg = c.createGain();
  o.type = "sine"; o.frequency.setValueAtTime(f0, s); o.frequency.exponentialRampToValueAtTime(f1, s + dur);
  lfo.frequency.value = 7; lg.gain.value = f1 * 0.02; lfo.connect(lg).connect(o.frequency);
  g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(vol, s + 0.05); g.gain.exponentialRampToValueAtTime(0.0001, s + dur + 0.15);
  o.connect(g); route(g);
  o.start(s); lfo.start(s); o.stop(s + dur + 0.2); lfo.stop(s + dur + 0.2);
}
function clap(t, vol) {
  const c = audio(), s = c.currentTime + t, len = Math.floor(c.sampleRate * 0.12), buf = c.createBuffer(1, len, c.sampleRate), d = buf.getChannelData(0);
  for (let i = 0; i < len; i++) {
    const env = i < c.sampleRate * 0.03 ? Math.exp(-((i % (c.sampleRate * 0.01)) / (c.sampleRate * 0.0025))) : Math.exp(-(i - c.sampleRate * 0.03) / (c.sampleRate * 0.02));
    d[i] = (Math.random() * 2 - 1) * env;
  }
  const src = c.createBufferSource(), hp = c.createBiquadFilter(), bp = c.createBiquadFilter(), g = c.createGain();
  src.buffer = buf; hp.type = "highpass"; hp.frequency.value = 700;
  bp.type = "peaking"; bp.frequency.value = 1100 + Math.random() * 900; bp.gain.value = 8;
  g.gain.value = vol;
  src.connect(hp).connect(bp).connect(g); route(g);
  src.start(s);
}
const NOTE = (n) => 440 * Math.pow(2, (n - 69) / 12); // MIDI note -> Hz
// Each effect plays and returns [durationMs, face reaction].
const FX = {
  kikir() { [91, 88, 86, 84, 81, 79].forEach((n, i) => bell(NOTE(n), i * 0.075, 0.13, 0.35, 0.97)); return [750, "fx-giggle"]; },
  alkis() {
    for (let i = 0; i < 18; i++) clap(Math.random() * 1.1, 0.5 * (1 - i / 22));
    [84, 88, 91].forEach((n, i) => bell(NOTE(n), 0.15 + i * 0.09, 0.08, 1.0));
    return [1300, "fx-happy"];
  },
  yasasin() {
    [72, 76, 79, 84].forEach((n, i) => bell(NOTE(n), i * 0.09, 0.17, 1.1));
    bell(NOTE(88), 0.38, 0.14, 1.4);
    for (let i = 0; i < 6; i++) bell(NOTE(96 + Math.floor(Math.random() * 8)), 0.45 + i * 0.06, 0.04, 0.5);
    return [1100, "fx-happy"];
  },
  vay() { glide(392, 988, 0, 0.45); bell(NOTE(91), 0.45, 0.1, 1.0); return [900, "fx-wow"]; },
  bipbip() { [[84, 0], [91, 0.11], [88, 0.22], [96, 0.33]].forEach(([n, t]) => bell(NOTE(n), t, 0.1, 0.22, 1.06)); return [650, ""]; },
  hmm() { bell(NOTE(64), 0, 0.12, 0.6, 1.03); bell(NOTE(62), 0.28, 0.12, 0.8, 0.98); return [800, ""]; }
};
const FX_ALIASES = { "kıkır": "kikir", "kikir": "kikir", "kıkırdama": "kikir", "kıkırdar": "kikir", "güldü": "kikir", "gülme": "kikir", "hihi": "kikir",
  "alkış": "alkis", "alkis": "alkis", "tebrik": "alkis", "yaşasın": "yasasin", "yasasin": "yasasin", "sevinç": "yasasin",
  "aferin": "alkis", "vay": "vay", "vay be": "vay", "vaov": "vay", "şaşırma": "vay", "bipbip": "bipbip", "bip bip": "bipbip", "bip": "bipbip", "hmm": "hmm", "hımm": "hmm" };
function playFx(name) {
  const [ms, face] = FX[name]();
  if (face) { document.body.classList.add(face); setTimeout(() => document.body.classList.remove(face), ms); }
  return ms;
}


// ---------- party instruments (for the dance melodies) ----------
function kick(t, vol = 0.5) {
  const c = audio(), s = c.currentTime + t, o = c.createOscillator(), g = c.createGain();
  o.type = "sine"; o.frequency.setValueAtTime(140, s); o.frequency.exponentialRampToValueAtTime(42, s + 0.14);
  g.gain.setValueAtTime(vol, s); g.gain.exponentialRampToValueAtTime(0.0001, s + 0.22);
  o.connect(g).connect(musicBus || out); o.start(s); o.stop(s + 0.25);
}
function hat(t, vol = 0.08) {
  const c = audio(), s = c.currentTime + t, len = Math.floor(c.sampleRate * 0.05), buf = c.createBuffer(1, len, c.sampleRate), d = buf.getChannelData(0);
  for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 4);
  const src = c.createBufferSource(), hp = c.createBiquadFilter(), g = c.createGain();
  src.buffer = buf; hp.type = "highpass"; hp.frequency.value = 7000; g.gain.value = vol;
  src.connect(hp).connect(g).connect(musicBus || out); src.start(s);
}
// Short wooden "tok" like a marimba.
function marimba(freq, t, vol = 0.16) {
  const c = audio(), s = c.currentTime + t;
  [[1, 1], [4, 0.25]].forEach(([m, a]) => {
    const o = c.createOscillator(), g = c.createGain();
    o.type = "sine"; o.frequency.value = freq * m;
    g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(vol * a, s + 0.004); g.gain.exponentialRampToValueAtTime(0.0001, s + 0.35 / m);
    o.connect(g); route(g); o.start(s); o.stop(s + 0.4);
  });
}
// Bright plucked string (filtered sawtooth with a quick decay).
function pluck(freq, t, vol = 0.07, dur = 0.3) {
  const c = audio(), s = c.currentTime + t, o = c.createOscillator(), f = c.createBiquadFilter(), g = c.createGain();
  o.type = "sawtooth"; o.frequency.value = freq;
  f.type = "lowpass"; f.frequency.setValueAtTime(freq * 6, s); f.frequency.exponentialRampToValueAtTime(freq * 1.5, s + dur);
  g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(vol, s + 0.005); g.gain.exponentialRampToValueAtTime(0.0001, s + dur);
  o.connect(f).connect(g); route(g); o.start(s); o.stop(s + dur + 0.05);
}
// Warm synth bass note.
function bass(freq, t, vol = 0.18, dur = 0.22) {
  const c = audio(), s = c.currentTime + t, o = c.createOscillator(), f = c.createBiquadFilter(), g = c.createGain();
  o.type = "triangle"; o.frequency.value = freq; f.type = "lowpass"; f.frequency.value = 600;
  g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(vol, s + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, s + dur);
  o.connect(f).connect(g).connect(musicBus || out); o.start(s); o.stop(s + dur + 0.05);
}
