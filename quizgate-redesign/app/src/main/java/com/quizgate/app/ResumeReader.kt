package com.quizgate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** Runs off the UI thread. No network; a selected PDF is rendered and OCRed locally. */
object ResumeReader {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun read(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                require(out.size() + n <= MAX_BYTES) { "Choose a file smaller than 2 MB." }
                out.write(buffer,0,n)
            }
            out.toByteArray()
        } ?: error("This file could not be opened.")
        require(bytes.isNotEmpty()) { "The selected file is empty." }
        val isPdf = bytes.size >= 5 && bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-"
        val text = if (isPdf) readPdf(context,bytes) else {
            require(context.contentResolver.getType(uri)?.startsWith("text/") == true) { "Choose a PDF or plain-text resume." }
            bytes.toString(Charsets.UTF_8).also { require(!it.contains('\u0000')) { "This is not a plain-text file." } }
        }
        require(text.trim().length >= 30) { "Not enough readable text. Try a clearer PDF or a plain-text file." }
        return text.take(40000)
    }
    private fun readPdf(context: Context, bytes: ByteArray): String {
        val temp = File.createTempFile("selected-document-",".pdf",context.cacheDir)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    require(pdf.pageCount in 1..5) { "Choose a PDF with 1 to 5 pages." }
                    val result = StringBuilder()
                    for (index in 0 until pdf.pageCount) {
                        if (Thread.currentThread().isInterrupted) error("Reading was cancelled.")
                        pdf.openPage(index).use { page ->
                            val width = 1400
                            val height = (width.toDouble() * page.height / page.width).toInt().coerceIn(1,2200)
                            val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                            var task: com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text>? = null
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                task = recognizer.process(InputImage.fromBitmap(bitmap,0))
                                val recognized = Tasks.await(task,20,TimeUnit.SECONDS)
                                result.append(recognized.text).append('\n')
                            } finally {
                                val pending = task
                                if (pending == null || pending.isComplete) bitmap.recycle()
                                else pending.addOnCompleteListener { bitmap.recycle() }
                            }
                        }
                        if (result.length > 40000) break
                    }
                    return result.toString()
                }
            }
        } finally { recognizer.close(); temp.delete() }
    }
}
