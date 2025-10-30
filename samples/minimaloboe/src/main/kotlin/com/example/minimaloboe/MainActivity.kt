/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.minimaloboe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.minimaloboe.ui.theme.SamplesTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "FilterAudioStreamUAF"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let our AudioPlayer observe lifecycle events for the application so when it goes into the
        // background we can stop audio playback.
        ProcessLifecycleOwner.get().lifecycle.addObserver(AudioPlayer)

        setContent {
            SamplesTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MainControls()
                }
            }
        }
        stopAndReleaseOnHeadsetPlug()
    }

    private fun stopAndReleaseOnHeadsetPlug() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        val receiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val isConnected = intent?.getIntExtra("state", 0) == 1
                Log.i(TAG, "received ACTION_HEADSET_PLUG -> $isConnected")

                // delay to make sure `internalErrorCallback()` fires before releasing the stream
                lifecycleScope.launch {
                    delay(500L)

                    Log.i(TAG, "releasing stream 500 ms after receiving ACTION_HEADSET_PLUG")

                    // this will stop(), close(), and free the AudioStream's memory
                    AudioPlayer.setPlaybackEnabled(false)
                }
            }
        }
        registerReceiver(receiver, filter)
    }
}

@Composable
fun MainControls() {
    val playerState by AudioPlayer.playerState.collectAsStateWithLifecycle()
    val forceConversion by AudioPlayer.forceConversion.collectAsStateWithLifecycle()
    MainControls(playerState, forceConversion, AudioPlayer::setPlaybackEnabled, AudioPlayer::setForceConversion)
}

@Composable
fun MainControls(
    playerState: PlayerState,
    forceConversion: Boolean,
    setPlaybackEnabled: (Boolean) -> Unit = {},
    setForceConversion: (Boolean) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Column {

            val isPlaying = playerState is PlayerState.Started
            Text(text = "Minimal Oboe!")
            Row {
                Button(
                    onClick = { setPlaybackEnabled(true) },
                    enabled = !isPlaying
                ) {
                    Text(text = "Start Audio")
                }
                Button(
                    onClick = { setPlaybackEnabled(false) },
                    enabled = isPlaying
                ) {
                    Text(text = "Stop Audio")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Switch(
                    checked = forceConversion,
                    onCheckedChange = setForceConversion,
                    enabled = !isPlaying,
                )
                Text("Use FilterAudioStream")
            }

            // Create a status message for displaying the current playback state.
            val uiStatusMessage = "Current status: " +
                    when (playerState) {
                        PlayerState.NoResultYet -> "No result yet"
                        PlayerState.Started -> "Started"
                        PlayerState.Stopped -> "Stopped"
                        is PlayerState.Unknown -> {
                            "Unknown. Result = " + playerState.resultCode
                        }
                    }
            Text(uiStatusMessage)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SamplesTheme {
        MainControls(PlayerState.Started, true)
    }
}
