/*
package com.dardang.ghelperclient.connected

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dardang.ghelperclient.AppViewModel
import com.dardang.ghelperclient.service.MODE_GPU_ECO
import com.dardang.ghelperclient.service.MODE_GPU_OPTIMIZED
import com.dardang.ghelperclient.service.MODE_GPU_STANDARD
import com.dardang.ghelperclient.service.MODE_GPU_ULTIMATE
import com.dardang.ghelperclient.service.MODE_SCREEN_120HZ
import com.dardang.ghelperclient.service.MODE_SCREEN_60HZ
import com.dardang.ghelperclient.service.MODE_SCREEN_AUTO
import com.dardang.ghelperclient.service.MODE_SCREEN_MINILED
import com.dardang.ghelperclient.service.PERFORMANCE_MODE_BALANCED
import com.dardang.ghelperclient.service.PERFORMANCE_MODE_SILENT
import com.dardang.ghelperclient.service.PERFORMANCE_MODE_TURBO
import com.dardang.ghelperclient.service.TYPE_MODES
import com.dardang.ghelperclient.ui.theme.ButtonColor


@Composable
fun ModesScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {


    val uiModes by viewModel.uiModes.collectAsState(AppViewModel.UIModes())

    LaunchedEffect(Unit) {
        viewModel.read(TYPE_MODES)
    }

    Column(modifier = modifier) {


        Spacer(Modifier.height(20.dp))

        Text(text = "Performance Mode")
        Row {
            AppButton(
                "Silent",
                selectedColor = if (uiModes.performanceMode == PERFORMANCE_MODE_SILENT)
                    Color.Green
                else ButtonColor,
                onClick = {
                    viewModel.setPerformanceMode(PERFORMANCE_MODE_SILENT)
                }
            )

            Spacer(Modifier.width(5.dp))

            AppButton(
                "Balanced",
                selectedColor = if (uiModes.performanceMode == PERFORMANCE_MODE_BALANCED)
                    Color.Blue
                else ButtonColor,
                onClick = {
                    viewModel.setPerformanceMode(PERFORMANCE_MODE_BALANCED)
                }
            )
            Spacer(Modifier.width(5.dp))
            AppButton(
                "Turbo",
                selectedColor = if (uiModes.performanceMode == PERFORMANCE_MODE_TURBO)
                    Color.Red
                else ButtonColor,
                onClick = {
                    viewModel.setPerformanceMode(PERFORMANCE_MODE_TURBO)
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(text = "GPU Mode")
        Row {
            AppButton(
                "Eco",
                selectedColor = if (uiModes.gpuMode == MODE_GPU_ECO)
                    Color.Blue
                else ButtonColor,
                onClick = {
                    viewModel.setGpuMode(MODE_GPU_ECO)
                }
            )

            Spacer(Modifier.width(5.dp))
            AppButton(
                "Standard",
                selectedColor = if (uiModes.gpuMode == MODE_GPU_STANDARD)
                    Color.Blue
                else ButtonColor,
                onClick = {
                    viewModel.setGpuMode(MODE_GPU_STANDARD)
                }
            )
            Spacer(Modifier.width(5.dp))
            AppButton(
                "Ultimate",
                selectedColor = if (uiModes.gpuMode == MODE_GPU_ULTIMATE)
                    Color.Blue
                else ButtonColor,
                onClick = {
                    viewModel.setGpuMode(MODE_GPU_ULTIMATE)
                }
            )
            Spacer(Modifier.width(5.dp))
            AppButton(
                "Optimized",
                selectedColor = if (uiModes.gpuMode == MODE_GPU_OPTIMIZED)
                    Color.Blue
                else ButtonColor,
                onClick = {
                    viewModel.setGpuMode(MODE_GPU_OPTIMIZED)
                }
            )
        }


        Spacer(Modifier.height(20.dp))

        //val screenText = if (screen.screenEnabled) "${screen.frequency}Hz "

        Text(text = "Screen ")

        if (!uiModes.screen.screenEnabled) {
            Text(text = "Screen is disabled")
        } else {
            Row {

                val selection =
                    if (uiModes.screen.screenAuto)
                        MODE_SCREEN_AUTO
                    else if (uiModes.screen.frequency == uiModes.screen.minRate)
                        MODE_SCREEN_60HZ
                    else if (uiModes.screen.frequency > uiModes.screen.minRate)
                        MODE_SCREEN_120HZ
                    else -1

                AppButton(
                    text = "Auto",
                    selectedColor = if (selection == MODE_SCREEN_AUTO) Color.Blue else ButtonColor
                ) {
                    viewModel.setScreenMode(MODE_SCREEN_AUTO)}
                Spacer(Modifier.width(5.dp))

                // 60Hz
                AppButton(
                    text = "${uiModes.screen.minRate}Hz",
                    selectedColor = if (selection == MODE_SCREEN_60HZ) Color.Blue else ButtonColor
                ) {
                    viewModel.setScreenMode(MODE_SCREEN_60HZ)
                }
                Spacer(Modifier.width(5.dp))


                // 120Hz
                val text120Hz = if (uiModes.screen.maxFrequency > uiModes.screen.minRate) {
                    "${uiModes.screen.maxFrequency}Hz ${if (uiModes.screen.overdriveSetting) "+ OD" else ""}"
                } else {
                    "120Hz"
                }

                AppButton(
                    text = text120Hz,
                    selectedColor = if (selection == MODE_SCREEN_120HZ) Color.Blue else ButtonColor
                ) {
                    viewModel.setScreenMode(MODE_SCREEN_120HZ)
                }
                Spacer(Modifier.width(5.dp))


                if (uiModes.screen.fhd > 0) {
                    AppButton(
                        text = if (uiModes.screen.fhd > 1) "FHD" else "UHD",
                        selectedColor = ButtonColor
                    ) { }
                    Spacer(Modifier.width(5.dp))
                }

                val hdrControlVisible = (uiModes.screen.hdr && uiModes.screen.hdrControl > 0)

                if (uiModes.screen.miniLed1 > 0 || uiModes.screen.miniLed2 > 0) {

                    if (uiModes.screen.miniLed1 > 0) {
                        AppButton(
                            enabled = !uiModes.screen.hdr,
                            text = "Mini LED",
                            selectedColor = if (uiModes.screen.miniLed1 == 2 || uiModes.screen.hdr) Color.Blue else ButtonColor
                        ) { }
                        Spacer(Modifier.width(5.dp))

                    } else if (uiModes.screen.miniLed2 > 0) {

                        val miniLed2 = if (uiModes.screen.hdr) 2 else uiModes.screen.miniLed2

                        val text = when (miniLed2) {
                            1 -> "Mutlizone"
                            2 -> "Multizone Strong"
                            3 -> "One Zone"
                            else -> "Unknown"
                        }
                        AppButton(
                            enabled = !uiModes.screen.hdr,
                            text = text,
                            selectedColor =
                                if (miniLed2 == 3)
                                    ButtonColor
                                else
                                    Color.Blue
                        ) {
                            viewModel.setScreenMode(MODE_SCREEN_MINILED)
                        }
                        Spacer(Modifier.width(5.dp))

                    }

                }
                if (hdrControlVisible) {
                    AppButton(
                        enabled = !uiModes.screen.hdr,
                        text = "HDR",
                        selectedColor = if (uiModes.screen.hdrControl > 1) Color.Blue else ButtonColor
                    ) { }
                    Spacer(Modifier.width(5.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Text(text = "Keyboard")

        Row(
            modifier = Modifier.horizontalScroll(state = rememberScrollState())
        ) {
            for (mode in uiModes.auraKeyboardModes) {
                AppButton(
                    mode.text,
                    selectedColor = if (uiModes.auraKeyboardMode == mode)
                        Color.Blue
                    else ButtonColor,
                    onClick = {
                        viewModel.setKeyboardMode(mode)
                    })
                Spacer(Modifier.width(5.dp))
            }
        }

    }
}


@Composable
private fun AppButton(
    text: String,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.dp, selectedColor),
        shape = RoundedCornerShape(5.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = ButtonColor),
        onClick = onClick
    ) {
        Text(text = text, fontSize = 13.sp)
    }
}*/
