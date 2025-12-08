package com.dardang.ghelperclient.connected

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dardang.ghelperclient.ui.theme.ContainerColor2
import com.dardang.ghelperclient.ui.theme.SensorColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.ceil


@Composable
fun OptionButtons(
    modifier: Modifier = Modifier,
    background:Color = ContainerColor2,
    values: List<String>,
    selectedIndex: Int = -1,
    onSelectedIndex: (Int) -> Unit
) {
    Row(
        modifier
            .height(40.dp)
            .background(background)//, CutCornerShape(topStart = 15.dp, bottomEnd = 15.dp))
    ) {

        values.forEachIndexed { i, value ->

            val selected = selectedIndex == i
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .clickable(interactionSource = MutableInteractionSource()) {
                        onSelectedIndex(i)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    value,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.W700 else FontWeight.W400,
                    color = if (selected) SensorColor else Color.Unspecified
                )
            }
        }
    }

}


@Composable
fun LinearGraph2(
    modifier: Modifier = Modifier,
    value: Flow<Int>,
    smoothScroll: Boolean = false,
    color: Color = SensorColor,
    secondaryColor: Color = SensorColor.copy(alpha = 0.3f),
    backgroundColor: Color = ContainerColor2,
    strokeSize: Float = 1f * LocalDensity.current.density
) {

    val density = LocalDensity.current.density

    var maxNumData by remember { mutableIntStateOf(20) }
    val data = remember { mutableStateListOf<Float>() }

    val step = remember { 20 * density }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var bgPath by remember { mutableStateOf(Path()) }
    var path by remember { mutableStateOf(Path()) }


    var tick by remember { mutableIntStateOf(0) }
    var lastTick by remember { mutableLongStateOf(0L) }
    val animateStep = remember { Animatable(0f) }

    fun calcHeight(value: Float) = size.height - value * size.height


    LaunchedEffect(size, maxNumData, smoothScroll) {
        if (size == IntSize.Zero || maxNumData == 0) return@LaunchedEffect

        value.collectLatest {

            data.add(it / 100f)
            if (data.size > maxNumData) {
                data.removeAt(0)
            }

            val p = Path()
            p.moveTo(size.width.toFloat(), size.height.toFloat())

            var x = 0f
            for (i in 0 until data.size) {
                val invI = data.size - i - 1
                x = size.width - (step * i)
                p.lineTo(x, calcHeight(data[invI]))
            }

            p.lineTo(x, size.height.toFloat() + strokeSize)
            p.lineTo(size.width.toFloat(), size.height.toFloat() + strokeSize)

            path = p


            if (smoothScroll) {
                // it looks empty part (step) at the right if smooth scroll
                p.translate(Offset(step, 0f))

                val t = System.currentTimeMillis()
                tick = (t - lastTick).toInt()
                lastTick = t

                animateStep.snapTo(0f)
                animateStep.animateTo(step, tween(tick, easing = LinearEasing))
            }
        }
    }


    Canvas(
        modifier = modifier
            .background(backgroundColor)
            .onGloballyPositioned {
                size = it.size

                maxNumData = ceil((size.width + step) / step).toInt()

                // bottom-right to left
                val p = Path()
                // draw on X
                val extWidth = it.size.width + step

                for (i in 0 until ceil(extWidth / step).toInt()) {
                    val x = extWidth - (step * i)
                    p.moveTo(x, 0f)
                    p.lineTo(x, it.size.height.toFloat())
                }
                // draw on Y
                for (i in 0 until ceil(it.size.height / step).toInt() + 1) {
                    val y = size.height - (step * i)
                    p.moveTo(0f, y)
                    p.lineTo(extWidth, y)
                }
                bgPath = p
                //bgPath.translate(Offset(step, 0f))
            },

        onDraw = {

            // clip to prevent overdraw bottom horizontal line
            clipRect(
                strokeSize,
                -strokeSize,
                size.width - strokeSize,
                size.height.toFloat()
            ) {

                fun drawAll() {
                    drawPath(bgPath, color = secondaryColor, style = Stroke(1.5f))
                    drawPath(path, color = color, style = Stroke(strokeSize))
                    drawPath(path, color = secondaryColor, style = Fill)
                }

                if (smoothScroll) {
                    translate(-animateStep.value) {
                        drawAll()
                    }
                } else {
                    drawAll()
                }
            }
        }
    )
}


@Composable
fun GradientLinearProgressIndicator(
    primaryColor: Color = SensorColor,
    secondaryColor: Color = ContainerColor2,
    size: Dp = 5.dp,
    @FloatRange(0.0, 1.0)
    progress: Float,
) {

    val animProgress by animateFloatAsState(progress)//, animationSpec = tween(1000))

    Spacer(Modifier.height(5.dp))

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(size)
    ) {

//        val h = size.toPx() / 2f
//        var p = Path()
//        p.moveTo(0f, this.size.height)
//        p.lineTo(0f, h)
//        p.lineTo(h, 0f)
//        p.lineTo(this.size.width, 0f)
//        p.lineTo(this.size.width, h)
//        p.lineTo(this.size.width - h, this.size.height)
//        p.close()
//
//        drawPath(p, color = secondaryColor, style = Fill)
//
//        p = Path()
//        p.moveTo(0f, this.size.height)
//        p.lineTo(0f, h)
//        p.lineTo(h, 0f)
//        p.lineTo(this.size.width * animProgress, 0f)
//        p.lineTo(this.size.width * animProgress, h)
//        p.lineTo(this.size.width * animProgress - h, this.size.height)
//        p.close()
//
//        drawPath(p, color = primaryColor, style = Fill)


        drawRect(secondaryColor, size = Size(this.size.width, this.size.height))
        drawRect(primaryColor, size = Size(this.size.width * animProgress, this.size.height))
    }
}
