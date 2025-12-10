import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Physics Constants
const val GRAVITY = 9.81
const val DAMPING = 0.01 // Air resistance
const val LENGTH_METERS = 2.0 // Simulation length for physics

interface AudioController {
    fun playLeftSound()
    fun playRightSound()
}

class PendulumState {
    var angle by mutableStateOf(PI / 4)
    var angularVelocity by mutableStateOf(0.0)
    var angularAcceleration by mutableStateOf(0.0)
    var isDragging by mutableStateOf(false)
    var maxAngle by mutableStateOf(0.0)
    var maxVelocity by mutableStateOf(0.0)
    var hasUserInteracted by mutableStateOf(false)
}

@Composable
fun App(audioController: AudioController? = null) {
    MaterialTheme(
        colors = darkColors(
            background = Color(0xFF121212),
            onBackground = Color.White
        )
    ) {
        val state = remember { PendulumState() }
        
        // Physics Loop
        LaunchedEffect(Unit) {
            var lastTime = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { time ->
                    val dt = (time - lastTime) / 1_000_000_000.0
                    lastTime = time
                    
                    if (!state.isDragging) {
                        val previousVelocity = state.angularVelocity
                        val previousAngle = state.angle
                        
                        // Semi-implicit Euler Integration
                        val alpha = -(GRAVITY / LENGTH_METERS) * sin(state.angle) -
                                DAMPING * state.angularVelocity
                        state.angularAcceleration = alpha
                        state.angularVelocity += alpha * dt
                        state.angle += state.angularVelocity * dt
                        
                        // Detect peak of right swing
                        // Velocity was positive, now zero or negative
                         if (previousVelocity > 0 && state.angularVelocity <= 0) {
                            state.maxAngle = state.angle
                            audioController?.playRightSound()
                        }

                        // Detect peak of left swing
                        // Velocity was negative, now zero or positive
                        if (previousVelocity < 0 && state.angularVelocity >= 0) {
                            audioController?.playLeftSound()
                        }
                        
                        // Detect bottom of swing: Angle sign changed (crossed zero)
                        if ((previousAngle > 0 && state.angle <= 0) ||
                            (previousAngle < 0 && state.angle >= 0)) {
                            state.maxVelocity = kotlin.math.abs(state.angularVelocity)
                        }
                    }
                }
            }
        }
        PendulumScreen(state)
    }
}

@Composable
fun PendulumScreen(state: PendulumState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            state.isDragging = true
                            state.angularVelocity = 0.0
                            state.maxAngle = 0.0 // Reset max on new interaction
                            state.maxVelocity = 0.0
                            state.hasUserInteracted = true
                        },
                        onDragEnd = { 
                            state.isDragging = false 
                            /* Optionally impart velocity from the drag throw here
                             * if we tracked drag history
                             */
                        },
                        onDrag = { change, _ ->
                            // Calculate a new angle based on touch position relative to pivot
                            // (center of screen)
                            val center = Offset(size.width / 2f,
                                size.height / 3f) // Pivot point
                            val touch = change.position
                            val dx = touch.x - center.x
                            val dy = touch.y - center.y
                            
                            // atan2(x, y) gives angle from vertical down if we treat y as down
                            // standard atan2(y, x) is from x axis.
                            // Pendulum angle 0 is vertically down.
                            // dx is the sin component, dy is the cos component.
                            // angle = atan2(dx, dy)
                            state.angle = kotlin.math.atan2(dx, dy).toDouble()

                            // Update max angle during drag only if we want to show
                            // the current angle as max.
                            // Requirement says "max value for each swing", implies physics
                            // We can track the drag "peak" if desired,
                            // but let's stick to physics peaks or simple reset.
                            // Simply updating maxAngle during drag makes
                            // it equal to the current angle usually.
                            state.maxAngle = state.angle
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { state.hasUserInteracted = true }
                    )
                }
        ) {
            val pivot = Offset(size.width / 2f, size.height / 3f)
            // Visual length (pixels)
            val visualLength = size.height * 0.4f
            
            val bobX = pivot.x + visualLength * sin(state.angle).toFloat()
            val bobY = pivot.y + visualLength * cos(state.angle).toFloat()
            val bobPos = Offset(bobX, bobY)

            // Draw Rod
            drawLine(
                color = Color.LightGray,
                start = pivot,
                end = bobPos,
                strokeWidth = 4f
            )
            
            // Draw Pivot
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = pivot
            )

            // Draw Bob
            val bobRadius = 40f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF),
                        Color(0xFF008299)), // Neon Cyan
                    center = bobPos,
                    radius = bobRadius
                ),
                radius = bobRadius,
                center = bobPos
            )
        }
        
        // Status Overlay
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Convert radians to degrees for display
                val degrees = (state.angle * 180 / PI).toInt()
                // Format to 3-place integer (e.g. " 45", "123")
                val degreesStr = degrees.toString().padStart(3, ' ')

                // Format Velocity to 5.2f (e.g. " 1.23")
                // KMP workaround for String.format
                val v = state.angularVelocity
                val vSign = if (v < 0) "-" else " "
                val vAbs = kotlin.math.abs(v)
                val vInt = vAbs.toInt()
                val vDec = ((vAbs - vInt) * 100).toInt()
                // e.g. " 1.23" (length 5)
                val vStrRaw = "$vSign$vInt.${vDec.toString().padStart(2, '0')}"
                val vStr = vStrRaw.padStart(5, ' ')

                // Format Max Angle
                val maxDegrees = (state.maxAngle * 180 / PI).toInt()
                val maxDegreesStr = maxDegrees.toString().padStart(3, ' ')

                // Format Max Velocity
                val maxV = state.maxVelocity
                val maxVInt = maxV.toInt()
                val maxVDec = ((maxV - maxVInt) * 100).toInt()
                // e.g. " 1.23" (length 5) - Max Velocity is always positive
                val maxVStrRaw = " $maxVInt.${maxVDec.toString().padStart(2, '0')}"
                val maxVStr = maxVStrRaw.padStart(5, ' ')

                Text(text = "Angle: $degreesStr°")
                Text(text = "Max Angle: $maxDegreesStr°")
                Text(text = "Velocity: $vStr") // Note: Monospace font recommended for true alignment, but default is fine
                Text(text = "Max Velocity: $maxVStr")
            }
        }
        
        // Audio Hint
        if (!state.hasUserInteracted) {
            Text(
                text = "Click in the window if no sound is heard",
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }
    }
}
