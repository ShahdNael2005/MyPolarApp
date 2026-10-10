// I used this link for audio help https://www.geeksforgeeks.org/javascript/web-audio-api/
const canvas = document.getElementById("space");
const context = canvas.getContext("2d");
const waveCanvas = document.getElementById("wave");
const waveContext = waveCanvas.getContext("2d");

const heartRateElement = document.getElementById("hr");
const zoneElement = document.getElementById("zone");
//const slider = document.getElementById("slider");
const heartImage = document.getElementById("heart");
const playButton = document.getElementById("play");
const soundButton = document.getElementById("sound");

let width;
let height;
const devicePixelRatioValue = window.devicePixelRatio || 1;

let heartRate = 72;
let time = 0;
let isRunning = true;
let isSoundEnabled = true;
let lastBeat = 0;
let audioContext = null;
let particles = [];

// ======================================================
// POLAR HEART RATE DATA
// ======================================================

const DATA_FILE =
  "./Polar_H10_9F642824_20260430_102838_HR.txt";

let heartRateData = [];
let currentIndex = 0;

let playbackStart = 0;
let recordingDuration = 0;
let pausedElapsed = 0;

async function loadHeartRateData() {

  try {
    const response =
      await fetch(DATA_FILE);
    if (!response.ok) {
      throw new Error(
        `Could not load ${DATA_FILE}`
      );
    }
    const text =
      await response.text();
    const lines =
      text
        .trim()
        .split(/\r?\n/);
    // Remove header
    lines.shift();

    heartRateData = lines
      .map(line => {
        const columns =
          line.split(";");
        return {
          timestamp:
            new Date(columns[0]).getTime(),
          bpm:
            Number(columns[1])
        };
      })
      .filter(sample =>
        Number.isFinite(sample.timestamp) &&
        Number.isFinite(sample.bpm)
      );
    if (heartRateData.length === 0) {
      throw new Error(
        "No readable HR data found."
      );
    }

    // Make first sample start at 0ms
    const firstTime =
      heartRateData[0].timestamp;
    heartRateData.forEach(sample => {
      sample.time =
        sample.timestamp -
        firstTime;
    });

    recordingDuration =
      heartRateData[
        heartRateData.length - 1
      ].time;
    currentIndex = 0;
    heartRate =
      heartRateData[0].bpm;
    // slider.value = heartRate;
    playbackStart =
      performance.now();
    console.log(
      "Polar HR loaded:",
      heartRateData.length,
      "samples"
    );

    console.log(
      "Duration:",
      recordingDuration / 1000,
      "seconds"
    );
  }

  catch (error) {
    console.error(
      "Polar HR error:",
      error
    );
  }
}

function resizeCanvas() {
  width = canvas.width = window.innerWidth * devicePixelRatioValue;
  height = canvas.height = window.innerHeight * devicePixelRatioValue;
  waveCanvas.width = 260 * devicePixelRatioValue;
  waveCanvas.height = 105 * devicePixelRatioValue;
}

function lerp(start, end, progress) {
  return start + (end - start) * progress;
}

function clamp(value, minimum, maximum) {
  return Math.max(minimum, Math.min(maximum, value));
}

function getHeartRateColor() {
  const progress = clamp((heartRate - 55) / 60, 0, 1);
  const red = lerp(20, 255, progress);
  const green = lerp(230, 45, progress);
  const blue = lerp(255, 85, progress);

  return `rgb(${red | 0}, ${green | 0}, ${blue | 0})`;
}

function getHeartRateZone() {
  if (heartRate < 60) return "LOW";
  if (heartRate < 85) return "NORMAL";
  if (heartRate < 100) return "ELEVATED";
  return "HIGH";
}

function addParticles(amount) {
  for (let index = 0; index < amount; index += 1) {
    particles.push({
      angle: Math.random() * Math.PI * 2,
      radius: Math.random() * width * 0.36 + width * 0.08,
      speed: Math.random() * 1.4 + 0.5,
      size: Math.random() * 0.8 + 0.2,
      life: Math.random() * 100,
    });
  }
}

function playHeartbeatSound() {
  if (!isSoundEnabled) return;

  audioContext =
    audioContext ||
    new (window.AudioContext || window.webkitAudioContext)();

  const oscillator = audioContext.createOscillator();
  const gain = audioContext.createGain();

  oscillator.frequency.value = heartRate > 95 ? 120 : 90;
  gain.gain.setValueAtTime(0.0001, audioContext.currentTime);
  gain.gain.exponentialRampToValueAtTime(
    0.17,
    audioContext.currentTime + 0.012,
  );
  gain.gain.exponentialRampToValueAtTime(
    0.0001,
    audioContext.currentTime + 0.13,
  );

  oscillator.connect(gain);
  gain.connect(audioContext.destination);
  oscillator.start();
  oscillator.stop(audioContext.currentTime + 0.14);
}

function drawHoops(centerX, centerY) {
  const currentColor = getHeartRateColor();

  context.save();
  context.translate(centerX, centerY);

  for (let index = 0; index < 13; index += 1) {
    const y = (index - 6) * height * 0.045;
    const radiusX = width * (0.16 + index * 0.018);
    const radiusY = height * 0.023;
    const pulse =
      Math.sin(time * 0.08 + index) * 8 * devicePixelRatioValue;

    context.beginPath();
    context.ellipse(
      0,
      y,
      radiusX + pulse,
      radiusY,
      0,
      0,
      Math.PI * 2,
    );
    context.strokeStyle = currentColor;
    context.globalAlpha = 0.12 + (index % 3) * 0.06;
    context.lineWidth =
      (index % 4 === 0 ? 2.2 : 1) * devicePixelRatioValue;
    context.setLineDash(
      index % 2
        ? [8 * devicePixelRatioValue, 15 * devicePixelRatioValue]
        : [],
    );
    context.shadowColor = currentColor;
    context.shadowBlur = 18;
    context.stroke();

    for (let dotIndex = 0; dotIndex < 8; dotIndex += 1) {
      const angle = time * 0.012 + dotIndex * 0.78 + index * 0.13;
      const x = Math.cos(angle) * (radiusX + pulse);
      const dotY = y + Math.sin(angle) * radiusY;

      context.fillStyle = currentColor;
      context.globalAlpha = 0.8;
      context.beginPath();
      context.arc(x, dotY, 2.1 * devicePixelRatioValue, 0, Math.PI * 2);
      context.fill();
    }
  }

  context.restore();
}

function drawWaveField() {
  const currentColor = getHeartRateColor();

  context.strokeStyle = currentColor;
  context.lineWidth = devicePixelRatioValue;
  context.shadowBlur = 14;
  context.shadowColor = currentColor;

  for (let row = 0; row < 28; row += 1) {
    context.beginPath();
    context.globalAlpha = 0.035;

    for (
      let x = 0;
      x < width;
      x += 18 * devicePixelRatioValue
    ) {
      const y =
        height * 0.5 +
        Math.sin(x * 0.006 + time * 0.018 + row * 0.35) * height * 0.18 +
        (row - 14) * height * 0.016;

      if (x === 0) {
        context.moveTo(x, y);
      } else {
        context.lineTo(x, y);
      }
    }

    context.stroke();
  }
}

function drawParticles(centerX, centerY) {
  const targetCount = heartRate > 95 ? 820 : heartRate > 75 ? 600 : 380;

  if (particles.length < targetCount) addParticles(8);
  if (particles.length > targetCount) particles.splice(0, 4);

  const currentColor = getHeartRateColor();

  particles.forEach((particle) => {
    particle.angle += 0.002 * particle.speed * (heartRate / 70);
    particle.life += 1;

    const wobble =
      Math.sin(time * 0.02 + particle.life * 0.04) *
      20 *
      devicePixelRatioValue;
    const x = centerX + Math.cos(particle.angle) * particle.radius;
    const y =
      centerY +
      Math.sin(particle.angle) * (particle.radius * 0.28) +
      wobble;

    context.fillStyle = currentColor;
    context.globalAlpha = 0.25 + 0.55 * particle.size;
    context.shadowBlur = 12;
    context.shadowColor = currentColor;
    context.beginPath();
    context.arc(
      x,
      y,
      (1.1 + 2.5 * particle.size) * devicePixelRatioValue,
      0,
      Math.PI * 2,
    );
    context.fill();
  });
}

function drawEcgWaveform() {
  const currentColor = getHeartRateColor();

  waveContext.clearRect(0, 0, waveCanvas.width, waveCanvas.height);
  waveContext.strokeStyle = currentColor;
  waveContext.lineWidth = 2.4 * devicePixelRatioValue;
  waveContext.shadowBlur = 14;
  waveContext.shadowColor = currentColor;
  waveContext.beginPath();

  for (let x = 0; x < waveCanvas.width; x += 1) {
    const phase = ((x + time * 3) % 90) / 90;
    let spike;

    if (phase < 0.05) {
      spike = (-30 * phase) / 0.05;
    } else if (phase < 0.1) {
      spike = (58 * (phase - 0.05)) / 0.05;
    } else if (phase < 0.15) {
      spike = 58 - (78 * (phase - 0.1)) / 0.05;
    } else if (phase < 0.22) {
      spike = -20 + (20 * (phase - 0.15)) / 0.07;
    } else {
      spike = Math.sin(phase * 18) * 3;
    }

    const y =
      waveCanvas.height * 0.55 - spike * devicePixelRatioValue;
    if (x === 0) {
      waveContext.moveTo(x, y);
    } else {
      waveContext.lineTo(x, y);
    }
  }
  waveContext.stroke();
}

function animate() {
  time += 1;
  // ======================================================
  // PLAY POLAR HR DATA
  // ======================================================
  if (
    isRunning &&
    heartRateData.length > 0
  ) {
    let elapsed =
      performance.now() -
      playbackStart;
    // Loop when recording finishes
    if (
      elapsed >=
      recordingDuration
    ) {
      playbackStart =
        performance.now();
      currentIndex = 0;
      elapsed = 0;
    }
    // Move through HR samples according to timestamp
    while (
      currentIndex <
      heartRateData.length - 1 &&

      heartRateData[
        currentIndex + 1
      ].time <= elapsed
    ) {

      currentIndex++;

    }
    // REAL BPM from Polar file
    heartRate =
      heartRateData[
        currentIndex
      ].bpm;

    //slider.value = heartRate;
  }

  heartRateElement.textContent = Math.round(heartRate);
  zoneElement.textContent = getHeartRateZone();
  zoneElement.style.color = getHeartRateColor();

  const beatInterval = 3600 / heartRate;

  if (time - lastBeat > beatInterval) {
    lastBeat = time;
    playHeartbeatSound();
    heartImage.style.animationDuration = `${60 / heartRate}s`;
  }

  context.clearRect(0, 0, width, height);

  const centerX = width * 0.5;
  const centerY = height * 0.5;

  drawWaveField();
  drawHoops(centerX, centerY);
  drawParticles(centerX, centerY);
  drawEcgWaveform();

  window.requestAnimationFrame(animate);
}
/*
slider.addEventListener("input", () => {
  isRunning = false;
  heartRate = Number(slider.value);
});*/

playButton.addEventListener("click", () => {
  if (isRunning) {
    // Pause
    pausedElapsed =
      performance.now() -
      playbackStart;

    isRunning = false;
  }
  else {
    // Resume from same position
    playbackStart =
      performance.now() -
      pausedElapsed;
    isRunning = true;
  }

});

soundButton.addEventListener("click", () => {
  isSoundEnabled = !isSoundEnabled;
});

resizeCanvas();
window.addEventListener("resize", resizeCanvas);
addParticles(420);
loadHeartRateData()
  .then(() => {
    animate();
  });