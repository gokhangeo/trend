// Shared robot drawing for the main screen (index.html) and the floating robot (bubble.html).
// Head items are drawn in the head's coordinates so they bob and tilt with it; outfits sit on the body.

const RING_COLORS = [["#3ddba4", "Yeşil"], ["#ff7eb6", "Pembe"], ["#a78bfa", "Mor"], ["#4fb3ff", "Mavi"], ["#ffa94d", "Turuncu"], ["#ffd43b", "Sarı"]];
const EYE_COLORS = [["#7ff5e0", "Turkuaz"], ["#ff9fd0", "Pembe"], ["#c4b5fd", "Lila"], ["#ffe066", "Sarı"]];

// Shop catalogue. slot: which part of the robot it goes on; price: stars it costs in the shop.
const ITEMS = {
  // outfits
  tisort: { slot: "outfit", name: "Çilekli tişört", icon: "🍓", price: 8 },
  okul: { slot: "outfit", name: "Okul forması", icon: "🎒", price: 10 },
  elbise: { slot: "outfit", name: "Pembe elbise", icon: "👗", price: 15 },
  tulum: { slot: "outfit", name: "Kot tulum", icon: "👖", price: 18 },
  pijama: { slot: "outfit", name: "Yıldızlı pijama", icon: "🌙", price: 20 },
  kazak: { slot: "outfit", name: "Kar tanesi kazak", icon: "❄️", price: 22 },
  kahraman: { slot: "outfit", name: "Süper kahraman", icon: "⚡", price: 30 },
  balerin: { slot: "outfit", name: "Balerin kıyafeti", icon: "🩰", price: 35 },
  prenses: { slot: "outfit", name: "Prenses elbisesi", icon: "👸", price: 45 },
  // head
  fiyonk: { slot: "head", name: "Fiyonk", icon: "🎀", price: 0 },
  cicek: { slot: "head", name: "Çiçek toka", icon: "🌸", price: 5 },
  kulaklik: { slot: "head", name: "Kulaklık", icon: "🎧", price: 10 },
  kedi: { slot: "head", name: "Kedi kulakları", icon: "🐱", price: 12 },
  tavsan: { slot: "head", name: "Tavşan kulakları", icon: "🐰", price: 15 },
  sapka: { slot: "head", name: "Parti şapkası", icon: "🥳", price: 20 },
  cicektac: { slot: "head", name: "Çiçek taç", icon: "💐", price: 25 },
  unicorn: { slot: "head", name: "Unicorn boynuzu", icon: "🦄", price: 35 },
  tac: { slot: "head", name: "Prenses tacı", icon: "👑", price: 50 },
  // face
  allik: { slot: "face", name: "Kalp yanaklar", icon: "💗", price: 6 },
  gozluk: { slot: "face", name: "Pembe gözlük", icon: "👓", price: 15 },
  kalpgozluk: { slot: "face", name: "Kalp gözlük", icon: "😍", price: 20 },
  gunes: { slot: "face", name: "Güneş gözlüğü", icon: "😎", price: 25 }
};
const SLOTS = ["outfit", "head", "face"];

const STAR = (cx, cy, r, fill) => {
  const pts = [];
  for (let i = 0; i < 10; i++) {
    const a = Math.PI / 5 * i - Math.PI / 2, rr = i % 2 ? r * 0.45 : r;
    pts.push((cx + rr * Math.cos(a)).toFixed(1) + "," + (cy + rr * Math.sin(a)).toFixed(1));
  }
  return `<polygon points="${pts.join(" ")}" fill="${fill}"/>`;
};
const HEART = (cx, cy, s, fill, extra = "") =>
  `<path d="M${cx} ${cy + s * 0.9} C${cx - s * 1.6} ${cy - s * 0.2} ${cx - s * 0.6} ${cy - s * 1.3} ${cx} ${cy - s * 0.35} C${cx + s * 0.6} ${cy - s * 1.3} ${cx + s * 1.6} ${cy - s * 0.2} ${cx} ${cy + s * 0.9} Z" fill="${fill}" ${extra}/>`;
const TORSO = "M60 214 L60 180 Q60 157 84 155 L116 155 Q140 157 140 180 L140 214 Z";
const torso = (fill) => `<path d="${TORSO}" fill="${fill}"/>`;

const OUTFITS = {
  tisort: torso("#ff6b6b") + `<path d="M88 155 Q100 166 112 155" stroke="#fff" stroke-width="3" fill="none"/>`
    + [[78, 178], [96, 186], [118, 176], [86, 200], [110, 198], [126, 194], [72, 194]].map(([x, y]) => `<ellipse cx="${x}" cy="${y}" rx="1.8" ry="2.6" fill="#ffe066"/>`).join("")
    + `<path d="M70 168 l5 -4 l5 4" stroke="#51cf66" stroke-width="2.5" fill="none" stroke-linecap="round"/>`,
  okul: torso("#1f3a68") + `<path d="M82 155 L100 176 L118 155 Z" fill="#fff"/><path d="M95 166 L105 166 L100 172 Z" fill="#e03131"/>`
    + `<circle cx="100" cy="186" r="2.2" fill="#fff"/><circle cx="100" cy="198" r="2.2" fill="#fff"/>`,
  elbise: torso("#ff8cc6") + `<circle cx="92" cy="159" r="7" fill="#fff"/><circle cx="108" cy="159" r="7" fill="#fff"/>`
    + `<path d="M64 196 Q100 206 136 196" stroke="#fff" stroke-width="3" fill="none" stroke-dasharray="4 4"/>`
    + `<circle cx="100" cy="176" r="2.4" fill="#fff"/><circle cx="100" cy="186" r="2.4" fill="#fff"/>`,
  tulum: torso("#f8f9fa") + `<path d="M70 172 L130 172 L130 214 L70 214 Z" fill="#4a7bd0"/>`
    + `<path d="M80 155 L82 173 M120 155 L118 173" stroke="#4a7bd0" stroke-width="6"/>`
    + `<circle cx="82" cy="174" r="3" fill="#ffd43b"/><circle cx="118" cy="174" r="3" fill="#ffd43b"/>`
    + `<rect x="90" y="182" width="20" height="14" rx="3" fill="none" stroke="#2f5aa8" stroke-width="2"/>`,
  pijama: torso("#2f3a72") + STAR(78, 176, 6, "#ffd43b") + STAR(118, 172, 5, "#ffd43b") + STAR(100, 196, 6, "#ffd43b") + STAR(128, 196, 4, "#ffe066")
    + `<path d="M108 182 a7 7 0 1 0 6 10 a5.5 5.5 0 1 1 -6 -10 Z" fill="#fff3bf"/>`
    + `<path d="M88 155 Q100 164 112 155" stroke="#a5b4fc" stroke-width="3" fill="none"/>`,
  kazak: torso("#8fd3ff") + [[82, 182], [116, 178], [100, 200]].map(([x, y]) =>
    `<g stroke="#fff" stroke-width="2" stroke-linecap="round"><path d="M${x - 6} ${y} L${x + 6} ${y} M${x} ${y - 6} L${x} ${y + 6} M${x - 4} ${y - 4} L${x + 4} ${y + 4} M${x - 4} ${y + 4} L${x + 4} ${y - 4}"/></g>`).join("")
    + `<path d="M84 155 Q100 162 116 155" stroke="#4dabf7" stroke-width="5" fill="none"/>`
    + `<path d="M60 168 L140 168" stroke="#fff" stroke-width="2" stroke-dasharray="3 3"/>`,
  kahraman: `<path d="M52 214 L58 162 L142 162 L148 214 Z" fill="#a61e4d"/>` + torso("#e63946")
    + `<circle cx="100" cy="184" r="13" fill="#ffd43b"/><path d="M103 173 L94 186 L100 186 L96 196 L106 182 L100 182 Z" fill="#e63946"/>`,
  balerin: torso("#ffc9de") + `<path d="M56 196 Q64 186 72 196 Q80 186 88 196 Q96 186 104 196 Q112 186 120 196 Q128 186 136 196 Q144 186 146 200 L146 214 L54 214 L54 200 Z" fill="#ffa8cc" opacity=".95"/>`
    + `<path d="M84 155 L84 170 M116 155 L116 170" stroke="#ff8cc6" stroke-width="3"/>` + HEART(100, 172, 5, "#ff5fa2"),
  prenses: torso("#b57edc") + `<circle cx="66" cy="168" r="12" fill="#d7b4f3"/><circle cx="134" cy="168" r="12" fill="#d7b4f3"/>`
    + `<path d="M80 156 Q100 176 120 156" stroke="#ffd43b" stroke-width="3" fill="none"/><circle cx="100" cy="168" r="4" fill="#ff5fa2" stroke="#ffd43b" stroke-width="1.5"/>`
    + `<path d="M62 200 L138 200" stroke="#ffd43b" stroke-width="3"/>` + STAR(84, 188, 3.5, "#fff3bf") + STAR(116, 188, 3.5, "#fff3bf")
};

const HEAD_BACK = {
  tavsan: `<g transform="rotate(-12 80 60)"><ellipse cx="80" cy="50" rx="9" ry="24" fill="var(--head)"/><ellipse cx="80" cy="52" rx="4.5" ry="17" fill="#ffc9de"/></g>`
    + `<g transform="rotate(12 120 60)"><ellipse cx="120" cy="50" rx="9" ry="24" fill="var(--head)"/><ellipse cx="120" cy="52" rx="4.5" ry="17" fill="#ffc9de"/></g>`,
  kulaklik: `<path d="M50 104 Q48 46 100 46 Q152 46 150 104" stroke="var(--acc2)" stroke-width="7" fill="none" stroke-linecap="round"/>`
};
const HEAD_FRONT = {
  kulaklik: `<g fill="var(--acc2)"><rect x="38" y="88" width="22" height="42" rx="10"/><rect x="140" y="88" width="22" height="42" rx="10"/></g>`,
  fiyonk: `<path d="M136 74 L119 61 L119 87 Z" fill="#ff5fa2"/><path d="M136 74 L153 61 L153 87 Z" fill="#ff5fa2"/><circle cx="136" cy="74" r="5.5" fill="#ff9cc8"/>`,
  cicek: [[64, 68], [73, 73], [70, 83], [58, 83], [55, 73]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="6" fill="#ffa8d2"/>`).join("") + `<circle cx="64" cy="76" r="4.5" fill="#ffd43b"/>`,
  kedi: `<path d="M57 84 L62 54 L84 74 Z" fill="var(--head)"/><path d="M63 76 L65 62 L76 72 Z" fill="#ffb3cf"/>`
    + `<path d="M143 84 L138 54 L116 74 Z" fill="var(--head)"/><path d="M137 76 L135 62 L124 72 Z" fill="#ffb3cf"/>`,
  sapka: `<path d="M78 76 L122 76 L100 26 Z" fill="#a78bfa"/><path d="M86 58 L114 58 M92 44 L108 44" stroke="#ffd43b" stroke-width="4"/><circle cx="100" cy="26" r="6.5" fill="#ff7eb6"/>`,
  cicektac: [[68, 74, "#ff8cc6"], [84, 68, "#ffd43b"], [100, 66, "#a78bfa"], [116, 68, "#4fb3ff"], [132, 74, "#ff8cc6"]].map(([x, y, c]) =>
    [0, 72, 144, 216, 288].map((a) => `<circle cx="${(x + 4.5 * Math.cos(a * Math.PI / 180)).toFixed(1)}" cy="${(y + 4.5 * Math.sin(a * Math.PI / 180)).toFixed(1)}" r="3.6" fill="${c}"/>`).join("")
    + `<circle cx="${x}" cy="${y}" r="2.6" fill="#fff"/>`).join(""),
  unicorn: `<path d="M92 76 L100 32 L108 76 Z" fill="#ffd43b" stroke="#f59f00" stroke-width="1.2"/><path d="M95 64 L105 60 M96 54 L104 50 M98 44 L102 42" stroke="#f59f00" stroke-width="2"/>`
    + `<circle cx="84" cy="74" r="4" fill="#ffa8d2"/><circle cx="116" cy="74" r="4" fill="#a5d8ff"/>`,
  tac: `<path d="M74 77 L78 50 L90 65 L100 44 L110 65 L122 50 L126 77 Z" fill="#ffd43b" stroke="#f59f00" stroke-width="1.5" stroke-linejoin="round"/>`
    + `<circle cx="100" cy="60" r="3.5" fill="#ff5fa2"/><circle cx="84" cy="67" r="2.8" fill="#4fb3ff"/><circle cx="116" cy="67" r="2.8" fill="#4fb3ff"/>`
};
const FACE = {
  allik: HEART(70, 136, 5, "#ff7eb6") + HEART(130, 136, 5, "#ff7eb6"),
  gozluk: `<g stroke="#ff5fa2" stroke-width="3.5" fill="rgba(255,255,255,.08)"><circle cx="84.5" cy="104" r="14"/><circle cx="115.5" cy="104" r="14"/><path d="M98.5 104 L101.5 104" fill="none"/></g>`,
  kalpgozluk: HEART(84.5, 104, 11, "rgba(255,95,162,.45)", 'stroke="#ff2d7a" stroke-width="2.5"') + HEART(115.5, 104, 11, "rgba(255,95,162,.45)", 'stroke="#ff2d7a" stroke-width="2.5"')
    + `<path d="M97 101 L103 101" stroke="#ff2d7a" stroke-width="2.5"/>`,
  gunes: `<rect x="70" y="93" width="28" height="22" rx="9" fill="#212529"/><rect x="102" y="93" width="28" height="22" rx="9" fill="#212529"/>`
    + `<path d="M98 100 L102 100" stroke="#212529" stroke-width="3"/><path d="M76 98 L84 98 M108 98 L116 98" stroke="#fff" stroke-width="2.5" stroke-linecap="round" opacity=".7"/>`
};

const group = (cls, map) => Object.entries(map).map(([k, svg]) => `<g class="${cls} ${cls}-${k}">${svg}</g>`).join("");

const ROBOT_SVG = `
<svg class="face" viewBox="0 0 200 200">
  <g transform="translate(100 88) scale(1.06) translate(-100 -104)">
    <g class="flip" id="flip">
      <g class="body-group">
        <rect x="92" y="144" width="16" height="14" rx="4" fill="var(--head-shade)"/>
        <path d="${TORSO}" fill="var(--head)"/>
        <path d="M84 176 Q100 184 116 176" stroke="var(--head-shade)" stroke-width="3" fill="none"/>
        ${group("o", OUTFITS)}
      </g>
      <g class="head-group head">
        ${group("hb", HEAD_BACK)}
        <g class="antenna">
          <rect x="97" y="54" width="6" height="22" rx="3" fill="var(--head-shade)"/>
          <circle class="antenna-tip tip" cx="100" cy="52" r="7"/>
        </g>
        <rect x="44" y="92" width="14" height="34" rx="7" fill="var(--ear)"/>
        <rect x="142" y="92" width="14" height="34" rx="7" fill="var(--ear)"/>
        <rect x="54" y="72" width="92" height="76" rx="24" fill="var(--head)"/>
        <rect x="64" y="84" width="72" height="42" rx="16" fill="var(--visor)"/>
        <g class="eyes"><g class="pupils">
          <rect class="eye" x="78" y="95" width="13" height="18" rx="5" fill="var(--eye)"/>
          <rect class="eye" x="109" y="95" width="13" height="18" rx="5" fill="var(--eye)"/>
        </g></g>
        <g class="sleep-eyes" stroke="var(--eye)" stroke-width="3.5" stroke-linecap="round" fill="none">
          <path d="M78 106 Q84.5 110 91 106"/><path d="M109 106 Q115.5 110 122 106"/>
        </g>
        <g class="cheeks" fill="var(--cheek)">
          <ellipse cx="70" cy="136" rx="6" ry="3.5"/>
          <ellipse cx="130" cy="136" rx="6" ry="3.5"/>
        </g>
        <path class="mouth" d="M88 135 Q100 143 112 135" stroke="var(--visor)" stroke-width="4" fill="none" stroke-linecap="round"/>
        ${group("f", FACE)}
        ${group("h", HEAD_FRONT)}
      </g>
    </g>
  </g>
</svg>`;

(function injectRobotStyle() {
  const ids = (slot) => Object.keys(ITEMS).filter((k) => ITEMS[k].slot === slot);
  const st = document.createElement("style");
  st.textContent = `
    .o, .h, .hb, .f { display: none; }
    ${ids("outfit").map((k) => `svg[data-outfit="${k}"] .o-${k}{display:inline}`).join("\n")}
    ${ids("head").map((k) => `svg[data-head="${k}"] .h-${k}, svg[data-head="${k}"] .hb-${k}{display:inline}`).join("\n")}
    ${ids("face").map((k) => `svg[data-face="${k}"] .f-${k}{display:inline}`).join("\n")}
    svg[data-head="sapka"] .antenna, svg[data-head="tac"] .antenna, svg[data-head="unicorn"] .antenna { display: none; }
    svg[data-face="gunes"] .eyes, svg[data-face="gunes"] .sleep-eyes { visibility: hidden; }
    svg[data-face="allik"] .cheeks { display: none; }
    .sleep-eyes { display: none; }
    body.sleeping .sleep-eyes { display: inline; }
    body.sleeping .eyes { display: none; }`;
  document.head.appendChild(st);
})();

function hexToRgba(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

/** Converts looks saved by older versions ({acc}) to the slot format. */
function normalizeLook(look) {
  look = Object.assign({}, look || {});
  if (look.acc) {
    if (look.acc !== "none" && ITEMS[look.acc]) look[ITEMS[look.acc].slot] = look.acc;
    delete look.acc;
  }
  return look;
}

/** look = {ring, eye, outfit, head, face}; applies to every robot drawing on the page (or only inside `root`). */
function applyLook(look, root) {
  if (typeof look === "string") { try { look = JSON.parse(look || "{}"); } catch (e) { look = {}; } }
  look = normalizeLook(look);
  const ring = look.ring || RING_COLORS[0][0], eye = look.eye || EYE_COLORS[0][0];
  const style = root ? root.style : document.documentElement.style;
  style.setProperty("--ring", ring);
  style.setProperty("--ring-glow-base", hexToRgba(ring, 0.38));
  style.setProperty("--eye", eye);
  style.setProperty("--acc2", ring);
  (root || document).querySelectorAll("svg.face").forEach((svg) => {
    SLOTS.forEach((slot) => { svg.dataset[slot] = look[slot] && ITEMS[look[slot]] ? look[slot] : "none"; });
  });
}
