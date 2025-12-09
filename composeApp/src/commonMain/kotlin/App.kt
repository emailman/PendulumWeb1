import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Physics Constants
const val GRAVITY = 9.81
const val DAMPING = 0.01 // Air resistance
const val LENGTH_METERS = 2.0 // Simulation length for physics

class PendulumState {
    var angle by mutableStateOf(PI / 4)
    var angularVelocity by mutableStateOf(0.0)
    var angularAcceleration by mutableStateOf(0.0)
    var isDragging by mutableStateOf(false)
}

@Composable
fun App() {
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
                        // Semi-implicit Euler Integration
                        val alpha = -(GRAVITY / LENGTH_METERS) * sin(state.angle) -
                                DAMPING * state.angularVelocity
                        state.angularAcceleration = alpha
                        state.angularVelocity += alpha * dt
                        state.angle += state.angularVelocity * dt
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
                            val center = Offset(size.width / 2f, size.height / 3f) // Pivot point
                            val touch = change.position
                            val dx = touch.x - center.x
                            val dy = touch.y - center.y
                            
                            // atan2(x, y) gives angle from vertical down if we treat y as down
                            // standard atan2(y, x) is from x axis.
                            // Pendulum angle 0 is vertical down.
                            // dx is the sin component, dy is the cos component.
                            // angle = atan2(dx, dy)
                            state.angle = kotlin.math.atan2(dx, dy).toDouble()
                        }
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
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF008299)), // Neon Cyan
                    center = bobPos,
                    radius = bobRadius
                ),
                radius = bobRadius,
                center = bobPos
            )
        }
    }
}
