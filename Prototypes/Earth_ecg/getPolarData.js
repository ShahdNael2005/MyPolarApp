// ======================================================
// POLAR HEART RATE DATA
// ======================================================

const DATA_FILE =
  "./Polar_H10_9F642824_20260430_102838_HR.txt";

let recordedHeartRates = [];
let playbackIndex = 0;

let playbackTimer = null;
let isPlaying = false;

// One Polar HR reading per second
const PLAYBACK_INTERVAL_MS = 1000;


// ======================================================
// OPTIONAL CONTROLS
// ======================================================

const playButton =
  document.getElementById("play-button");

const pauseButton =
  document.getElementById("pause-button");

const restartButton =
  document.getElementById("restart-button");

const playbackStatus =
  document.getElementById("playback-status");

const playbackProgressFill =
  document.getElementById("playback-progress-fill");


// ======================================================
// LOAD POLAR HR FILE
// ======================================================

async function loadPolarData() {

  try {

    if (
      window.location.protocol === "file:"
    ) {

      throw new Error(
        "Open index.html using http://localhost:8000"
      );

    }


    if (playbackStatus) {

      playbackStatus.textContent =
        "Loading Polar heart-rate data...";

    }


    const response =
      await fetch(DATA_FILE);


    if (!response.ok) {

      throw new Error(
        `Could not load ${DATA_FILE} (HTTP ${response.status})`
      );

    }


    const text =
      await response.text();


    const lines =
      text
        .trim()
        .split(/\r?\n/);


    // Remove header:
    // Phone timestamp;HR [bpm];HRV [ms];...
    lines.shift();


    recordedHeartRates =
      lines
        .map(line => {

          const columns =
            line.split(";");


          return {

            timestamp:
              new Date(
                columns[0]
              ).getTime(),

            bpm:
              Number(
                columns[1]
              )

          };

        })
        .filter(sample =>

          Number.isFinite(
            sample.timestamp
          ) &&

          Number.isFinite(
            sample.bpm
          )

        );


    if (
      recordedHeartRates.length === 0
    ) {

      throw new Error(
        "No readable HR samples found."
      );

    }


    // --------------------------------------
    // Make first timestamp = 0
    // --------------------------------------

    const firstTime =
      recordedHeartRates[0]
        .timestamp;


    recordedHeartRates.forEach(
      sample => {

        sample.time =
          sample.timestamp -
          firstTime;

      }
    );


    playbackIndex = 0;


    // Start Earth visualisation at
    // first Polar HR value
    applyHeartRate(
      recordedHeartRates[0].bpm
    );


    if (playButton)
      playButton.disabled = false;

    if (pauseButton)
      pauseButton.disabled = false;

    if (restartButton)
      restartButton.disabled = false;


    if (playbackStatus) {

      playbackStatus.textContent =
        `${recordedHeartRates.length} HR readings loaded`;

    }


    console.log(
      "Polar HR loaded:",
      recordedHeartRates.length,
      "readings"
    );


    console.log(
      "First BPM:",
      recordedHeartRates[0].bpm
    );


    // Automatically start
    playData();

  }

  catch (error) {

    console.error(
      "Polar HR error:",
      error
    );


    if (playbackStatus) {

      playbackStatus.textContent =
        "Polar HR error — " +
        error.message;

    }

  }

}


// ======================================================
// UPDATE PROGRESS
// ======================================================

function updatePlaybackDisplay() {

  if (
    recordedHeartRates.length === 0
  ) {

    return;

  }


  const progress =

    (
      playbackIndex /
      recordedHeartRates.length
    )

    * 100;


  if (playbackProgressFill) {

    playbackProgressFill.style.width =
      `${progress}%`;

  }


  if (playbackStatus) {

    playbackStatus.textContent =

      `${playbackIndex} / ` +
      `${recordedHeartRates.length} — ` +
      `${currentHR} BPM`;

  }

}


// ======================================================
// SHOW NEXT HR READING
// ======================================================

function showNextHeartRate() {

  if (
    playbackIndex >=
    recordedHeartRates.length
  ) {

    pausePlayback();


    if (playbackStatus) {

      playbackStatus.textContent =
        "Playback complete";

    }


    if (playbackProgressFill) {

      playbackProgressFill.style.width =
        "100%";

    }


    return;

  }


  const sample =
    recordedHeartRates[
      playbackIndex
    ];


  // IMPORTANT:
  // This sends real Polar BPM
  // into your Earth visualisation.
  applyHeartRate(
    sample.bpm
  );


  playbackIndex++;


  updatePlaybackDisplay();

}


// ======================================================
// PLAY
// ======================================================

function playData() {

  if (
    recordedHeartRates.length === 0
  ) {

    return;

  }


  // Restart automatically
  if (
    playbackIndex >=
    recordedHeartRates.length
  ) {

    playbackIndex = 0;

  }


  clearInterval(
    playbackTimer
  );


  showNextHeartRate();


  playbackTimer =
    setInterval(

      showNextHeartRate,

      PLAYBACK_INTERVAL_MS

    );


  isPlaying = true;


  if (playButton)
    playButton.disabled = true;

  if (pauseButton)
    pauseButton.disabled = false;

}


// ======================================================
// PAUSE
// ======================================================

function pausePlayback() {

  clearInterval(
    playbackTimer
  );


  playbackTimer = null;

  isPlaying = false;


  if (playButton)
    playButton.disabled = false;

  if (pauseButton)
    pauseButton.disabled = true;

}


// ======================================================
// RESTART
// ======================================================

function restartPlayback() {

  pausePlayback();


  playbackIndex = 0;


  if (
    recordedHeartRates.length > 0
  ) {

    const firstHR =
      recordedHeartRates[0].bpm;


    applyHeartRate(
      firstHR
    );


    if (playbackProgressFill) {

      playbackProgressFill.style.width =
        "0%";

    }


    if (playbackStatus) {

      playbackStatus.textContent =
        `Ready — ${firstHR} BPM`;

    }


    playData();

  }

}


// ======================================================
// BUTTONS
// ======================================================

if (playButton) {

  playButton.addEventListener(
    "click",
    playData
  );

}


if (pauseButton) {

  pauseButton.addEventListener(
    "click",
    pausePlayback
  );

}


if (restartButton) {

  restartButton.addEventListener(
    "click",
    restartPlayback
  );

}


// ======================================================
// START
// ======================================================

loadPolarData();