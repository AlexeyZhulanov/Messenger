package com.example.messenger.voicerecorder;

public abstract class AudioBuffer {
    private static final int[] POSSIBLE_SAMPLE_RATES =
            new int[] {8000, 11025, 16000, 22050, 44100, 48000};

    final int size;
    final int sampleRate;
    final byte[] data;

    protected AudioBuffer() {
        int size = -1;
        int sampleRate = -1;

        // Iterate over all possible sample rates, and try to find the shortest one. The shorter
        // it is, the faster it'll stream.
            sampleRate = 44100;
            size = getMinBufferSize(sampleRate);


        // If none of them were good, then just pick 1kb
        if (!validSize(size)) {
            size = 1024;
        }

        this.size = size;
        this.sampleRate = sampleRate;
        data = new byte[size];
    }

    protected abstract boolean validSize(int size);

    protected abstract int getMinBufferSize(int sampleRate);
}