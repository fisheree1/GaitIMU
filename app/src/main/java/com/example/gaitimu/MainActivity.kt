package com.example.gaitimu

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gaitimu.io.CsvLogger
import com.example.gaitimu.io.FileExporter
import com.example.gaitimu.model.ImuSample
import com.example.gaitimu.sensor.ImuRecorder
import com.example.gaitimu.sensor.SampleRateEstimator
import com.example.gaitimu.ui.theme.GaitIMUTheme
import kotlin.math.roundToInt

enum class SampleRatePreset(val hz: Int) {
    HZ_50(50),
    HZ_100(100);

    val samplePeriodUs: Int
        get() = 1_000_000 / hz
}

data class DashboardUiState(
    val isRecording: Boolean = false,
    val statusText: String = "Idle",
    val sampleRateHz: Float = 0f,
    val selectedSampleRate: SampleRatePreset = SampleRatePreset.HZ_50,
    val currentFileName: String = "-",
    val latestSample: ImuSample = ImuSample(0L, 0f, 0f, 0f, 0f, 0f, 0f)
)

class MainActivity : ComponentActivity() {

    private lateinit var imuRecorder: ImuRecorder
    private lateinit var csvLogger: CsvLogger
    private lateinit var fileExporter: FileExporter
    private val sampleRateEstimator = SampleRateEstimator()

    private var uiState by mutableStateOf(DashboardUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imuRecorder = ImuRecorder(this)
        csvLogger = CsvLogger(this)
        fileExporter = FileExporter(this)

        enableEdgeToEdge()
        setContent {
            GaitIMUTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        uiState = uiState,
                        hasAllSensors = imuRecorder.isAvailable,
                        onStart = { startRecording() },
                        onStop = { stopRecording() },
                        onRateSelected = { rate -> selectSampleRate(rate) },
                        onExport = { exportCsv() },
                        onClear = { clearUi() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (uiState.isRecording) {
            stopRecording(status = "Stopped (background)")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imuRecorder.stop()
        csvLogger.close()
    }

    private fun startRecording() {
        if (!imuRecorder.isAvailable) {
            toast("Accelerometer or gyroscope is unavailable")
            return
        }
        if (uiState.isRecording) {
            return
        }

        val file = try {
            csvLogger.startNewFile()
        } catch (error: Exception) {
            toast("Failed to create CSV: ${error.message}")
            return
        }

        sampleRateEstimator.reset()

        val started = imuRecorder.start(samplePeriodUs = uiState.selectedSampleRate.samplePeriodUs) { sample ->
            csvLogger.append(sample)
            val hz = sampleRateEstimator.onSample(sample.timestampNs)
            uiState = uiState.copy(
                latestSample = sample,
                sampleRateHz = hz
            )
        }

        if (!started) {
            csvLogger.close()
            toast("Failed to start recorder")
            return
        }

        uiState = uiState.copy(
            isRecording = true,
            statusText = "Recording (${uiState.selectedSampleRate.hz}Hz)",
            currentFileName = file.name,
            sampleRateHz = 0f
        )
    }

    private fun stopRecording(status: String = "Stopped") {
        imuRecorder.stop()
        csvLogger.close()
        uiState = uiState.copy(
            isRecording = false,
            statusText = status
        )
    }

    private fun exportCsv() {
        csvLogger.flush()
        val file = csvLogger.currentFile()
        if (file == null || !file.exists()) {
            toast("No CSV file to export")
            return
        }
        try {
            fileExporter.shareCsv(file)
        } catch (error: Exception) {
            toast("Export failed: ${error.message}")
        }
    }

    private fun clearUi() {
        uiState = uiState.copy(
            sampleRateHz = 0f,
            latestSample = ImuSample(0L, 0f, 0f, 0f, 0f, 0f, 0f),
            statusText = if (uiState.isRecording) "Recording" else "Idle"
        )
    }

    private fun selectSampleRate(rate: SampleRatePreset) {
        if (uiState.isRecording) {
            toast("请先停止采集再切换采样率")
            return
        }
        uiState = uiState.copy(selectedSampleRate = rate)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    hasAllSensors: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRateSelected: (SampleRatePreset) -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sample = uiState.latestSample
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "GaitIMU MVP",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Status: ${uiState.statusText}")
                Text(text = "Sensors: ${if (hasAllSensors) "Ready" else "Unavailable"}")
                Text(text = "Target Rate: ${uiState.selectedSampleRate.hz} Hz")
                Text(text = "Sample Rate: ${uiState.sampleRateHz.roundToInt()} Hz")
                Text(text = "File: ${uiState.currentFileName}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onRateSelected(SampleRatePreset.HZ_50) },
                    enabled = !uiState.isRecording,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.selectedSampleRate == SampleRatePreset.HZ_50) "50Hz ✓" else "50Hz")
                }
                Button(
                    onClick = { onRateSelected(SampleRatePreset.HZ_100) },
                    enabled = !uiState.isRecording,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.selectedSampleRate == SampleRatePreset.HZ_100) "100Hz ✓" else "100Hz")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Accelerometer (m/s²)")
                Text(text = "ax=${"%.3f".format(sample.ax)}, ay=${"%.3f".format(sample.ay)}, az=${"%.3f".format(sample.az)}")
                Text(text = "Gyroscope (rad/s)")
                Text(text = "gx=${"%.3f".format(sample.gx)}, gy=${"%.3f".format(sample.gy)}, gz=${"%.3f".format(sample.gz)}")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onStart, enabled = hasAllSensors && !uiState.isRecording, modifier = Modifier.weight(1f)) {
                Text("Start")
            }
            Button(onClick = onStop, enabled = uiState.isRecording, modifier = Modifier.weight(1f)) {
                Text("Stop")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text("Export")
            }
            Button(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text("Clear")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    GaitIMUTheme {
        DashboardScreen(
            uiState = DashboardUiState(),
            hasAllSensors = true,
            onStart = {},
            onStop = {},
            onRateSelected = {},
            onExport = {},
            onClear = {}
        )
    }
}
