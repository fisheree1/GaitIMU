package com.example.gaitimu.io

import android.content.Context
import com.example.gaitimu.model.ImuSample
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvLogger(private val context: Context) {

    private var outputFile: File? = null
    private var writer: BufferedWriter? = null
    private val lineBuffer = mutableListOf<String>()
    private var isStarted = false

    fun startNewFile(): File {
        close()
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "imu_${formatter.format(Date())}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)

        writer = BufferedWriter(FileWriter(file, false))
        outputFile = file
        isStarted = true

        writer?.apply {
            write("t_ns,ax,ay,az,gx,gy,gz")
            newLine()
            flush()
        }

        return file
    }

    fun append(sample: ImuSample, flushThreshold: Int = 300) {
        if (!isStarted) {
            return
        }
        lineBuffer.add(
            "${sample.timestampNs},${sample.ax},${sample.ay},${sample.az},${sample.gx},${sample.gy},${sample.gz}"
        )
        if (lineBuffer.size >= flushThreshold) {
            flush()
        }
    }

    fun flush() {
        val bufferedWriter = writer ?: return
        if (lineBuffer.isEmpty()) {
            return
        }
        lineBuffer.forEach { line ->
            bufferedWriter.write(line)
            bufferedWriter.newLine()
        }
        bufferedWriter.flush()
        lineBuffer.clear()
    }

    fun currentFile(): File? = outputFile

    fun close() {
        flush()
        writer?.close()
        writer = null
        lineBuffer.clear()
        isStarted = false
    }
}
