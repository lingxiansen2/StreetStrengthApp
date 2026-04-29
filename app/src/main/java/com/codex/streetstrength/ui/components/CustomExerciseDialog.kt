package com.codex.streetstrength.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.repository.CustomExerciseDraft
import com.codex.streetstrength.ui.formatCategoryType
import com.codex.streetstrength.ui.formatMetricType

@Composable
fun CustomExerciseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CustomExerciseDraft) -> Unit,
) {
    var categoryType by remember { mutableStateOf(CategoryType.PULL) }
    var name by remember { mutableStateOf("") }
    var variantName by remember { mutableStateOf("标准") }
    var supportsLoad by remember { mutableStateOf(false) }
    var metricType by remember { mutableStateOf(MetricType.REPS) }
    var cue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u65b0\u5efa\u81ea\u5b9a\u4e49\u9879\u76ee") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("\u9879\u76ee\u540d\u79f0") },
                )
                OutlinedTextField(
                    value = variantName,
                    onValueChange = { variantName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("\u9ed8\u8ba4\u53d8\u5f0f\u540d\u79f0") },
                )
                Text("\u5206\u7c7b")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CategoryType.entries) { category ->
                        FilterChip(
                            selected = categoryType == category,
                            onClick = { categoryType = category },
                            label = { Text(formatCategoryType(category)) },
                        )
                    }
                }
                Text("\u8ba1\u91cf\u65b9\u5f0f")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        items = listOf(
                            MetricType.REPS,
                            MetricType.HOLD_SECONDS,
                            MetricType.HOLD_TO_FAILURE,
                            MetricType.HOLD_SECONDS_PLUS_ECCENTRIC,
                        ),
                    ) { metric ->
                        FilterChip(
                            selected = metricType == metric,
                            onClick = { metricType = metric },
                            label = { Text(formatMetricType(metric)) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u652f\u6301\u8d1f\u91cd")
                    Switch(checked = supportsLoad, onCheckedChange = { supportsLoad = it })
                }
                OutlinedTextField(
                    value = cue,
                    onValueChange = { cue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u52a8\u4f5c\u63d0\u793a\u53ef\u9009") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        CustomExerciseDraft(
                            categoryType = categoryType,
                            name = name.trim(),
                            variantName = variantName.trim(),
                            supportsExternalLoad = supportsLoad,
                            defaultMetricType = metricType,
                            cue = cue.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = name.isNotBlank() && variantName.isNotBlank(),
            ) {
                Text("\u4fdd\u5b58")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
            }
        },
    )
}
