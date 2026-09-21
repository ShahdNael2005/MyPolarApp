package com.example.mypolarapp;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Generates and plays a short sine-wave tone for each heartbeat
 * Heart rates above 95 BPM use a higher sound
 * Each tone rises quickly in volume and then fades out
 */
public class HeartbeatSoundPlayer {

    private static final int SAMPLE_RATE = 44100;  // Number of audio samples generated per second
    private static final float DURATION_SECONDS = 0.14f; // Total length of each heartbeat sound 140 milliseconds
    private static final float ATTACK_SECONDS = 0.012f; // Time taken to reach peak volume: 12 milliseconds
    private static final float PEAK_GAIN = 0.17f; // Maximum amplitude of the sound
    private static final float FLOOR_GAIN = 0.0001f; // Small, non-zero amplitude used at the start and end of the envelope

    /**
     * Generates and plays one heartbeat sound using the current BPM.
     */
    public void play(float heartRate) {
        // higher pitch when the heart rate exceeds 95 BPM
        float frequency = heartRate > 95 ? 120f : 90f;
        // Generate the audio samples for the selected frequency
        short[] samples = synthesize(frequency);

        AudioTrack track;
        try {
            // Create a separate audio track for this heartbeat
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            // Identify the audio as media containing a sound effect
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            // Store each sample as a signed 16-bit PCM value
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    // Each 16-bit sample requires two bytes
                    .setBufferSizeInBytes(samples.length * 2)
                    // Load the complete short sound into memory before playback
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
        } catch (Exception e) {
            // Skip the sound if track fails
            // The visualisation can continue without audio
            return;
        }
        // Copy the generated samples into the track and begin playback
        track.write(samples, 0, samples.length);
        track.play();

        // Release once playback finishes so we don't leak a track per beat.
        track.setNotificationMarkerPosition(samples.length - 1);
        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack t) {
                t.release();
            }

            @Override
            public void onPeriodicNotification(AudioTrack t) { //non:(
            }
        });
    }
    /**
     * Creates a sine wave with an exponential volume rise and decay.
     */
    private short[] synthesize(float frequency) {
        // Convert the sound duration into a total number of samples
        int sampleCount = (int) (SAMPLE_RATE * DURATION_SECONDS);
        short[] samples = new short[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            // Calculate the time position in seconds
            float t = i / (float) SAMPLE_RATE;
            float envelope;
            if (t < ATTACK_SECONDS) {
                envelope = (float) (FLOOR_GAIN * Math.pow(PEAK_GAIN / FLOOR_GAIN, t / ATTACK_SECONDS));
            } else {
                // Express progress through the decay phase as a fraction
                float decayT = (t - ATTACK_SECONDS) / (DURATION_SECONDS - ATTACK_SECONDS);
                envelope = (float) (PEAK_GAIN * Math.pow(FLOOR_GAIN / PEAK_GAIN, decayT));
            }
            // Generate a sine-wave value between -1 and 1
            float wave = (float) Math.sin(2 * Math.PI * frequency * t);
            samples[i] = (short) (wave * envelope * Short.MAX_VALUE);
        }
        return samples;
    }
}