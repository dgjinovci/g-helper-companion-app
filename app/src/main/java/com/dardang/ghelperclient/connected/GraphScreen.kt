package com.dardang.ghelperclient.connected

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dardang.ghelperclient.R
import com.dardang.ghelperclient.service.MODE_GPU_ECO
import com.dardang.ghelperclient.service.MODE_GPU_OPTIMIZED
import com.dardang.ghelperclient.service.MODE_GPU_STANDARD
import com.dardang.ghelperclient.service.MODE_GPU_ULTIMATE
import com.dardang.ghelperclient.service.PERFORMANCE_MODE_BALANCED
import com.dardang.ghelperclient.service.PERFORMANCE_MODE_SILENT
import com.dardang.ghelperclient.service.PERFORMANCE_MODE_TURBO
import com.dardang.ghelperclient.ui.theme.ContainerColor
import com.dardang.ghelperclient.viewModels.AppViewModel
import kotlin.math.ceil


@Composable
fun GraphScreen(viewModel: AppViewModel) {


    val uiStats by viewModel.serviceStats.collectAsState()
    val performanceMode by viewModel.performanceMode.collectAsState()
    val gpuMode by viewModel.gpuMode.collectAsState()

    val isRPM by viewModel.isRPM.collectAsState(false)
    val isCelsius by viewModel.isTempCelsius.collectAsState(true)


    LazyVerticalGrid(
        columns = GridCells.Fixed(if (viewModel.isTablet || viewModel.isLandscape) 2 else 1),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(15.dp),
    ) {
        // CPU
        item {
            Column(Modifier.asBordered()) {

                val cpuUsage by viewModel.cpuUsage.collectAsState(0)
                // 0 - ProgressBar, 1 - ProgressBar (prev smooth), 2 - Graph, 3 - Smooth Graph
                val sensorMode by viewModel.sensorMode.collectAsState()

                RowItem(
                    modifier = Modifier.clickable { viewModel.setSensorMode((sensorMode + 2) % 4) },
                    title = "CPU",
                    subtitle = "Usage ${cpuUsage}%",
                ) {
                    if (sensorMode < 2) {
                        GradientLinearProgressIndicator(progress = cpuUsage / 100f)
                    } else {
                        LinearGraph2(
                            value = viewModel.cpuUsage,
                            smoothScroll = sensorMode == 3,
                            modifier = Modifier
                                .height(if (viewModel.isTablet) 120.dp else 80.dp)
                                .fillMaxWidth()
                                .clickable(true) {
                                    viewModel.setSensorMode(if (sensorMode == 3) 2 else 3)
                                }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                RowItem(
                    modifier = Modifier.clickable { viewModel.setCelsius(!isCelsius) },
                    icon = painterResource(R.drawable.thermostat),
                    title = "Temp",
                    subtitle = if (isCelsius) "${uiStats.cpuTemp}°C"
                    else "${(uiStats.cpuTemp * 1.8).toInt() + 32}°F"
                ) {
                    GradientLinearProgressIndicator(progress = uiStats.cpuTemp / 100f)
                }

                Spacer(Modifier.height(10.dp))

                RowItem(
                    modifier = Modifier.clickable { viewModel.setRPM(!isRPM) },
                    icon = painterResource(R.drawable.fan),
                    title = "Fan Speed",
                    subtitle = if (isRPM) "${uiStats.cpuFan.rpm} RPM"
                    else "${(uiStats.cpuFan.percent * 100).toInt()}%"
                ) {
                    GradientLinearProgressIndicator(progress = uiStats.cpuFan.percent)
                }

                Spacer(Modifier.height(20.dp))

                val modes = remember {
                    mapOf(
                        PERFORMANCE_MODE_SILENT to "Silent",
                        PERFORMANCE_MODE_BALANCED to "Balanced",
                        PERFORMANCE_MODE_TURBO to "Turbo"
                    )
                }
                OptionButtons(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    values = modes.values.toList(),
                    selectedIndex = modes.keys.indexOf(performanceMode)
                ) {
                    val mode = modes.keys.toTypedArray()[it]
                    viewModel.setPerformanceMode(mode)
                }
            }
        }
        // GPU
        item {
            Column(Modifier.asBordered()) {

                val gpuUsage by viewModel.gpuUsage.collectAsState(0)
                // 0 - ProgressBar, 1 - ProgressBar (prev smooth), 2 - Graph, 3 - Smooth Graph
                val sensorMode by viewModel.sensorMode.collectAsState()

                RowItem(
                    modifier = Modifier.clickable { viewModel.setSensorMode((sensorMode + 2) % 4) },
                    title = "GPU",
                    subtitle = "Usage ${gpuUsage}%"
                ) {
                    if (sensorMode < 2) {
                        GradientLinearProgressIndicator(progress = gpuUsage / 100f)
                    } else {
                        LinearGraph2(
                            value = viewModel.gpuUsage,
                            smoothScroll = sensorMode == 3,
                            modifier = Modifier
                                .height(if (viewModel.isTablet) 120.dp else 80.dp)
                                .fillMaxWidth()
                                .clickable(true) {
                                    viewModel.setSensorMode(if (sensorMode == 3) 2 else 3)
                                }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                RowItem(
                    modifier = Modifier.clickable { viewModel.setCelsius(!isCelsius) },
                    icon = painterResource(R.drawable.thermostat),
                    title = "Temp",
                    subtitle = if (isCelsius) "${uiStats.gpuTemp}°C"
                    else "${(uiStats.gpuTemp * 1.8).toInt() + 32}°F"
                ) {
                    GradientLinearProgressIndicator(progress = uiStats.gpuTemp / 100f)
                }

                Spacer(Modifier.height(10.dp))

                RowItem(
                    modifier = Modifier.clickable { viewModel.setRPM(!isRPM) },
                    icon = painterResource(R.drawable.fan),
                    title = "Fan Speed",
                    subtitle = if (isRPM) "${uiStats.gpuFan.rpm} RPM"
                    else "${(uiStats.gpuFan.percent * 100).toInt()}%"
                ) {
                    GradientLinearProgressIndicator(progress = uiStats.gpuFan.percent)
                }

                Spacer(Modifier.height(20.dp))

                val modes = remember {
                    mapOf(
                        MODE_GPU_ECO to "Eco",
                        MODE_GPU_STANDARD to "Standard",
                        MODE_GPU_ULTIMATE to "Ultimate",
                        MODE_GPU_OPTIMIZED to "Optimized",
                    )
                }
                OptionButtons(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    values = modes.values.toList(),
                    selectedIndex = modes.keys.indexOf(gpuMode)
                ) { index ->
                    val mode = modes.keys.toTypedArray()[index]
                    viewModel.setGpuMode(mode)
                }
            }
        }

        item {
            Column(Modifier.asBordered()) {

                RowItem(
                    modifier = Modifier.clickable { viewModel.setRPM(!isRPM) },
                    icon = painterResource(R.drawable.fan),
                    title = "Mid Fan Speed",
                    subtitle = if (isRPM) "${uiStats.midFan.rpm} RPM"
                    else "${(uiStats.midFan.percent * 100).toInt()}%"
                ) {
                    GradientLinearProgressIndicator(progress = uiStats.midFan.percent)
                }

                Spacer(Modifier.height(10.dp))
            }
        }
        item {

            Column(Modifier.asBordered()) {
                val text =
                    "%.1f/%.1f GB".format(
                        uiStats.memory.used / 1024f / 1024f,
                        uiStats.memory.total / 1024f / 1024f
                    )
                RowItem(
                    modifier = Modifier.clickable { viewModel.setRPM(!isRPM) },
                    icon = rememberVectorPainter(Icons.Default.Menu),
                    title = "Memory $text",
                    subtitle = "${ceil(uiStats.memory.percent * 100).toInt()}%"
                ) {
                    GradientLinearProgressIndicator(progress = uiStats.memory.percent)
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}


@Composable
private fun RowItem(
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    title: String,
    subtitle: String,
    graph: @Composable ColumnScope.() -> Unit
) {

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    painter = icon, contentDescription = "",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
            }

            Text(title)
            Spacer(Modifier.weight(1f))
            Text(subtitle)
        }

        Spacer(Modifier.height(4.dp))

        graph()
    }
}


private fun Modifier.asBordered(): Modifier {
    return this
        //.padding(horizontal = 15.dp)
        .fillMaxWidth()
        .background(ContainerColor)
        .padding(10.dp)
}

