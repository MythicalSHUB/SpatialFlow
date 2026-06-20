package com.codetrio.spatialflow.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.sqrt

object LoudnessAnalyzer {

    private const val TAG = "LoudnessAnalyzer"
    private const val TIMEOUT_US = 10000L

    /**
     * Analyzes the audio file at the given path and estimates its integrated loudness in LUFS.
     * This uses a fast RMS estimation to approximate BS.1770 LUFS.
     * @param path The absolute path to the local audio file.
     * @return Estimated LUFS, or null if analysis fails.
     */
    fun analyzeLufsFast(path: String): Float? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            
            if (audioTrackIndex < 0 || format == null) return null
            
            extractor.selectTrack(audioTrackIndex)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: return null
            val codec = MediaCodec.createDecoderByType(mimeType)
            
            codec.configure(format, null, null, 0)
            codec.start()
            
            var totalSquareSum = 0.0
            var sampleCount = 0L
            
            val info = MediaCodec.BufferInfo()
            var isEOS = false
            
            // To make it fast, we skip large chunks of audio and sample periodically
            val skipIntervalUs = 500_000L // 0.5 sec
            var nextSampleTimeUs = 0L
            
            while (!isEOS) {
                val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferId >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferId)
                    if (inputBuffer != null) {
                        // Seek to the next sampling point for faster analysis
                        if (extractor.sampleTime > 0 && extractor.sampleTime < nextSampleTimeUs) {
                            extractor.seekTo(nextSampleTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        }
                        
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                            nextSampleTimeUs = extractor.sampleTime + skipIntervalUs
                            extractor.advance()
                        }
                    }
                }
                
                var outputBufferId = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                while (outputBufferId >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferId)
                    if (outputBuffer != null && info.size > 0) {
                        // Process PCM 16-bit
                        val shortBuffer = outputBuffer.asShortBuffer()
                        val numSamples = info.size / 2
                        
                        for (i in 0 until numSamples) {
                            val sample = shortBuffer.get(i).toDouble() / Short.MAX_VALUE.toDouble()
                            totalSquareSum += sample * sample
                        }
                        sampleCount += numSamples
                    }
                    
                    codec.releaseOutputBuffer(outputBufferId, false)
                    
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                    }
                    outputBufferId = codec.dequeueOutputBuffer(info, 0)
                }
            }
            
            codec.stop()
            codec.release()
            extractor.release()
            
            if (sampleCount == 0L) return null
            
            val meanSquare = totalSquareSum / sampleCount
            val rms = sqrt(meanSquare)
            
            // Convert RMS to an estimated LUFS value. 
            // A full scale sine wave (0 dBFS) has an RMS of -3 dB.
            // LUFS is roughly RMS_dBFS + offset (typically around -3 to 0 depending on the material).
            val rmsDb = 20 * log10(rms)
            
            // Rough approximation to BS.1770
            val estimatedLufs = rmsDb + 3.0
            
            return estimatedLufs.toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze LUFS", e)
            extractor.release()
            null
        }
    }
}
