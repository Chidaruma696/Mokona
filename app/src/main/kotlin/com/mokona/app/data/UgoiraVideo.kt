package com.mokona.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Turns a ugoira (frames in a zip plus a delay per frame) into an H.264 MP4 with
 * the phone's own encoder, no libraries. Each frame keeps its exact delay as the
 * presentation time; short loops are repeated so the file lasts at least a few seconds.
 */
object UgoiraVideo {
	private const val MIN_DURATION_MS = 3000L

	fun encode(context: Context, illustId: Long, meta: UgoiraMetadata, files: Map<String, ByteArray>): Uri {
		val first = meta.frames.firstOrNull()?.let { files[it.file] } ?: error("no frames")
		val probe = BitmapFactory.decodeByteArray(first, 0, first.size)
		// H.264 wants even dimensions; drop one row or column when needed.
		val w = probe.width and 1.inv()
		val h = probe.height and 1.inv()
		val total = meta.frames.sumOf { it.delay.coerceAtLeast(16).toLong() }
		val loops = if (total <= 0) 1 else ((MIN_DURATION_MS + total - 1) / total).coerceIn(1, 20).toInt()

		val tmp = File(context.cacheDir, "ugoira_$illustId.mp4")
		val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
			setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
			setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 6).coerceIn(1_000_000, 12_000_000))
			setInteger(MediaFormat.KEY_FRAME_RATE, 30)
			setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
		}
		val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
		codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
		codec.start()
		val muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
		var track = -1
		val info = MediaCodec.BufferInfo()
		val pixels = IntArray(w * h)

		fun drain(untilEos: Boolean) {
			while (true) {
				val out = codec.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
				when {
					out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
					out == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEos) return
					out >= 0 -> {
						val buf = codec.getOutputBuffer(out)!!
						if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) muxer.writeSampleData(track, buf, info)
						val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
						codec.releaseOutputBuffer(out, false)
						if (eos) return
					}
				}
			}
		}

		try {
			var ptsUs = 0L
			val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
			repeat(loops) {
				for (f in meta.frames) {
					val bytes = files[f.file] ?: continue
					val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: continue
					var idx: Int
					while (true) {
						idx = codec.dequeueInputBuffer(10_000)
						if (idx >= 0) break
						drain(false)
					}
					val image = codec.getInputImage(idx) ?: error("no input image")
					bmp.getPixels(pixels, 0, w, 0, 0, w, h)
					fillYuv(image, pixels, w, h)
					codec.queueInputBuffer(idx, 0, w * h * 3 / 2, ptsUs, 0)
					ptsUs += f.delay.coerceAtLeast(16) * 1000L
					drain(false)
				}
			}
			val idx = codec.dequeueInputBuffer(10_000)
			if (idx >= 0) codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
			drain(true)
		} finally {
			runCatching { codec.stop() }; codec.release()
			runCatching { muxer.stop() }; muxer.release()
		}

		val resolver = context.contentResolver
		val values = ContentValues().apply {
			put(MediaStore.Video.Media.DISPLAY_NAME, "pixiv_$illustId.mp4")
			put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
			put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Mokona")
		}
		val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore")
		resolver.openOutputStream(uri)!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
		tmp.delete()
		return uri
	}

	/** ARGB pixels into the encoder's flexible YUV 4:2:0 image (BT.601). */
	private fun fillYuv(image: android.media.Image, argb: IntArray, w: Int, h: Int) {
		val y = image.planes[0]; val u = image.planes[1]; val v = image.planes[2]
		val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
		for (row in 0 until h) {
			val yBase = row * y.rowStride
			for (col in 0 until w) {
				val p = argb[row * w + col]
				val r = p shr 16 and 0xff; val g = p shr 8 and 0xff; val b = p and 0xff
				val yy = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
				yb.put(yBase + col * y.pixelStride, yy.coerceIn(0, 255).toByte())
				if (row and 1 == 0 && col and 1 == 0) {
					val uu = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
					val vv = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
					val cr = row / 2; val cc = col / 2
					ub.put(cr * u.rowStride + cc * u.pixelStride, uu.coerceIn(0, 255).toByte())
					vb.put(cr * v.rowStride + cc * v.pixelStride, vv.coerceIn(0, 255).toByte())
				}
			}
		}
	}
}
