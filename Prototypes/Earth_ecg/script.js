
// ── STATE ─────────────────────────────────────────────────────────────
let currentHR = 70;
let legPhase = 0;
let earthRotation = 0;
let lastFrameTime = 0;
let ecgHistory = [];

const MAX_ECG_HISTORY = 500;

// ── ZONE DETECTION ────────────────────────────────────────────────────
function getZone(hr) {
  if (hr < 60)  return 'low';
  if (hr <= 80) return 'walk';
  if (hr <= 100) return 'jog';
  return 'run';
}


function getZoneColors(zone) {
  return {
    low:  { r:80,  g:160, b:220,  frame:'zone-low',  bpmClass:'bpm-low'  },
    walk: { r:100, g:220, b:80,     frame:'zone-walk', bpmClass:'bpm-walk' },
    jog:  { r:160, g:90,  b:220,  frame:'zone-jog',  bpmClass:'bpm-jog'  },
    run:  { r:230, g:60,  b:60,   frame:'zone-run',  bpmClass:'bpm-run'  },
  }[zone];
}

// ── DRAW STICK FIGURE ─────────────────────────────────────────────────
function drawPerson(canvas, legPhase, zone) {
  const ctx = canvas.getContext('2d');
  const W = canvas.width, H = canvas.height;
  ctx.clearRect(0, 0, W, H);

  const z = getZoneColors(zone);
  const color = `rgb(${z.r},${z.g},${z.b})`;
  ctx.strokeStyle = color;
  ctx.lineWidth = 2.5;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  // Speeds
  const speeds = { low: 0.02, walk: 0.06, jog: 0.13, run: 0.22 };
  const sp = speeds[zone];

  // Body positions depend on zone
  const cx = W / 2;
  const headY = 8;
  const torsoTop = 18;
  const torsoBot = 42;

  // Head
  ctx.beginPath();
  ctx.arc(cx, headY, 7, 0, Math.PI * 2);
  ctx.stroke();

  // Lean forward when running
  const lean = zone === 'run' ? 4 : zone === 'jog' ? 2 : 0;

  // Torso
  ctx.beginPath();
  ctx.moveTo(cx, torsoTop);
  ctx.lineTo(cx + lean, torsoBot);
  ctx.stroke();

  // Arm swing
  const armSwing = zone === 'low' ? 0 : Math.sin(legPhase * 2) * (zone === 'run' ? 14 : zone === 'jog' ? 10 : 6);
  // Left arm
  ctx.beginPath();
  ctx.moveTo(cx, torsoTop + 6);
  ctx.lineTo(cx - 10 - armSwing + lean, torsoTop + 18);
  ctx.stroke();
  // Right arm
  ctx.beginPath();
  ctx.moveTo(cx, torsoTop + 6);
  ctx.lineTo(cx + 10 + armSwing + lean, torsoTop + 18);
  ctx.stroke();

  // Leg stride amplitude
  const stride = zone === 'run' ? 16 : zone === 'jog' ? 11 : zone === 'walk' ? 7 : 0;
  const liftR = zone === 'run' ? 10 : zone === 'jog' ? 6 : 0;

  const legL1 = Math.sin(legPhase) * stride;
  const legR1 = -Math.sin(legPhase) * stride;
  const liftL  = zone !== 'low' ? Math.max(0,  Math.sin(legPhase)) * liftR : 0;
  const liftR2  = zone !== 'low' ? Math.max(0, -Math.sin(legPhase)) * liftR : 0;

  // Left leg
  ctx.beginPath();
  ctx.moveTo(cx + lean, torsoBot);
  ctx.lineTo(cx + lean + legL1 * 0.5, torsoBot + (H - torsoBot) * 0.45 - liftL * 0.5);
  ctx.lineTo(cx + lean + legL1, H - 4 - liftL);
  ctx.stroke();

  // Right leg
  ctx.beginPath();
  ctx.moveTo(cx + lean, torsoBot);
  ctx.lineTo(cx + lean + legR1 * 0.5, torsoBot + (H - torsoBot) * 0.45 - liftR2 * 0.5);
  ctx.lineTo(cx + lean + legR1, H - 4 - liftR2);
  ctx.stroke();

  // Glow
  ctx.shadowColor = color;
  ctx.shadowBlur = 8;
  ctx.beginPath();
  ctx.arc(cx, headY, 7, 0, Math.PI * 2);
  ctx.stroke();
  ctx.shadowBlur = 0;
}

// ── DRAW ECG STRIP ────────────────────────────────────────────────────
function drawECG(canvas, history, zone) {
  const ctx = canvas.getContext('2d');
  const W = canvas.width, H = canvas.height;
  ctx.clearRect(0, 0, W, H);

  if (history.length < 2) return;

  const z = getZoneColors(zone);

  // Grid lines
  ctx.strokeStyle = 'rgba(255,255,255,0.05)';
  ctx.lineWidth = 1;
  for (let y = 0; y < H; y += 20) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
  }

  // Waveform
  const grad = ctx.createLinearGradient(0, 0, W, 0);
  grad.addColorStop(0, `rgba(${z.r},${z.g},${z.b},0)`);
  grad.addColorStop(0.4, `rgba(${z.r},${z.g},${z.b},0.4)`);
  grad.addColorStop(1, `rgba(${z.r},${z.g},${z.b},0.9)`);

  ctx.strokeStyle = grad;
  ctx.lineWidth = 2;
  ctx.shadowColor = `rgb(${z.r},${z.g},${z.b})`;
  ctx.shadowBlur = 6;
  ctx.beginPath();

  const minHR = 45, maxHR = 190;
  history.forEach((hr, i) => {
    const x = (i / (MAX_ECG_HISTORY - 1)) * W;
    const y = H - ((hr - minHR) / (maxHR - minHR)) * (H - 10) - 5;
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
  ctx.shadowBlur = 0;
}

// ── DRAW EARTH ────────────────────────────────────────────────────────
const earthElement = document.getElementById('earth');

function updateEarth(rot) {
  // translate3d keeps the Earth on the GPU while rotate provides smooth motion.
  earthElement.style.transform = `translate3d(0, 0, 0) rotate(${rot.toFixed(3)}deg)`;
}

// ── UPDATE FRAME BORDER ───────────────────────────────────────────────
function updateFrame(zone, hr) {
  const segments = document.querySelectorAll('.frame-segment');
  const blobs = document.querySelectorAll('.pulse-blob');
  const z = getZoneColors(zone);

  segments.forEach(segment => {
    const positionClass = Array.from(segment.classList).find(
      className =>
        className === 'frame-top' ||
        className === 'frame-bottom' ||
        className === 'frame-left' ||
        className === 'frame-right'
    );

    segment.className =
      `frame-segment ${positionClass} ${z.frame}`;
  });

  blobs.forEach(blob => {
    blob.style.background =
      `rgba(${z.r}, ${z.g}, ${z.b}, 0.6)`;

    // The pulse speed follows the selected BPM.
    blob.style.animationDuration = `${60 / hr}s`;
  });
}

function applyHeartRate(hr) {
  const parsedHR = Math.round(Number(hr));

  if (!Number.isFinite(parsedHR)) {
    return;
  }

  // Ignore values that are clearly not heart-rate readings.
  if (parsedHR < 30 || parsedHR > 240) {
    return;
  }

  currentHR = parsedHR;

  const zone = getZone(currentHR);

  updateHUD(currentHR, zone);
  updateFrame(zone, currentHR);
}

function applyECGSample(value) {
  value = Number(value);
  if (!Number.isFinite(value)) return;

  ecgHistory.push(value);

  if (ecgHistory.length > MAX_ECG_HISTORY) {
    ecgHistory.shift();
  }
}
function resetHeartRateDisplay(hr) {

  const parsedHR = Math.round(Number(hr));

  if (!Number.isFinite(parsedHR)) {
    return;
  }

  currentHR = parsedHR;

  ecgHistory =
    new Array(MAX_ECG_HISTORY)
      .fill(parsedHR);

  applyHeartRate(parsedHR);
}
// ── UPDATE HUD ────────────────────────────────────────────────────────
// ── UPDATE HUD ────────────────────────────────────────────────────────
function updateHUD(hr, zone) {
  const bpmEl = document.getElementById('bpm-display');
  const zoneEl = document.getElementById('zone-label');
  const z = getZoneColors(zone);

  // SHOW HEART RATE NUMBER
  bpmEl.textContent = `${hr} BPM`;

  // Force it to be visible
  bpmEl.style.display = 'block';
  bpmEl.style.visibility = 'visible';
  bpmEl.style.opacity = '1';

  bpmEl.className = z.bpmClass;

  // Zone text
  const zoneNames = {
    low: 'LOW',
    walk: 'WALK',
    jog: 'JOG',
    run: 'RUN'
  };

  zoneEl.textContent = `♥ ${zoneNames[zone]}`;
  zoneEl.style.color = `rgb(${z.r},${z.g},${z.b})`;
}

// ── STARS BACKGROUND ──────────────────────────────────────────────────
function initStars() {
  const canvas = document.getElementById('stars');
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#0a0a0f';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  for (let i = 0; i < 120; i++) {
    const x = Math.random() * canvas.width;
    const y = Math.random() * canvas.height;
    const r = Math.random() * 1.2;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(255,255,255,${Math.random() * 0.5 + 0.1})`;
    ctx.fill();
  }
}

// ── POSITION PERSON CANVAS ────────────────────────────────────────────
function positionPersonCanvas() {
  const personCanvas = document.getElementById('person-canvas');
  const earthContainer = document.getElementById('earth-container');
  const world = document.getElementById('world');
  const worldRect = world.getBoundingClientRect();
  const earthRect = earthContainer.getBoundingClientRect();

  const earthTop = earthRect.top - worldRect.top;
  personCanvas.style.position = 'absolute';
  personCanvas.style.left = (world.offsetWidth / 2 - 25) + 'px';
  personCanvas.style.top = (earthTop - 82) + 'px';
}

// ── MAIN LOOP ─────────────────────────────────────────────────────────

function tick(timestamp) {
  const frameScale = lastFrameTime
    ? Math.min((timestamp - lastFrameTime) / (1000 / 60), 3)
    : 1;

  lastFrameTime = timestamp;

  const zone = getZone(currentHR);

  const personSpeeds = {
    low: 0,
    walk: 0.04,
    jog: 0.08,
    run: 0.15
  };

  legPhase += personSpeeds[zone] * frameScale;

  const earthSpeeds = {
    low: 0,
    walk: 0.3,
    jog: 0.6,
    run: 1.1
  };

  earthRotation =
    (
      earthRotation +
      earthSpeeds[zone] * frameScale
    ) % 360;

  drawPerson(
    document.getElementById('person-canvas'),
    legPhase,
    zone
  );

  drawECG(
    document.getElementById('ecg-strip'),
    ecgHistory,
    zone
  );

  updateEarth(earthRotation);

  requestAnimationFrame(tick);
}


// ── INIT ──────────────────────────────────────────────────────────────
initStars();



applyHeartRate(currentHR);

// Let layout settle, then position person
setTimeout(() => {
  positionPersonCanvas();
  requestAnimationFrame(tick);
}, 100);

window.addEventListener('resize', () => {
  initStars();
  positionPersonCanvas();
});
