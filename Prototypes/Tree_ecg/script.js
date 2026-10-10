const app = document.getElementById('app');
const scene = document.getElementById('scene');
const tree = document.getElementById('tree');

const bpmText = document.getElementById('bpmText');
const stateText = document.getElementById('stateText');
const meaningText = document.getElementById('meaningText');
const dataStatus = document.getElementById('dataStatus');

const canvas = document.getElementById('ecgCanvas');
const ctx = canvas.getContext('2d');
if (stateText) stateText.style.display = 'none';
if (meaningText) meaningText.style.display = 'none';
if (dataStatus) dataStatus.style.display = 'none';



// ======================================================
// POLAR ECG DATA
// ======================================================

let bpm = 50;

let polarData = [];
let currentIndex = 0;

const DATA_FILE = "./Polar_H10_9F642824_20260430_102838_HR.txt";

const START_BPM = 50;
const END_BPM = 155;

let playbackStart = 0;
let recordingDuration = 0;


// ======================================================
// TREE
// ======================================================

const leafPositions = [
  [120,145], [155,110], [200,85], [250,95], [300,130],
  [95,210], [150,205], [260,205], [335,200], [205,155],
  [175,260], [250,270], [120,290], [310,290], [215,45],
  [80,160], [350,155], [185,330], [265,335],
  // 6 extra leaves that grow after 75 BPM
  [110,115],
  [325,120],
  [135,255],
  [290,245],
  [160,60],
  [275,65]
];

const applePositions = [
  [150,145], [250,130], [315,185],
  [120,230], [265,250], [205,90],
    // 4 new apples
  [175,185],
  [290,100],
  [175,290],
  [330,260]
];


leafPositions.forEach((pos, i) => {
  const leaf = document.createElement('div');

  leaf.className = 'leaf';

  leaf.dataset.index = i;

  leaf.style.left = pos[0] + 'px';
  leaf.style.top = pos[1] + 'px';
  leaf.style.transitionDelay = (i * 0.035) + 's';

  tree.appendChild(leaf);
});


applePositions.forEach((pos, i) => {
  const apple = document.createElement('div');

  // Give each apple a number
  apple.dataset.index = i;
  apple.className = 'apple';
  apple.style.left = pos[0] + 'px';
  apple.style.top = pos[1] + 'px';
  apple.style.transitionDelay = (i * 0.08) + 's';

  tree.appendChild(apple);
});


for (let i = 0; i < 34; i++) {
  const p = document.createElement('div');

  p.className = 'particle';
  p.style.left = Math.random() * 100 + '%';
  p.style.bottom = Math.random() * 160 + 'px';
  p.style.animationDelay = Math.random() * 7 + 's';
  p.style.animationDuration = 5 + Math.random() * 6 + 's';

  scene.appendChild(p);
}


// ======================================================
// LOAD POLAR FILE
// ======================================================

async function loadPolarData() {
  try {

    if (window.location.protocol === 'file:') {
      throw new Error(
        "Open index.html using http://localhost:8000"
      );
    }

    dataStatus.textContent = "Loading Polar ECG...";

    const response = await fetch(DATA_FILE);

    if (!response.ok) {
      throw new Error(
        `Could not load ${DATA_FILE} (HTTP ${response.status})`
      );
    }

    const text = await response.text();

    const lines = text
      .trim()
      .split(/\r?\n/);

    // Remove:
    // Phone timestamp;sensor timestamp [ns];timestamp [ms];ecg [uV]
    lines.shift();

    polarData = lines
      .map(line => {
        const columns = line.split(';');

        return {
          timestamp:new Date(columns[0]).getTime(),
          bpm: Number(columns[1])
        };
      })
      .filter(sample =>
        Number.isFinite(sample.timestamp) &&
        Number.isFinite(sample.bpm)
      );

    if (polarData.length === 0) {
      throw new Error("The Polar file contains no readable ECG samples.");
    }

    // Start the recording at 0 ms.
    // Start the recording at 0 ms.
    const firstTime = polarData[0].timestamp;

    polarData.forEach(sample => {
      sample.time =
        sample.timestamp -
        firstTime;
    });

    recordingDuration =
      polarData[polarData.length - 1].time;

    currentIndex = 0;
    playbackStart = performance.now();

    bpm =
      polarData[0].bpm;
    dataStatus.textContent =
      `Polar ECG loaded — ${Math.round(recordingDuration / 1000)} s recording`;

    console.log(
      "Polar ECG loaded:",
      polarData.length,
      "samples"
    );

  } catch (error) {

    console.error("Error loading Polar data:", error);

    dataStatus.textContent =
      "Polar file error — " + error.message;
  }
}


// ======================================================
// BPM
// =====================================================
let ecgTime = 0;

function ecgValue(x) {

  const beatLength =
    60 / bpm;

  const phase =
    ((x / 100 + ecgTime) % beatLength)
    / beatLength;

  let y = 0;

  // P wave
  y +=
    0.10 *
    Math.exp(
      -Math.pow(
        (phase - 0.18) / 0.035,
        2
      )
    );

  // Q
  y -=
    0.16 *
    Math.exp(
      -Math.pow(
        (phase - 0.35) / 0.018,
        2
      )
    );

  // R
  y +=
    1.10 *
    Math.exp(
      -Math.pow(
        (phase - 0.39) / 0.012,
        2
      )
    );

  // S
  y -=
    0.28 *
    Math.exp(
      -Math.pow(
        (phase - 0.43) / 0.018,
        2
      )
    );

  // T
  y +=
    0.30 *
    Math.exp(
      -Math.pow(
        (phase - 0.65) / 0.07,
        2
      )
    );

  return y;
}

// ======================================================
// VISUAL STATE
// ======================================================

function getState(rate) {

  if (rate < 60) {
    return {
      className: 'low',
      label: 'Low',
      text: ' '
    };
  }

  if (rate <= 90) {
    return {
      className: 'stable',
      label: 'Stable',
      text: ' '
    };
  }

  if (rate <= 120) {
    return {
      className: 'high',
      label: 'Heart rate increasing',
      text: ' '
    };
  }

  return {
    className: 'too-fast',
    label: 'Heart rate fast',
    text: ' '
  };
}

function updateApples() {

  const apples = document.querySelectorAll('.apple');

  apples.forEach((apple, index) => {

    // Original 6 apples
    if (index < 6) {

      if (bpm > 90 && bpm <= 120) {
        apple.style.opacity = '1';
        apple.style.transform = 'scale(1)';
      } 
      else if (bpm > 120) {
        apple.style.opacity = '0.45';
        apple.style.transform = 'scale(0.75)';
      }
      else {
        apple.style.opacity = '0';
        apple.style.transform = 'scale(0.2)';
      }

      return;
    }


    // ------------------------------------
    // EXTRA APPLES: 100 → 120 BPM
    // ------------------------------------

    const extraApple = index - 6;

    const thresholds = [
      100,
      107,
      114,
      120
    ];

    if (
      bpm >= thresholds[extraApple] &&
      bpm <= 120
    ) {

      apple.style.opacity = '1';
      apple.style.transform = 'scale(1)';

    }
    else if (bpm > 120) {

      apple.style.opacity = '0.45';
      apple.style.transform = 'scale(0.75)';

    }
    else {

      apple.style.opacity = '0';
      apple.style.transform = 'scale(0.2)';

    }

  });
}
function updateLeaves() {

  const leaves = document.querySelectorAll('.leaf');

  // You originally had 19 leaves
  const ORIGINAL_LEAVES = 19;

  // New leaves appear gradually
  const thresholds = [
    75,
    78,
    81,
    84,
    87,
    90
  ];

  leaves.forEach((leaf, index) => {

    // Leave original 19 leaves controlled by your CSS
    if (index < ORIGINAL_LEAVES) {
      return;
    }

    const extraLeafIndex =
      index - ORIGINAL_LEAVES;

    const threshold =
      thresholds[extraLeafIndex];


    // Before threshold = hidden
    if (bpm < threshold) {

      leaf.style.opacity = '0';
      leaf.style.transform =
        'scale(0.2) rotate(0deg)';

    }

    // 75 - 120 = grow green leaves
    else if (bpm <= 120) {

      leaf.style.opacity = '1';
      leaf.style.transform =
        'scale(1) rotate(-12deg)';

    }

    // Above 120 = stressed/fire state
    else {

      leaf.style.opacity = '0.75';
      leaf.style.transform =
        'scale(0.85) rotate(24deg)';

    }

  });
}

function updateState() {

  const state = getState(bpm);

  app.className = 'app ' + state.className;
  document.body.className = state.className;
if (bpmText) {
    bpmText.textContent = Math.round(bpm);
  }
  updateLeaves();
  updateApples();
}


// ======================================================
// ECG DRAWING
// ======================================================

function drawGrid() {

  ctx.clearRect(
    0,
    0,
    canvas.width,
    canvas.height
  );

  ctx.strokeStyle =
    'rgba(255,255,255,0.08)';

  ctx.lineWidth = 1;

  for (let x = 0; x < canvas.width; x += 40) {

    ctx.beginPath();

    ctx.moveTo(x, 0);
    ctx.lineTo(x, canvas.height);

    ctx.stroke();
  }

  for (let y = 0; y < canvas.height; y += 30) {

    ctx.beginPath();

    ctx.moveTo(0, y);
    ctx.lineTo(canvas.width, y);

    ctx.stroke();
  }
}


function drawECG() {

  drawGrid();

  if (
    polarData.length > 0 &&
    recordingDuration > 0
  ) {

    let elapsed =
      performance.now() - playbackStart;

    // Automatically loop when the 5-minute recording ends.
    if (elapsed >= recordingDuration) {

      playbackStart =
        performance.now();

      currentIndex = 0;
      elapsed = 0;
    }

    while (
      currentIndex < polarData.length - 1 &&
      polarData[currentIndex + 1].time <= elapsed
    ) {
      currentIndex++;
    }

    bpm = polarData[currentIndex].bpm;

    updateState();
    dataStatus.textContent =
      `Playing: ${Math.round(elapsed / 1000)}s / ${Math.round(recordingDuration / 1000)}s`;


    // ==========================================
    // DRAW ECG BASED ON CURRENT REAL BPM
    // ==========================================

    ctx.beginPath();

    ctx.strokeStyle =
      '#78ff9f';

    ctx.lineWidth = 3;


    for (
      let x = 0;
      x < canvas.width;
      x++
    ) {

      const y =
        canvas.height * 0.58 -
        ecgValue(x) * 55;


      if (x === 0) {

        ctx.moveTo(x, y);

      }

      else {

        ctx.lineTo(x, y);

      }

    }


    ctx.stroke();


    ecgTime += 0.012;

  }


  requestAnimationFrame(
    drawECG
  );
}


// ======================================================
// START
// ======================================================

updateState();
loadPolarData();
drawECG();

  