@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.CanvasBasedWindow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Colors
val BackgroundColor = Color(0xFF2C3E50)
val ChairColor = Color(0xFF8B4513)
val ButtonColor = Color(0xFF27AE60)
val EliminatedAreaColor = Color(0xFF1A252F)

val PlayerColors = listOf(
    Color(0xFFE74C3C), // Red
    Color(0xFF3498DB), // Blue
    Color(0xFF2ECC71), // Green
    Color(0xFFF39C12), // Orange
    Color(0xFF9B59B6), // Purple
    Color(0xFF1ABC9C), // Teal
    Color(0xFFE91E63), // Pink
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFFEB3B), // Yellow
    Color(0xFFFF5722)  // Deep Orange
)

// Game constants
const val TOTAL_CHAIRS = 10
const val CHAIR_WIDTH = 50f
const val CHAIR_HEIGHT = 40f
const val PLAYER_RADIUS = 18f
const val CHAIR_SPACING = 80f
const val COLUMN_GAP = 70f

data class Player(
    val id: Int,
    val color: Color,
    val position: Float = 0f,
    val isEliminated: Boolean = false,
    val isSitting: Boolean = true,
    val chairIndex: Int = -1,
    val startPositionWhenStopped: Float = -1f,  // Track starting position for lap detection
    val eliminationAnimationProgress: Float = 0f,  // 0 = start, 1 = at eliminated area
    val eliminationStartX: Float = 0f,  // X position when eliminated
    val eliminationStartY: Float = 0f   // Y position when eliminated
)

// Enum to identify which segment of the stadium path a player is on
enum class PathSegment {
    RIGHT_STRAIGHT,
    BOTTOM_SEMI,
    LEFT_STRAIGHT,
    TOP_SEMI
}

data class Chair(
    val id: Int,
    val column: Int,
    val row: Int,
    val isRemoved: Boolean = false
)

enum class GameState {
    WAITING,
    PLAYING,
    STOPPING,
    ELIMINATING,
    GAME_OVER
}

fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        MusicalChairsGame()
    }
}

@Composable
fun MusicalChairsGame() {
    var gameState by remember { mutableStateOf(GameState.WAITING) }
    var players by remember { mutableStateOf(createInitialPlayers()) }
    var chairs by remember { mutableStateOf(createInitialChairs()) }

    // Animation loop - use Unit key so coroutine isn't cancelled on state changes
    LaunchedEffect(Unit) {
        while (true) {
            if (gameState == GameState.PLAYING || gameState == GameState.STOPPING) {
                if (gameState == GameState.PLAYING) {
                    // Normal movement around the oval
                    players = players.map { player ->
                        if (!player.isEliminated && !player.isSitting) {
                            val newPosition = (player.position + 0.008f) % 1f
                            player.copy(position = newPosition)
                        } else player
                    }
                } else if (gameState == GameState.STOPPING) {
                    // Get center coordinates for position calculations
                    val centerX = 800.dp.value / 2
                    val centerY = 500.dp.value / 2

                    val step = 0.008f
                    var eliminatedPlayer: Player? = null

                    // First pass: move players and check for lap completion
                    players = players.map { player ->
                        if (!player.isEliminated && !player.isSitting) {
                            val previousPosition = player.position
                            val newPosition = (player.position + step) % 1f

                            // Check if player completed a full lap without finding a chair
                            if (hasCompletedLap(newPosition, player.startPositionWhenStopped, previousPosition)) {
                                val elimPos = getOvalPosition(newPosition, centerX, centerY, chairs)
                                eliminatedPlayer = player
                                player.copy(
                                    position = newPosition,
                                    isEliminated = true,
                                    eliminationStartX = elimPos.x,
                                    eliminationStartY = elimPos.y
                                )
                            } else {
                                player.copy(position = newPosition)
                            }
                        } else player
                    }

                    // Second pass: check for chair claiming (only if no one was eliminated yet)
                    if (eliminatedPlayer == null) {
                        // Process players in order of their position to handle "first to arrive" rule
                        val activePlayers = players.filter { !it.isEliminated && !it.isSitting }
                            .sortedBy { it.position }

                        val claimedChairs = mutableSetOf<Int>()
                        val playerUpdates = mutableMapOf<Int, Pair<Boolean, Int>>()  // playerId -> (isSitting, chairIndex)

                        for (player in activePlayers) {
                            val claimableChair = findClaimableChair(player, chairs, players, centerX, centerY)
                            if (claimableChair != null && claimableChair.id !in claimedChairs) {
                                claimedChairs.add(claimableChair.id)
                                playerUpdates[player.id] = Pair(true, claimableChair.id)
                            }
                        }

                        // Apply chair claims
                        players = players.map { player ->
                            val update = playerUpdates[player.id]
                            if (update != null) {
                                player.copy(isSitting = update.first, chairIndex = update.second)
                            } else player
                        }
                    }

                    // Check if round should end
                    val activeChairs = chairs.filter { !it.isRemoved }
                    val allChairsFilled = activeChairs.all { chair -> isChairOccupied(chair, players) }
                    val someoneEliminated = eliminatedPlayer != null

                    if (someoneEliminated || allChairsFilled) {
                        // If all chairs filled but no one was eliminated by lap completion,
                        // find and eliminate the player who doesn't have a chair
                        if (allChairsFilled && eliminatedPlayer == null) {
                            val playerWithoutChair = players.find { !it.isEliminated && !it.isSitting }
                            if (playerWithoutChair != null) {
                                val elimPos = getOvalPosition(playerWithoutChair.position, centerX, centerY, chairs)
                                players = players.map { player ->
                                    if (player.id == playerWithoutChair.id) {
                                        player.copy(
                                            isEliminated = true,
                                            eliminationStartX = elimPos.x,
                                            eliminationStartY = elimPos.y
                                        )
                                    } else player
                                }
                            }
                        }

                        gameState = GameState.ELIMINATING
                    }
                }
            } else if (gameState == GameState.ELIMINATING) {
                // Animate the eliminated player moving to the side
                val animationSpeed = 0.03f  // Adjust for faster/slower animation
                var animationComplete = true

                players = players.map { player ->
                    if (player.isEliminated && player.eliminationAnimationProgress < 1f) {
                        animationComplete = false
                        player.copy(
                            eliminationAnimationProgress = (player.eliminationAnimationProgress + animationSpeed).coerceAtMost(1f)
                        )
                    } else player
                }

                if (animationComplete) {
                    val activePlayerCount = players.count { !it.isEliminated }
                    if (activePlayerCount <= 1) {
                        gameState = GameState.GAME_OVER
                    } else {
                        // Reset for next round - seat remaining players on chairs
                        players = seatPlayersOnChairs(players, chairs)
                        gameState = GameState.WAITING
                    }
                }
            }

            kotlinx.coroutines.delay(16)
        }
    }

    val activePlayerCount = players.count { !it.isEliminated }
    val activeChairCount = chairs.count { !it.isRemoved }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        // Game canvas
        Canvas(
            modifier = Modifier
                .size(800.dp, 500.dp)
                .align(Alignment.Center)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            drawChairs(chairs, centerX, centerY)
            drawPlayers(players, chairs, centerX, centerY, textMeasurer)
            drawEliminatedPlayers(players, size.width, textMeasurer)
        }

        // Title and status
        Text(
            text = "Musical Chairs - Players: $activePlayerCount | Chairs: $activeChairCount",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(20.dp)
        )

        // Control button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    when (gameState) {
                        GameState.WAITING -> {
                            players = players.map { it.copy(isSitting = false) }
                            players = assignOvalPositions(players)
                            chairs = removeOneChair(chairs)
                            gameState = GameState.PLAYING
                        }
                        GameState.PLAYING -> {
                            // Record each player's position when music stops for lap tracking
                            players = players.map { player ->
                                if (!player.isEliminated && !player.isSitting) {
                                    player.copy(startPositionWhenStopped = player.position)
                                } else player
                            }
                            gameState = GameState.STOPPING
                        }
                        GameState.GAME_OVER -> {
                            players = createInitialPlayers()
                            chairs = createInitialChairs()
                            gameState = GameState.WAITING
                        }
                        else -> {}
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = ButtonColor),
                enabled = gameState in listOf(GameState.WAITING, GameState.PLAYING, GameState.GAME_OVER)
            ) {
                Text(
                    text = when (gameState) {
                        GameState.WAITING -> "Start Music"
                        GameState.PLAYING -> "Stop Music"
                        GameState.STOPPING -> "Finding Chairs..."
                        GameState.ELIMINATING -> "Eliminating..."
                        GameState.GAME_OVER -> "Play Again"
                    },
                    color = Color.White,
                    fontSize = 18.sp
                )
            }

            if (gameState == GameState.GAME_OVER) {
                val winner = players.find { !it.isEliminated }
                Text(
                    text = "Winner: Player ${(winner?.id ?: 0) + 1}",
                    color = winner?.color ?: Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

fun createInitialPlayers(): List<Player> {
    return List(TOTAL_CHAIRS) { index ->
        Player(
            id = index,
            color = PlayerColors[index],
            isSitting = true,
            chairIndex = index
        )
    }
}

fun createInitialChairs(): List<Chair> {
    return List(TOTAL_CHAIRS) { index ->
        Chair(
            id = index,
            column = index % 2,
            row = index / 2
        )
    }
}

fun assignOvalPositions(players: List<Player>): List<Player> {
    val activePlayers = players.filter { !it.isEliminated }
    val count = activePlayers.size
    return players.map { player ->
        if (!player.isEliminated) {
            val index = activePlayers.indexOf(player)
            player.copy(position = index.toFloat() / count)
        } else player
    }
}

fun removeOneChair(chairs: List<Chair>): List<Chair> {
    val activeChairs = chairs.filter { !it.isRemoved }
    val maxRow = activeChairs.maxOfOrNull { it.row } ?: return chairs
    val bottomChairs = activeChairs.filter { it.row == maxRow }
    val chairToRemove = bottomChairs.firstOrNull()

    return chairs.map { chair ->
        if (chair.id == chairToRemove?.id) {
            chair.copy(isRemoved = true)
        } else chair
    }
}

fun getChairPosition(chair: Chair, centerX: Float, centerY: Float): Offset {
    val x = if (chair.column == 0) {
        centerX - COLUMN_GAP / 2 - CHAIR_WIDTH / 2
    } else {
        centerX + COLUMN_GAP / 2 + CHAIR_WIDTH / 2
    }
    val y = centerY + (2 - chair.row) * CHAIR_SPACING
    return Offset(x, y)
}

// Stadium path geometry constants - adjusts based on remaining chairs
fun getStadiumGeometry(chairs: List<Chair>): StadiumGeometry {
    // Chairs are at centerX ± 60, so path should be outside that
    // Using halfWidth = 100 puts the path at centerX ± 100
    val halfWidth = 100f

    // Calculate the extent of remaining chairs
    val activeChairs = chairs.filter { !it.isRemoved }
    val activeRows = activeChairs.map { it.row }.distinct()

    // Chair row Y positions: centerY + (2 - row) * CHAIR_SPACING
    // Row 0: +160, Row 1: +80, Row 2: 0, Row 3: -80, Row 4: -160
    val minRow = activeRows.minOrNull() ?: 0
    val maxRow = activeRows.maxOrNull() ?: 4

    // Calculate Y extent of remaining chairs
    val topY = (2 - maxRow) * CHAIR_SPACING  // Most negative Y (top of screen)
    val bottomY = (2 - minRow) * CHAIR_SPACING  // Most positive Y (bottom of screen)

    // halfStraight should cover from topY to bottomY plus buffer
    val chairExtent = (bottomY - topY) / 2
    val buffer = 40f  // Buffer above and below chairs
    val halfStraight = chairExtent + buffer

    val straightLength = halfStraight * 2

    // Semi-circle radius must equal halfWidth for smooth path connection
    val radius = halfWidth  // 100

    val perimeter = 2 * straightLength + 2 * PI.toFloat() * radius

    // Segment boundaries as proportions of total path (0 to 1)
    val seg1Len = halfStraight
    val seg2Len = PI.toFloat() * radius
    val seg3Len = straightLength
    val seg4Len = PI.toFloat() * radius

    val seg1End = seg1Len / perimeter
    val seg2End = (seg1Len + seg2Len) / perimeter
    val seg3End = (seg1Len + seg2Len + seg3Len) / perimeter
    val seg4End = (seg1Len + seg2Len + seg3Len + seg4Len) / perimeter

    // Also store the center offset (midpoint between top and bottom chairs)
    val centerOffset = (topY + bottomY) / 2

    return StadiumGeometry(halfWidth, radius, straightLength, halfStraight, perimeter, seg1End, seg2End, seg3End, seg4End, centerOffset)
}

data class StadiumGeometry(
    val halfWidth: Float,
    val radius: Float,
    val straightLength: Float,
    val halfStraight: Float,
    val perimeter: Float,
    val seg1End: Float,  // End of right straight (middle to bottom)
    val seg2End: Float,  // End of bottom semi-circle
    val seg3End: Float,  // End of left straight (bottom to top)
    val seg4End: Float,  // End of top semi-circle
    val centerOffset: Float = 0f  // Vertical offset to center path on remaining chairs
)

fun getPathSegment(t: Float, chairs: List<Chair>): PathSegment {
    val geom = getStadiumGeometry(chairs)
    return when {
        t < geom.seg1End -> PathSegment.RIGHT_STRAIGHT
        t < geom.seg2End -> PathSegment.BOTTOM_SEMI
        t < geom.seg3End -> PathSegment.LEFT_STRAIGHT
        t < geom.seg4End -> PathSegment.TOP_SEMI
        else -> PathSegment.RIGHT_STRAIGHT  // Wraps back to right straight
    }
}

fun getOvalPosition(t: Float, centerX: Float, centerY: Float, chairs: List<Chair>): Offset {
    val geom = getStadiumGeometry(chairs)
    val distance = t * geom.perimeter

    // Apply center offset to shift path vertically based on remaining chairs
    val adjustedCenterY = centerY + geom.centerOffset

    // Segment lengths
    val seg1Len = geom.halfStraight
    val seg2Len = PI.toFloat() * geom.radius
    val seg3Len = geom.straightLength
    val seg4Len = PI.toFloat() * geom.radius

    val seg1End = seg1Len
    val seg2End = seg1End + seg2Len
    val seg3End = seg2End + seg3Len
    val seg4End = seg3End + seg4Len

    return when {
        distance < seg1End -> {
            // Right straight, going down from middle
            Offset(centerX + geom.halfWidth, adjustedCenterY + distance)
        }
        distance < seg2End -> {
            // Bottom semi-circle (right to left, going through bottom)
            val arcDistance = distance - seg1End
            val angle = arcDistance / geom.radius  // 0 to π
            Offset(
                centerX + geom.radius * cos(angle),
                adjustedCenterY + geom.halfStraight + geom.radius * sin(angle)
            )
        }
        distance < seg3End -> {
            // Left straight, going up
            val straightDistance = distance - seg2End
            Offset(centerX - geom.halfWidth, adjustedCenterY + geom.halfStraight - straightDistance)
        }
        distance < seg4End -> {
            // Top semi-circle (left to right, going through top)
            val arcDistance = distance - seg3End
            val angle = PI.toFloat() + arcDistance / geom.radius  // π to 2π
            Offset(
                centerX + geom.radius * cos(angle),
                adjustedCenterY - geom.halfStraight + geom.radius * sin(angle)
            )
        }
        else -> {
            // Right straight, going down from top to middle
            val straightDistance = distance - seg4End
            Offset(centerX + geom.halfWidth, adjustedCenterY - geom.halfStraight + straightDistance)
        }
    }
}


// Check if a chair is currently occupied by a sitting player
fun isChairOccupied(chair: Chair, players: List<Player>): Boolean {
    return players.any { !it.isEliminated && it.isSitting && it.chairIndex == chair.id }
}

// Check if player has completed a full lap since music stopped
fun hasCompletedLap(currentPosition: Float, startPosition: Float, previousPosition: Float): Boolean {
    if (startPosition < 0) return false
    // Detect if we've crossed the start position (wrapped around)
    // This happens when previous position was just before start and current is just after
    val crossedForward = previousPosition < startPosition && currentPosition >= startPosition && (currentPosition - previousPosition) < 0.5f
    val crossedWrap = previousPosition > 0.9f && currentPosition < 0.1f && startPosition > previousPosition
    val crossedWrap2 = previousPosition > 0.9f && currentPosition < 0.1f && startPosition < currentPosition
    return crossedForward || crossedWrap || crossedWrap2
}

// Find a chair that a player can claim based on their current position
fun findClaimableChair(
    player: Player,
    chairs: List<Chair>,
    players: List<Player>,
    centerX: Float,
    centerY: Float
): Chair? {
    val segment = getPathSegment(player.position, chairs)

    // Can only claim chairs on straight segments
    if (segment != PathSegment.LEFT_STRAIGHT && segment != PathSegment.RIGHT_STRAIGHT) {
        return null
    }

    val column = if (segment == PathSegment.RIGHT_STRAIGHT) 1 else 0
    val playerPos = getOvalPosition(player.position, centerX, centerY, chairs)
    val tolerance = CHAIR_HEIGHT / 2 + PLAYER_RADIUS  // Y tolerance for alignment

    val activeChairs = chairs.filter { !it.isRemoved && it.column == column }

    return activeChairs.find { chair ->
        val chairPos = getChairPosition(chair, centerX, centerY)
        val yDistance = kotlin.math.abs(playerPos.y - chairPos.y)
        yDistance < tolerance && !isChairOccupied(chair, players)
    }
}


fun seatPlayersOnChairs(players: List<Player>, chairs: List<Chair>): List<Player> {
    // Keep players on their current chairs, just reset the lap tracking
    return players.map { player ->
        if (!player.isEliminated && player.isSitting) {
            // Player already has a chair, just reset lap tracking
            player.copy(startPositionWhenStopped = -1f)
        } else player
    }
}

fun DrawScope.drawChairs(chairs: List<Chair>, centerX: Float, centerY: Float) {
    chairs.forEach { chair ->
        val pos = getChairPosition(chair, centerX, centerY)
        val color = if (chair.isRemoved) BackgroundColor else ChairColor

        // Draw chair seat only
        drawRect(
            color = color,
            topLeft = Offset(pos.x - CHAIR_WIDTH / 2, pos.y - CHAIR_HEIGHT / 2),
            size = Size(CHAIR_WIDTH, CHAIR_HEIGHT)
        )
    }
}

fun DrawScope.drawPlayers(
    players: List<Player>,
    chairs: List<Chair>,
    centerX: Float,
    centerY: Float,
    textMeasurer: TextMeasurer
) {
    players.forEach { player ->
        if (!player.isEliminated) {
            val position = if (player.isSitting && player.chairIndex >= 0) {
                val chair = chairs.find { it.id == player.chairIndex }
                if (chair != null) {
                    getChairPosition(chair, centerX, centerY)
                } else {
                    getOvalPosition(player.position, centerX, centerY, chairs)
                }
            } else {
                getOvalPosition(player.position, centerX, centerY, chairs)
            }

            drawCircle(
                color = player.color,
                radius = PLAYER_RADIUS,
                center = position
            )

            // Draw player number
            val numberText = (player.id + 1).toString()
            val textLayoutResult = textMeasurer.measure(
                text = numberText,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    position.x - textLayoutResult.size.width / 2,
                    position.y - textLayoutResult.size.height / 2
                )
            )
        }
    }
}

// Easing function for smooth animation (ease-out cubic)
fun easeOutCubic(t: Float): Float {
    val t1 = 1 - t
    return 1 - t1 * t1 * t1
}

fun DrawScope.drawEliminatedPlayers(players: List<Player>, screenWidth: Float, textMeasurer: TextMeasurer) {
    val eliminatedPlayers = players.filter { it.isEliminated }
    val targetX = screenWidth - 60
    val startY = 50f

    // Draw background for eliminated players area
    if (eliminatedPlayers.isNotEmpty()) {
        drawRect(
            color = EliminatedAreaColor,
            topLeft = Offset(targetX - 30, startY - 30),
            size = Size(70f, eliminatedPlayers.size * 45f + 40f)
        )
    }

    eliminatedPlayers.forEachIndexed { index, player ->
        val targetY = startY + index * 45f

        // Interpolate position based on animation progress
        val progress = easeOutCubic(player.eliminationAnimationProgress)
        val currentX = player.eliminationStartX + (targetX - player.eliminationStartX) * progress
        val currentY = player.eliminationStartY + (targetY - player.eliminationStartY) * progress
        val position = Offset(currentX, currentY)

        drawCircle(
            color = player.color,
            radius = PLAYER_RADIUS,
            center = position
        )

        // Draw player number
        val numberText = (player.id + 1).toString()
        val textLayoutResult = textMeasurer.measure(
            text = numberText,
            style = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                position.x - textLayoutResult.size.width / 2,
                position.y - textLayoutResult.size.height / 2
            )
        )
    }
}
