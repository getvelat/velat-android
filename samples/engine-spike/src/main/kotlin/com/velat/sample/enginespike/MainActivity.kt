/*
 * Copyright 2026 Rafal Niski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.velat.sample.enginespike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Single-activity entry point for the engine-spike sample app. All UI is
 * Compose; the engine lifecycle is owned by [EngineSpikeViewModel].
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EngineSpikeScreen()
                }
            }
        }
    }
}

@Composable
private fun EngineSpikeScreen(viewModel: EngineSpikeViewModel = viewModel()) {
    val context = LocalContext.current
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val promptInput by viewModel.promptInput.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Velat Engine Spike",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Day 4 — exercising MediaPipeLlmEngine on real device hardware.",
            style = MaterialTheme.typography.bodySmall,
        )

        ModelStatusCard(
            state = modelState,
            onLoadClick = { viewModel.loadModel(context) },
        )

        if (modelState is ModelState.Ready) {
            PromptCard(
                prompt = promptInput,
                onPromptChange = viewModel::onPromptChange,
                onGenerateClick = viewModel::generate,
                generating = generationState is GenerationState.Streaming,
            )
            ResponseCard(state = generationState)
        }
    }
}

@Composable
private fun ModelStatusCard(state: ModelState, onLoadClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Model",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is ModelState.Missing -> {
                    Text(
                        text = "Model file not found. Push it via:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.expectedPath,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is ModelState.NotLoaded -> {
                    Text(
                        text = "Found at: ${state.path}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onLoadClick) {
                        Text("Load model into engine")
                    }
                }

                is ModelState.Loading -> {
                    Text(
                        text = "Loading… (${state.elapsedMs} ms)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is ModelState.Ready -> {
                    Text(
                        text = "Loaded: ${state.modelName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Cold-load time: ${state.coldLoadMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is ModelState.Error -> {
                    Text(
                        text = "Load failed: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onGenerateClick: () -> Unit,
    generating: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Prompt",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !generating,
                placeholder = { Text("What is the capital of France?") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onGenerateClick,
                enabled = !generating && prompt.isNotBlank(),
            ) {
                Text(if (generating) "Generating…" else "Generate")
            }
        }
    }
}

@Composable
private fun ResponseCard(state: GenerationState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Response",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is GenerationState.Idle -> {
                    Text(
                        text = "No response yet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is GenerationState.Streaming, is GenerationState.Done -> {
                    val text = when (state) {
                        is GenerationState.Streaming -> state.text
                        is GenerationState.Done -> state.text
                        else -> ""
                    }
                    Text(
                        text = text.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.statsLine(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is GenerationState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun GenerationState.statsLine(): String = when (this) {
    is GenerationState.Streaming -> {
        val seconds = (System.currentTimeMillis() - startedAtMs) / 1000.0
        val rate = if (seconds > 0) tokenCount / seconds else 0.0
        "TTFT: ${ttftMs ?: "—"} ms · tokens: $tokenCount · ${"%.1f".format(rate)} tok/s"
    }

    is GenerationState.Done ->
        "TTFT: $ttftMs ms · tokens: $tokenCount · ${"%.1f".format(decodeRate)} tok/s · " +
            "total: $totalMs ms"

    else -> ""
}
