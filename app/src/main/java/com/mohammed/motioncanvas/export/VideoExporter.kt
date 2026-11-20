package com.mohammed.motioncanvas.export

import android.content.ContentValues
import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mohammed.motioncanvas.engine.FrameRenderer
import com.mohammed.motioncanvas.model.AudioTrack
import com.mohammed.motioncanvas.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoExporter {
    suspend fun exportToMp4(
        context: Context,
        project: Project,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val width = (project.widthPx / 2) * 2
            val height = (project.heightPx / 2) * 2
            val fps = project.fps.coerceIn(15, 60)
            val totalFrames = ((project.durationMs / 1000.0) * fps).toInt().coerceAtLeast(1)

            val videoOnlyFile = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(videoOnlyFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()
            val renderer = FrameRenderer(context)

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) encoder.signalEndOfInputStream()
                while (true) {
                    val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (status >= 0) {
                        val encodedData = encoder.getOutputBuffer(status) ?: error("Null buffer")
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bufferInfo.size = 0
                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(status, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }

            try {
                for (frameIdx in 0 until totalFrames) {
                    val timeMs = (frameIdx * 1000L) / fps
                    val canvas = inputSurface.lockHardwareCanvas()
                    try {
                        canvas.save()
                        canvas.scale(width.toFloat() / project.widthPx, height.toFloat() / project.heightPx)
                        renderer.renderFrame(canvas, project, timeMs)
                        canvas.restore()
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }
                    drainEncoder(false)
                    onProgress(((frameIdx + 1).toFloat() / totalFrames) * 0.9f)
                }
                drainEncoder(true)
            } finally {
                encoder.stop(); encoder.release(); inputSurface.release()
                if (muxerStarted) muxer.stop()
                muxer.release()
            }

            val finalFile = if (project.audio != null) {
                muxWithAudio(context, videoOnlyFile, project.audio, project.durationMs)
            } else videoOnlyFile

            onProgress(1f)
            val safeTitle = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "MotionCanvas" }
            val fileName = "${safeTitle}_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MotionCanvas")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore error")
            resolver.openOutputStream(uri)?.use { out -> finalFile.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
            }
            videoOnlyFile.delete()
            if (finalFile != videoOnlyFile) finalFile.delete()
            "Movies/MotionCanvas/$fileName"
        }
    }

    private fun muxWithAudio(context: Context, videoFile: File, audio: AudioTrack, totalDurationMs: Long): File {
        return runCatching {
            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(context, Uri.parse(audio.uriString), null)
            var audioSrcTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioSrcTrack = i; audioFormat = fmt; break
                }
            }
            if (audioSrcTrack == -1 || audioFormat == null) {
                audioExtractor.release(); return videoFile
            }
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (!mime.contains("mp4a") && !mime.contains("aac")) {
                audioExtractor.release(); return videoFile
            }

            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoFile.absolutePath)
            var videoSrcTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    videoSrcTrack = i; videoFormat = fmt; break
                }
            }

            val outFile = File(context.cacheDir, "muxed_${System.currentTimeMillis()}.mp4")
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideoTrack = muxer.addTrack(videoFormat!!)
            val outAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            videoExtractor.selectTrack(videoSrcTrack)
            while (true) {
                val size = videoExtractor.readSampleData(buf, 0)
                if (size < 0) break
                info.offset = 0; info.size = size
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(outVideoTrack, buf, info)
                videoExtractor.advance()
            }

            audioExtractor.selectTrack(audioSrcTrack)
            audioExtractor.seekTo(audio.trimStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val maxUs = totalDurationMs * 1000L
            val offsetUs = (audio.startTimeMs - audio.trimStartMs) * 1000L
            while (true) {
                val size = audioExtractor.readSampleData(buf, 0)
                if (size < 0) break
                val pts = audioExtractor.sampleTime + offsetUs
                if (pts > maxUs) break
                if (pts >= 0L) {
                    info.offset = 0; info.size = size
                    info.presentationTimeUs = pts
                    info.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(outAudioTrack, buf, info)
                }
                audioExtractor.advance()
            }

            videoExtractor.release(); audioExtractor.release()
            muxer.stop(); muxer.release()
            outFile
        }.getOrElse { videoFile }
    }
}
