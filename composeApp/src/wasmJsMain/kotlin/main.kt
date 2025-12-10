import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import kotlin.js.JsAny

// External definitions for Web Audio API
external class AudioContext {
    val state: String
    val currentTime: Double
    val destination: AudioDestinationNode
    fun resume(): JsAny? // Returns Promise
    fun createOscillator(): OscillatorNode
    fun createGain(): GainNode
}

external class AudioDestinationNode : AudioNode

open external class AudioNode {
    fun connect(destination: AudioNode): AudioNode
}

external class OscillatorNode : AudioNode {
    var type: String
    val frequency: AudioParam
    fun start(whenTime: Double = definedExternally)
    fun stop(whenTime: Double = definedExternally)
}

external class GainNode : AudioNode {
    val gain: AudioParam
}

external class AudioParam {
    var value: Float
    fun setValueAtTime(value: Double, startTime: Double): AudioParam
    fun exponentialRampToValueAtTime(value: Double, endTime: Double): AudioParam
}

class WebAudioController : AudioController {
    // Lazy initialization or direct creation
    private val audioContext = AudioContext()

    private fun playTone(frequency: Float) {
        if (audioContext.state == "suspended") {
            audioContext.resume()
        }

        val oscillator = audioContext.createOscillator()
        val gainNode = audioContext.createGain()

        oscillator.type = "sine"
        oscillator.frequency.value = frequency

        // Connect: Oscillator -> Gain -> Destination
        oscillator.connect(gainNode)
        gainNode.connect(audioContext.destination)

        oscillator.start()

        // Ramp down gain to avoid clicking (ADSR envelope style)
        // Note: Using Double for values as per standard API,
        // but Kotlin wrapper usually handles Number types
        gainNode.gain.setValueAtTime(0.1,
            audioContext.currentTime)
        // Ramp to near-zero over 0.5 seconds
        gainNode.gain.exponentialRampToValueAtTime(0.0001,
            audioContext.currentTime + 0.5)

        oscillator.stop(audioContext.currentTime + 0.5)
    }

    override fun playLeftSound() {
        playTone(440f) // A4
    }

    override fun playRightSound() {
        playTone(880f) // A5
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val audioController = WebAudioController()
    CanvasBasedWindow(title = "Pendulum App", canvasElementId = "ComposeTarget") {
        App(audioController)
    }
}