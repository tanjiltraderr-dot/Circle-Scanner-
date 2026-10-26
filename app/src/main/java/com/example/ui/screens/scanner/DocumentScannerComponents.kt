package com.example.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandBlue
import com.example.utils.DocumentFilter
import com.example.utils.DocumentQuad
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

val CamScannerTeal = Color(0xFF00A884)
val CamScannerTealBright = Color(0xFF00BFA5)
val CamScannerDarkBg = Color(0xFF121212)
val CamScannerSurface = Color(0xFF1E1E1E)

/**
 * Live Camera Document Detection Overlay.
 * When a document boundary is detected by CameraX ImageAnalysis:
 * - Maps buffer coordinates to screen using ScaleType.FILL_CENTER
 * - Draws a clean outline over the paper (green when stable, blue when moving)
 * - Highlights the 4 corners with subtle corner handles
 * When no reliable boundary is detected:
 * - Displays standard corner bracket framing reticle.
 */
@Composable
fun LiveDocumentDetectionOverlay(
    detectedQuad: DocumentQuad?,
    bufferWidth: Int,
    bufferHeight: Int,
    isReliable: Boolean,
    isStable: Boolean,
    isFrontCamera: Boolean = false,
    modifier: Modifier = Modifier
) {
    val outlineColor by animateColorAsState(
        targetValue = when {
            isStable -> Color(0xFF10B981) // Crisp Emerald Green for stable reliable document
            isReliable -> Color(0xFF00BFA5) // CamScanner Teal for live tracking/detecting
            else -> Color.White.copy(alpha = 0.5f)
        },
        animationSpec = tween(150),
        label = "outline_color"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height

        if (detectedQuad != null && isReliable && bufferWidth > 0 && bufferHeight > 0) {
            val scale = max(viewW / bufferWidth.toFloat(), viewH / bufferHeight.toFloat())
            val scaledW = bufferWidth * scale
            val scaledH = bufferHeight * scale
            val offsetX = (viewW - scaledW) / 2f
            val offsetY = (viewH - scaledH) / 2f

            val mapX = { x: Float ->
                val effX = if (isFrontCamera) (bufferWidth - x) else x
                offsetX + effX * scale
            }
            val mapY = { y: Float -> offsetY + y * scale }

            val pTL = Offset(mapX(detectedQuad.topLeft.x), mapY(detectedQuad.topLeft.y))
            val pTR = Offset(mapX(detectedQuad.topRight.x), mapY(detectedQuad.topRight.y))
            val pBR = Offset(mapX(detectedQuad.bottomRight.x), mapY(detectedQuad.bottomRight.y))
            val pBL = Offset(mapX(detectedQuad.bottomLeft.x), mapY(detectedQuad.bottomLeft.y))

            // 1. Soft translucent highlight over the paper area
            val polyPath = Path().apply {
                moveTo(pTL.x, pTL.y)
                lineTo(pTR.x, pTR.y)
                lineTo(pBR.x, pBR.y)
                lineTo(pBL.x, pBL.y)
                close()
            }
            drawPath(
                polyPath,
                color = outlineColor.copy(alpha = if (isStable) 0.18f else 0.09f)
            )

            // 2. Continuous boundary stroke
            val strokeWidth = if (isStable) 2.5.dp.toPx() else 1.8.dp.toPx()
            drawPath(
                polyPath,
                color = outlineColor,
                style = Stroke(width = strokeWidth)
            )

            // 3. Highlighted corner bracket handles on the 4 detected corners
            val cornerHandleRadius = if (isStable) 7.dp.toPx() else 5.dp.toPx()
            listOf(pTL, pTR, pBR, pBL).forEach { pt ->
                drawCircle(Color.White, radius = cornerHandleRadius + 2.5.dp.toPx(), center = pt)
                drawCircle(outlineColor, radius = cornerHandleRadius, center = pt)
            }
        }
    }
}

/**
 * Static Document Frame Overlay for fallback or standalone use.
 */
@Composable
fun DocumentFrameWithCorners(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerLen = 38.dp.toPx()
        val strokeW = 4.dp.toPx()
        val cornerColor = Color.White
        val boundaryColor = Color.White.copy(alpha = 0.55f)

        // Outer subtle rectangular paper outline
        drawRoundRect(
            color = boundaryColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = Stroke(width = 1.8.dp.toPx())
        )

        // Top Left corner bracket
        drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW, StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLen), strokeW, StrokeCap.Round)

        // Top Right corner bracket
        drawLine(cornerColor, Offset(w, 0f), Offset(w - cornerLen, 0f), strokeW, StrokeCap.Round)
        drawLine(cornerColor, Offset(w, 0f), Offset(w, cornerLen), strokeW, StrokeCap.Round)

        // Bottom Left corner bracket
        drawLine(cornerColor, Offset(0f, h), Offset(cornerLen, h), strokeW, StrokeCap.Round)
        drawLine(cornerColor, Offset(0f, h), Offset(0f, h - cornerLen), strokeW, StrokeCap.Round)

        // Bottom Right corner bracket
        drawLine(cornerColor, Offset(w, h), Offset(w - cornerLen, h), strokeW, StrokeCap.Round)
        drawLine(cornerColor, Offset(w, h), Offset(w - cornerLen, h), strokeW, StrokeCap.Round)
    }
}

private enum class DragTarget {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT,
    EDGE_TOP, EDGE_RIGHT, EDGE_BOTTOM, EDGE_LEFT
}

/**
 * Interactive Corner Dragging Quad Editor with Pixel-Perfect Aspect Mapping,
 * CamScanner-style teal corner handles, edge midpoints, and Live Magnifier / Loupe preview during dragging.
 */
@Composable
fun InteractiveQuadEditor(
    bitmap: Bitmap,
    quad: DocumentQuad,
    onQuadChanged: (DocumentQuad) -> Unit,
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeDragTarget by remember { mutableStateOf(DragTarget.NONE) }
    var touchPointOnBitmap by remember { mutableStateOf<PointF?>(null) }
    var dragStartQuad by remember { mutableStateOf(quad) }
    var dragStartPoint by remember { mutableStateOf(Offset.Zero) }

    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()

    // Calculate exact fitted display rect
    val (drawW, drawH, offsetX, offsetY, scale) = remember(containerSize, bmpW, bmpH) {
        if (containerSize.width > 0 && containerSize.height > 0 && bmpW > 0 && bmpH > 0) {
            val scaleVal = min(containerSize.width.toFloat() / bmpW, containerSize.height.toFloat() / bmpH)
            val dW = bmpW * scaleVal
            val dH = bmpH * scaleVal
            val oX = (containerSize.width - dW) / 2f
            val oY = (containerSize.height - dH) / 2f
            floatArrayOf(dW, dH, oX, oY, scaleVal)
        } else {
            floatArrayOf(0f, 0f, 0f, 0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(quad, scale, offsetX, offsetY, bmpW, bmpH) {
                detectDragGestures(
                    onDragStart = { startPos ->
                        if (scale <= 0f) return@detectDragGestures
                        val tx = startPos.x
                        val ty = startPos.y
                        dragStartQuad = quad
                        dragStartPoint = startPos

                        // Screen positions of 4 corners
                        val sTL = Offset(offsetX + quad.topLeft.x * scale, offsetY + quad.topLeft.y * scale)
                        val sTR = Offset(offsetX + quad.topRight.x * scale, offsetY + quad.topRight.y * scale)
                        val sBR = Offset(offsetX + quad.bottomRight.x * scale, offsetY + quad.bottomRight.y * scale)
                        val sBL = Offset(offsetX + quad.bottomLeft.x * scale, offsetY + quad.bottomLeft.y * scale)

                        // Screen positions of 4 edge midpoints
                        val mTop = Offset((sTL.x + sTR.x) / 2f, (sTL.y + sTR.y) / 2f)
                        val mRight = Offset((sTR.x + sBR.x) / 2f, (sTR.y + sBR.y) / 2f)
                        val mBottom = Offset((sBL.x + sBR.x) / 2f, (sBL.y + sBR.y) / 2f)
                        val mLeft = Offset((sTL.x + sBL.x) / 2f, (sTL.y + sBL.y) / 2f)

                        val cornerHitRadius = 52.dp.toPx()
                        val edgeHitRadius = 40.dp.toPx()

                        val dTL = hypot((tx - sTL.x).toDouble(), (ty - sTL.y).toDouble())
                        val dTR = hypot((tx - sTR.x).toDouble(), (ty - sTR.y).toDouble())
                        val dBR = hypot((tx - sBR.x).toDouble(), (ty - sBR.y).toDouble())
                        val dBL = hypot((tx - sBL.x).toDouble(), (ty - sBL.y).toDouble())

                        val dTop = hypot((tx - mTop.x).toDouble(), (ty - mTop.y).toDouble())
                        val dRight = hypot((tx - mRight.x).toDouble(), (ty - mRight.y).toDouble())
                        val dBottom = hypot((tx - mBottom.x).toDouble(), (ty - mBottom.y).toDouble())
                        val dLeft = hypot((tx - mLeft.x).toDouble(), (ty - mLeft.y).toDouble())

                        val minCornerD = minOf(dTL, dTR, dBR, dBL)
                        val minEdgeD = minOf(dTop, dRight, dBottom, dLeft)

                        activeDragTarget = when {
                            minCornerD <= cornerHitRadius && minCornerD == dTL -> DragTarget.TOP_LEFT
                            minCornerD <= cornerHitRadius && minCornerD == dTR -> DragTarget.TOP_RIGHT
                            minCornerD <= cornerHitRadius && minCornerD == dBR -> DragTarget.BOTTOM_RIGHT
                            minCornerD <= cornerHitRadius && minCornerD == dBL -> DragTarget.BOTTOM_LEFT
                            minEdgeD <= edgeHitRadius && minEdgeD == dTop -> DragTarget.EDGE_TOP
                            minEdgeD <= edgeHitRadius && minEdgeD == dRight -> DragTarget.EDGE_RIGHT
                            minEdgeD <= edgeHitRadius && minEdgeD == dBottom -> DragTarget.EDGE_BOTTOM
                            minEdgeD <= edgeHitRadius && minEdgeD == dLeft -> DragTarget.EDGE_LEFT
                            else -> DragTarget.NONE
                        }

                        if (activeDragTarget != DragTarget.NONE) {
                            val bmpX = ((tx - offsetX) / scale).coerceIn(0f, bmpW)
                            val bmpY = ((ty - offsetY) / scale).coerceIn(0f, bmpH)
                            touchPointOnBitmap = PointF(bmpX, bmpY)
                        }
                    },
                    onDragEnd = {
                        activeDragTarget = DragTarget.NONE
                        touchPointOnBitmap = null
                    },
                    onDragCancel = {
                        activeDragTarget = DragTarget.NONE
                        touchPointOnBitmap = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (scale <= 0f || activeDragTarget == DragTarget.NONE) return@detectDragGestures

                        val tx = change.position.x
                        val ty = change.position.y

                        val bmpX = ((tx - offsetX) / scale).coerceIn(0f, bmpW)
                        val bmpY = ((ty - offsetY) / scale).coerceIn(0f, bmpH)
                        touchPointOnBitmap = PointF(bmpX, bmpY)

                        val dxBmp = (tx - dragStartPoint.x) / scale
                        val dyBmp = (ty - dragStartPoint.y) / scale

                        val newQuad = when (activeDragTarget) {
                            DragTarget.TOP_LEFT -> quad.copy(topLeft = PointF(bmpX, bmpY))
                            DragTarget.TOP_RIGHT -> quad.copy(topRight = PointF(bmpX, bmpY))
                            DragTarget.BOTTOM_RIGHT -> quad.copy(bottomRight = PointF(bmpX, bmpY))
                            DragTarget.BOTTOM_LEFT -> quad.copy(bottomLeft = PointF(bmpX, bmpY))
                            DragTarget.EDGE_TOP -> {
                                val newTL_Y = (dragStartQuad.topLeft.y + dyBmp).coerceIn(0f, bmpH)
                                val newTR_Y = (dragStartQuad.topRight.y + dyBmp).coerceIn(0f, bmpH)
                                quad.copy(
                                    topLeft = PointF(quad.topLeft.x, newTL_Y),
                                    topRight = PointF(quad.topRight.x, newTR_Y)
                                )
                            }
                            DragTarget.EDGE_BOTTOM -> {
                                val newBL_Y = (dragStartQuad.bottomLeft.y + dyBmp).coerceIn(0f, bmpH)
                                val newBR_Y = (dragStartQuad.bottomRight.y + dyBmp).coerceIn(0f, bmpH)
                                quad.copy(
                                    bottomLeft = PointF(quad.bottomLeft.x, newBL_Y),
                                    bottomRight = PointF(quad.bottomRight.x, newBR_Y)
                                )
                            }
                            DragTarget.EDGE_LEFT -> {
                                val newTL_X = (dragStartQuad.topLeft.x + dxBmp).coerceIn(0f, bmpW)
                                val newBL_X = (dragStartQuad.bottomLeft.x + dxBmp).coerceIn(0f, bmpW)
                                quad.copy(
                                    topLeft = PointF(newTL_X, quad.topLeft.y),
                                    bottomLeft = PointF(newBL_X, quad.bottomLeft.y)
                                )
                            }
                            DragTarget.EDGE_RIGHT -> {
                                val newTR_X = (dragStartQuad.topRight.x + dxBmp).coerceIn(0f, bmpW)
                                val newBR_X = (dragStartQuad.bottomRight.x + dxBmp).coerceIn(0f, bmpW)
                                quad.copy(
                                    topRight = PointF(newTR_X, quad.topRight.y),
                                    bottomRight = PointF(newBR_X, quad.bottomRight.y)
                                )
                            }
                            else -> quad
                        }
                        onQuadChanged(newQuad)
                    }
                )
            }
    ) {
        // Image rendered in center
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Document Image to Crop",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay canvas with edges, semi-transparent mask, and CamScanner corner handles
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (scale > 0f) {
                val pTL = Offset(offsetX + quad.topLeft.x * scale, offsetY + quad.topLeft.y * scale)
                val pTR = Offset(offsetX + quad.topRight.x * scale, offsetY + quad.topRight.y * scale)
                val pBR = Offset(offsetX + quad.bottomRight.x * scale, offsetY + quad.bottomRight.y * scale)
                val pBL = Offset(offsetX + quad.bottomLeft.x * scale, offsetY + quad.bottomLeft.y * scale)

                // Fill polygon with signature CamScanner soft teal highlight
                val polyPath = Path().apply {
                    moveTo(pTL.x, pTL.y)
                    lineTo(pTR.x, pTR.y)
                    lineTo(pBR.x, pBR.y)
                    lineTo(pBL.x, pBL.y)
                    close()
                }
                drawPath(polyPath, color = CamScannerTealBright.copy(alpha = 0.16f))
                drawPath(polyPath, color = CamScannerTealBright, style = Stroke(width = 2.5.dp.toPx()))

                // 4 CamScanner signature corner handles: Outer white ring, teal body, white center
                val cornerRadiusOuter = 16.dp.toPx()
                val cornerRadiusInner = 12.dp.toPx()
                val cornerDotRadius = 4.5.dp.toPx()

                val corners = listOf(
                    pTL to (activeDragTarget == DragTarget.TOP_LEFT),
                    pTR to (activeDragTarget == DragTarget.TOP_RIGHT),
                    pBR to (activeDragTarget == DragTarget.BOTTOM_RIGHT),
                    pBL to (activeDragTarget == DragTarget.BOTTOM_LEFT)
                )

                corners.forEach { (pt, isActive) ->
                    val rScale = if (isActive) 1.25f else 1f
                    drawCircle(color = Color.White, radius = cornerRadiusOuter * rScale, center = pt)
                    drawCircle(color = CamScannerTealBright, radius = cornerRadiusInner * rScale, center = pt)
                    drawCircle(color = Color.White, radius = cornerDotRadius * rScale, center = pt)
                }

                // Midpoints on each edge for 8-point CamScanner boundary manipulation
                val midTop = Offset((pTL.x + pTR.x) / 2f, (pTL.y + pTR.y) / 2f)
                val midRight = Offset((pTR.x + pBR.x) / 2f, (pTR.y + pBR.y) / 2f)
                val midBottom = Offset((pBL.x + pBR.x) / 2f, (pBL.y + pBR.y) / 2f)
                val midLeft = Offset((pTL.x + pBL.x) / 2f, (pTL.y + pBL.y) / 2f)

                listOf(
                    midTop to (activeDragTarget == DragTarget.EDGE_TOP),
                    midRight to (activeDragTarget == DragTarget.EDGE_RIGHT),
                    midBottom to (activeDragTarget == DragTarget.EDGE_BOTTOM),
                    midLeft to (activeDragTarget == DragTarget.EDGE_LEFT)
                ).forEach { (mid, isActive) ->
                    val r = if (isActive) 9.dp.toPx() else 7.dp.toPx()
                    drawCircle(color = Color.White, radius = r, center = mid)
                    drawCircle(color = CamScannerTealBright, radius = r - 2.dp.toPx(), center = mid)
                }
            }
        }

        // Live Magnifier / Loupe preview during active drag (placed dynamically away from touch)
        if (activeDragTarget != DragTarget.NONE && touchPointOnBitmap != null) {
            val pt = touchPointOnBitmap!!
            val loupeSize = 110.dp

            val loupeAlignment = if (pt.y < bmpH * 0.45f) Alignment.BottomCenter else Alignment.TopCenter

            Box(
                modifier = Modifier
                    .align(loupeAlignment)
                    .padding(24.dp)
                    .size(loupeSize)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.5.dp, CamScannerTealBright, CircleShape)
            ) {
                val cropSide = (bmpW * 0.18f).toInt().coerceIn(40, 260)
                val startX = (pt.x - cropSide / 2).toInt().coerceIn(0, (bmpW - cropSide).toInt().coerceAtLeast(0))
                val startY = (pt.y - cropSide / 2).toInt().coerceIn(0, (bmpH - cropSide).toInt().coerceAtLeast(0))

                val subBmp = remember(pt.x, pt.y) {
                    try {
                        Bitmap.createBitmap(
                            bitmap,
                            startX,
                            startY,
                            cropSide.coerceAtMost(bitmap.width - startX),
                            cropSide.coerceAtMost(bitmap.height - startY)
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                if (subBmp != null) {
                    Image(
                        bitmap = subBmp.asImageBitmap(),
                        contentDescription = "Magnifier Loupe",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Crosshair at center of loupe
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val chLen = 14.dp.toPx()
                    drawLine(Color(0xFFEF4444), Offset(cx - chLen, cy), Offset(cx + chLen, cy), strokeWidth = 2.dp.toPx())
                    drawLine(Color(0xFFEF4444), Offset(cx, cy - chLen), Offset(cx, cy + chLen), strokeWidth = 2.dp.toPx())
                    drawCircle(Color(0xFFEF4444), radius = 3.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.5.dp.toPx()))
                }
            }
        }
    }
}

/**
 * CamScanner Reference Video Crop Bottom Bar (Frame 00:05 - 00:09)
 * - Left (rotate CCW)
 * - Right (rotate CW)
 * - All (full image bounds)
 * - Reset (restore auto-detected corners)
 * - Next (prominent circular teal FAB)
 */
@Composable
fun CamScannerCropBottomBar(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onRetake: () -> Unit,
    onSelectAll: () -> Unit,
    onReset: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CamScannerDarkBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Tool buttons group: Left, Right, Retake, All, Reset
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CamScannerCropToolItem(
                icon = androidx.compose.material.icons.Icons.Default.RotateLeft,
                label = "Left",
                onClick = onRotateLeft
            )
            CamScannerCropToolItem(
                icon = androidx.compose.material.icons.Icons.Default.RotateRight,
                label = "Right",
                onClick = onRotateRight
            )
            CamScannerCropToolItem(
                icon = androidx.compose.material.icons.Icons.Default.CameraAlt,
                label = "Retake",
                onClick = onRetake
            )
            CamScannerCropToolItem(
                icon = androidx.compose.material.icons.Icons.Default.CropFree,
                label = "All",
                onClick = onSelectAll
            )
            CamScannerCropToolItem(
                icon = androidx.compose.material.icons.Icons.Default.RestartAlt,
                label = "Reset",
                onClick = onReset
            )
        }

        // Circular Teal Next Button with right arrow
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(CamScannerTeal)
                    .clickable { onNext() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            androidx.compose.material3.Text(
                text = "Next",
                color = CamScannerTealBright,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CamScannerCropToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp
        )
    }
}

/**
 * CamScanner Reference Video Enhance Bottom Bar (Frame 00:10 - 00:17)
 * - Horizontal filter carousel: Original, Lighten, Magic Color, No Shadow, B&W, Grayscale
 * - Bottom tools: Left, Markup, Extract Text, Recrop, Done (circular teal checkmark FAB)
 */
@Composable
fun CamScannerEnhanceBottomBar(
    currentFilter: DocumentFilter,
    onFilterSelected: (DocumentFilter) -> Unit,
    onRotateLeft: () -> Unit,
    onMarkup: () -> Unit,
    onExtractText: () -> Unit,
    onRecrop: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CamScannerDarkBg)
    ) {
        // Filter Carousel Strip (Frame 00:13)
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(DocumentFilter.entries) { filter ->
                val isSelected = currentFilter == filter
                CamScannerFilterItem(
                    filter = filter,
                    isSelected = isSelected,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Bottom Tools: Left, Markup, Extract Text, Recrop, and Done FAB
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CamScannerCropToolItem(
                    icon = androidx.compose.material.icons.Icons.Default.RotateLeft,
                    label = "Left",
                    onClick = onRotateLeft
                )
                CamScannerCropToolItem(
                    icon = androidx.compose.material.icons.Icons.Default.Draw,
                    label = "Markup",
                    onClick = onMarkup
                )
                CamScannerCropToolItem(
                    icon = androidx.compose.material.icons.Icons.Default.TextFields,
                    label = "Extract Text",
                    onClick = onExtractText
                )
                CamScannerCropToolItem(
                    icon = androidx.compose.material.icons.Icons.Default.Crop,
                    label = "Recrop",
                    onClick = onRecrop
                )
            }

            // Circular Teal Done Button with checkmark
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(CamScannerTeal)
                        .clickable { onDone() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                androidx.compose.material3.Text(
                    text = "Done",
                    color = CamScannerTealBright,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CamScannerFilterItem(
    filter: DocumentFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) CamScannerTeal.copy(alpha = 0.25f) else Color(0xFF262626))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) CamScannerTealBright else Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val filterIcon = when (filter) {
                DocumentFilter.ORIGINAL -> androidx.compose.material.icons.Icons.Default.Image
                DocumentFilter.GRAYSCALE -> androidx.compose.material.icons.Icons.Default.Gradient
                DocumentFilter.BLACK_WHITE -> androidx.compose.material.icons.Icons.Default.Contrast
                DocumentFilter.DOCUMENT_CONTRAST -> androidx.compose.material.icons.Icons.Default.Brightness6
                DocumentFilter.NOISE_REDUCTION -> androidx.compose.material.icons.Icons.Default.BlurOn
                DocumentFilter.SHARPEN -> androidx.compose.material.icons.Icons.Default.Details
                DocumentFilter.MAGIC_COLOR -> androidx.compose.material.icons.Icons.Default.AutoFixHigh
                DocumentFilter.CAMSCANNER -> androidx.compose.material.icons.Icons.Default.AutoAwesome
                DocumentFilter.NO_SHADOW -> androidx.compose.material.icons.Icons.Default.WbSunny
            }
            androidx.compose.material3.Icon(
                imageVector = filterIcon,
                contentDescription = filter.label,
                tint = if (isSelected) CamScannerTealBright else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(24.dp)
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(14.dp)
                        .background(CamScannerTeal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = filter.label,
            color = if (isSelected) CamScannerTealBright else Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp
        )
    }
}

