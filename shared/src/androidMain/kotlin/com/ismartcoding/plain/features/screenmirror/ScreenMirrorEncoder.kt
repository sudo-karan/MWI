package com.ismartcoding.plain.features.screenmirror

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Bundle
import android.view.Surface

/**
 * Hardware H.264 encoder fed by a MediaProjection VirtualDisplay (spec §8). Encoded Annex-B access
 * units (including the codec-config SPS/PPS) are delivered via [onFrame] on the drain thread;
 * `isKeyframe` marks sync frames and codec config so the sender can gate late joiners.
 */
class ScreenMirrorEncoder(
    private val onFrame: (data: ByteArray, isKeyframe: Boolean) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var drainThread: Thread? = null

    @Volatile
    private var running = false

    fun start(projection: MediaProjection, width: Int, height: Int, dpi: Int, bitrate: Int, fps: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()

        codec = enc
        inputSurface = surface
        virtualDisplay = projection.createVirtualDisplay(
            "mwi-mirror", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null,
        )
        running = true
        drainThread = Thread({ drain() }, "mwi-mirror-drain").apply { start() }
    }

    private fun drain() {
        val enc = codec ?: return
        val info = MediaCodec.BufferInfo()
        while (running) {
            val index = try {
                enc.dequeueOutputBuffer(info, 10_000)
            } catch (_: IllegalStateException) {
                break
            }
            if (index >= 0) {
                val buffer = enc.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    buffer.get(bytes)
                    val isKey = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 ||
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    onFrame(bytes, isKey)
                }
                enc.releaseOutputBuffer(index, false)
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }

    /** Force the next output to be a sync frame (e.g. when a new client connects). */
    fun requestKeyframe() {
        runCatching {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        }
    }

    /** Adjust the target bitrate on the fly (dynamic quality). */
    fun setBitrate(bitrate: Int) {
        runCatching {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate) })
        }
    }

    fun stop() {
        running = false
        runCatching { drainThread?.join(500) }
        runCatching { virtualDisplay?.release() }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { inputSurface?.release() }
        virtualDisplay = null
        codec = null
        inputSurface = null
        drainThread = null
    }
}
