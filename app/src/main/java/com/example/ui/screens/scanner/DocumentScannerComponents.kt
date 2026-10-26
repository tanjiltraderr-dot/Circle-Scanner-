package com.example.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ToolCategory
import com.example.domain.model.ToolItem
import com.example.domain.model.ToolRegistry
import com.example.ui.theme.BrandBlue
import com.example.utils.DocumentFilter
import com.example.utils.DocumentQuad
import com.example.utils.ScannedPageItem
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

/**
 * QR Code vs Barcode mode selector
 */
enum class QrBarcodeSubMode(val label: String) {
    QR_CODE("QR Code"),
    BARCODE("Barcode")
}

/**
 * Permanent, Rock-Solid Category Viewfinder.
 * Replaces jittering live edge overlays with a clean, stable bounding border tailored to each category.
 * Captures within this frame, then runs high-accuracy auto-crop and perspective correction.
 */
@Composable
fun PermanentCategoryViewfinder(
    mode: CameraAppMode,
    qrBarcodeSubMode: QrBarcodeSubMode = QrBarcodeSubMode.QR_CODE,
    idCardSide: String = "Front Side",
    pageCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_progress"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight

        val (boxWidth, boxHeight, cornerRadius, guideTitle) = remember(mode, qrBarcodeSubMode, screenW, screenH) {
            when (mode) {
                CameraAppMode.SCANNER, CameraAppMode.PDF -> {
                    val w = screenW * 0.85f
                    val h = minOf(screenH * 0.65f, w * 1.38f)
                    val title = if (mode == CameraAppMode.PDF && pageCount > 0) {
                        "Page ${pageCount + 1} • Align document inside border"
                    } else {
                        "Align document inside border"
                    }
                    CategoryViewfinderDimensions(w, h, 14.dp, title)
                }
                CameraAppMode.ID_CARD -> {
                    val w = screenW * 0.88f
                    val h = w / 1.586f // Standard CR80 ID Card aspect ratio
                    CategoryViewfinderDimensions(w, h, 16.dp, "Place $idCardSide of NID / ID Card inside border")
                }
                CameraAppMode.QR_BARCODE -> {
                    if (qrBarcodeSubMode == QrBarcodeSubMode.QR_CODE) {
                        val side = minOf(screenW * 0.72f, 260.dp)
                        CategoryViewfinderDimensions(side, side, 16.dp, "Align QR code inside square border")
                    } else {
                        val w = screenW * 0.86f
                        val h = 135.dp
                        CategoryViewfinderDimensions(w, h, 14.dp, "Align barcode inside rectangular border")
                    }
                }
                CameraAppMode.PHOTO -> {
                    val w = screenW * 0.88f
                    val h = screenH * 0.62f
                    CategoryViewfinderDimensions(w, h, 12.dp, "Frame photo inside border")
                }
            }
        }

        // 1. Darkened vignette cutout & permanent border
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalW = size.width
            val totalH = size.height

            val left = (totalW - boxWidth.toPx()) / 2f
            val top = (totalH - boxHeight.toPx()) / 2f - 24.dp.toPx()
            val right = left + boxWidth.toPx()
            val bottom = top + boxHeight.toPx()

            // Outer dark translucent mask
            val cutoutPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(totalW, 0f)
                lineTo(totalW, totalH)
                lineTo(0f, totalH)
                close()
                val r = cornerRadius.toPx()
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        radiusX = r,
                        radiusY = r
                    )
                )
            }
            drawPath(cutoutPath, color = Color.Black.copy(alpha = 0.52f))

            // 2. Permanent border stroke
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(boxWidth.toPx(), boxHeight.toPx()),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                style = Stroke(width = 1.8.dp.toPx())
            )

            // 3. Prominent Corner Brackets (CamScanner Teal Bright)
            val cornerLen = 30.dp.toPx()
            val bracketStroke = 4.dp.toPx()
            val bracketColor = CamScannerTealBright

            // Top-Left
            drawLine(bracketColor, Offset(left, top + cornerLen), Offset(left, top + 10.dp.toPx()), bracketStroke, StrokeCap.Round)
            drawArc(bracketColor, 180f, 90f, false, Offset(left, top), androidx.compose.ui.geometry.Size(20.dp.toPx(), 20.dp.toPx()), style = Stroke(bracketStroke, cap = StrokeCap.Round))
            drawLine(bracketColor, Offset(left + 10.dp.toPx(), top), Offset(left + cornerLen, top), bracketStroke, StrokeCap.Round)

            // Top-Right
            drawLine(bracketColor, Offset(right - cornerLen, top), Offset(right - 10.dp.toPx(), top), bracketStroke, StrokeCap.Round)
            drawArc(bracketColor, 270f, 90f, false, Offset(right - 20.dp.toPx(), top), androidx.compose.ui.geometry.Size(20.dp.toPx(), 20.dp.toPx()), style = Stroke(bracketStroke, cap = StrokeCap.Round))
            drawLine(bracketColor, Offset(right, top + 10.dp.toPx()), Offset(right, top + cornerLen), bracketStroke, StrokeCap.Round)

            // Bottom-Right
            drawLine(bracketColor, Offset(right, bottom - cornerLen), Offset(right, bottom - 10.dp.toPx()), bracketStroke, StrokeCap.Round)
            drawArc(bracketColor, 0f, 90f, false, Offset(right - 20.dp.toPx(), bottom - 20.dp.toPx()), androidx.compose.ui.geometry.Size(20.dp.toPx(), 20.dp.toPx()), style = Stroke(bracketStroke, cap = StrokeCap.Round))
            drawLine(bracketColor, Offset(right - 10.dp.toPx(), bottom), Offset(right - cornerLen, bottom), bracketStroke, StrokeCap.Round)

            // Bottom-Left
            drawLine(bracketColor, Offset(left + cornerLen, bottom), Offset(left + 10.dp.toPx(), bottom), bracketStroke, StrokeCap.Round)
            drawArc(bracketColor, 90f, 90f, false, Offset(left, bottom - 20.dp.toPx()), androidx.compose.ui.geometry.Size(20.dp.toPx(), 20.dp.toPx()), style = Stroke(bracketStroke, cap = StrokeCap.Round))
            drawLine(bracketColor, Offset(left, bottom - 10.dp.toPx()), Offset(left, bottom - cornerLen), bracketStroke, StrokeCap.Round)

            // 4. Subtle Category-Specific Watermarks
            when (mode) {
                CameraAppMode.ID_CARD -> {
                    val chipW = 38.dp.toPx()
                    val chipH = 28.dp.toPx()
                    val chipX = left + 24.dp.toPx()
                    val chipY = top + (boxHeight.toPx() - chipH) / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(chipX, chipY),
                        size = androidx.compose.ui.geometry.Size(chipW, chipH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    val avatarR = 24.dp.toPx()
                    val avatarCenterX = right - 38.dp.toPx()
                    val avatarCenterY = top + (boxHeight.toPx()) / 2f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = avatarR,
                        center = Offset(avatarCenterX, avatarCenterY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                CameraAppMode.QR_BARCODE -> {
                    if (qrBarcodeSubMode == QrBarcodeSubMode.QR_CODE) {
                        val laserY = top + boxHeight.toPx() * laserProgress
                        drawLine(
                            color = CamScannerTealBright,
                            start = Offset(left + 8.dp.toPx(), laserY),
                            end = Offset(right - 8.dp.toPx(), laserY),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    } else {
                        val laserY = top + boxHeight.toPx() * 0.5f
                        drawLine(
                            color = Color(0xFFEF4444), // Red barcode laser
                            start = Offset(left + 6.dp.toPx(), laserY),
                            end = Offset(right - 6.dp.toPx(), laserY),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                CameraAppMode.SCANNER, CameraAppMode.PDF -> {
                    val midX = left + boxWidth.toPx() / 2f
                    val midY = top + boxHeight.toPx() / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(midX - 12.dp.toPx(), midY),
                        end = Offset(midX + 12.dp.toPx(), midY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(midX, midY - 12.dp.toPx()),
                        end = Offset(midX, midY + 12.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                CameraAppMode.PHOTO -> {}
            }
        }

        // Category Guide text badge
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (boxHeight / 2) + 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Text(
                    text = guideTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }
}

private data class CategoryViewfinderDimensions(
    val width: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp,
    val cornerRadius: androidx.compose.ui.unit.Dp,
    val title: String
)

/**
 * Switcher between QR Code and Barcode modes.
 * Contained in a dark pill container: the active item turns white with black text.
 */
@Composable
fun QrBarcodeSelectorSwitch(
    activeSubMode: QrBarcodeSubMode,
    onSubModeSelected: (QrBarcodeSubMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF18181B).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isQr = (activeSubMode == QrBarcodeSubMode.QR_CODE)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isQr) Color.White else Color.Transparent)
                    .clickable { onSubModeSelected(QrBarcodeSubMode.QR_CODE) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "QR Code",
                        tint = if (isQr) Color.Black else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "QR Code",
                        color = if (isQr) Color.Black else Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isQr) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            val isBarcode = (activeSubMode == QrBarcodeSubMode.BARCODE)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isBarcode) Color.White else Color.Transparent)
                    .clickable { onSubModeSelected(QrBarcodeSubMode.BARCODE) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ViewWeek,
                        contentDescription = "Barcode",
                        tint = if (isBarcode) Color.Black else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Barcode",
                        color = if (isBarcode) Color.Black else Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isBarcode) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

/**
 * Multi-page thumbnail strip with '+' Add Page button.
 * Displays all cropped pages so far with page index and an Add Page button that returns to camera.
 */
@Composable
fun MultiPageThumbnailStrip(
    pages: List<ScannedPageItem>,
    currentBitmap: Bitmap?,
    selectedIndex: Int,
    onSelectPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Render all scanned pages in session
            itemsIndexed(pages) { index, item ->
                val isSelected = (index == selectedIndex)
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) CamScannerTealBright else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectPage(index) }
                ) {
                    Image(
                        bitmap = item.processed.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Page number badge
                    Surface(
                        color = if (isSelected) CamScannerTeal else Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Delete button if more than 1 page
                    if (pages.size > 1 && onDeletePage != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = 0.85f))
                                .clickable { onDeletePage(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete Page",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // If current page is not yet in pages list, show current preview
            if (selectedIndex >= pages.size && currentBitmap != null) {
                item {
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .height(78.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.5.dp, CamScannerTealBright, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = "Current page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = CamScannerTeal,
                            shape = RoundedCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "${pages.size + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Prominent '+' Add Page button
            item {
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF242426))
                        .border(1.5.dp, CamScannerTealBright.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .clickable { onAddPage() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Page",
                            tint = CamScannerTealBright,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Add Page",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Translucent Frosted Glass Bottom Sheet displaying all application categories and features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFeaturesGlassBottomSheet(
    onDismiss: () -> Unit,
    onSelectCameraMode: (CameraAppMode) -> Unit,
    onSelectCategory: (ToolCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF218181B), // Dark frosted glass effect
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "All Features & Tools",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Utility Hub & Circle Scanner Features",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Scanner Presets Row
            Text(
                text = "Scanner Modes",
                color = CamScannerTealBright,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val scannerPresets = listOf(
                    Triple(CameraAppMode.SCANNER, "Document", Icons.Default.DocumentScanner),
                    Triple(CameraAppMode.PDF, "PDF Batch", Icons.Default.PictureAsPdf),
                    Triple(CameraAppMode.ID_CARD, "NID Card", Icons.Default.Badge),
                    Triple(CameraAppMode.QR_BARCODE, "QR & Barcode", Icons.Default.QrCodeScanner)
                )
                scannerPresets.forEach { (mode, label, icon) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF27272A),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSelectCameraMode(mode)
                                onDismiss()
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = CamScannerTealBright,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // All Categories Grid
            Text(
                text = "All Utility Hub Categories",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                items(ToolCategory.entries.toTypedArray()) { category ->
                    val count = ToolRegistry.getCategoryCount(category)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF27272A).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectCategory(category)
                                onDismiss()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(category.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = category.title,
                                    tint = category.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "$count tools",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

