package com.mokona.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mokona.app.R
import com.mokona.app.data.DownloadQueue
import com.mokona.app.data.TranslationModels

/**
 * The bar at the bottom while something downloads: a work being saved (real progress, pages done
 * of total, plus how many wait) or a dictionary (indeterminate: ML Kit gives no percentage).
 */
@Composable
fun DownloadBar(modifier: Modifier = Modifier) {
	val work = DownloadQueue.current
	val models = TranslationModels.downloading.keys.toList()
	if (work == null && models.isEmpty()) return
	Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 8.dp)) {
		if (work != null) {
			val waiting = DownloadQueue.pending.size
			Text(
				stringResource(R.string.saving_work, work.title) + " · ${DownloadQueue.done}/${DownloadQueue.total}" + if (waiting > 0) " · +$waiting" else "",
				style = MaterialTheme.typography.labelMedium, maxLines = 1,
			)
			LinearProgressIndicator(progress = { if (DownloadQueue.total == 0) 0f else DownloadQueue.done.toFloat() / DownloadQueue.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
		}
		if (models.isNotEmpty()) {
			Text(stringResource(R.string.model_downloading, models.joinToString(", ") { TranslationModels.name(it) }), style = MaterialTheme.typography.labelMedium, maxLines = 1)
			LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
		}
	}
}
