package com.mokona.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mokona.app.R
import com.mokona.app.data.Illust
import com.mokona.app.data.WallpaperItem
import com.mokona.app.data.WallpaperList
import com.mokona.app.data.WallpaperPrefs

/** Settings › Wallpaper lists: every list with its works; rename, delete, remove works. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperListsScreen(onBack: () -> Unit) {
	var creating by remember { mutableStateOf(false) }
	var renaming by remember { mutableStateOf<WallpaperList?>(null) }
	Column(Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(stringResource(R.string.wallpaper_lists)) },
			navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
			actions = { TextButton(onClick = { creating = true }) { Text(stringResource(R.string.new_list)) } },
		)
		if (WallpaperPrefs.lists.isEmpty()) {
			Text(stringResource(R.string.no_lists_yet), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			items(WallpaperPrefs.lists, key = { it.id }) { list ->
				Card {
					ListItem(
						headlineContent = { Text(list.name, style = MaterialTheme.typography.titleMedium) },
						supportingContent = { Text(stringResource(R.string.items_count, list.items.size)) },
						trailingContent = {
							Row {
								TextButton(onClick = { renaming = list }) { Text(stringResource(R.string.rename)) }
								TextButton(onClick = { WallpaperPrefs.deleteList(list.id) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
							}
						},
					)
					list.items.forEach { item ->
						ListItem(
							leadingContent = { AsyncImage(model = item.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))) },
							headlineContent = { Text(item.title, maxLines = 1) },
							supportingContent = { Text(item.artist, maxLines = 1) },
							trailingContent = { TextButton(onClick = { WallpaperPrefs.removeFromList(list.id, item.id) }) { Text(stringResource(R.string.remove)) } },
						)
					}
				}
			}
		}
	}
	if (creating) NameDialog(title = stringResource(R.string.new_list), initial = "", onDismiss = { creating = false }) { WallpaperPrefs.createList(it); creating = false }
	renaming?.let { l -> NameDialog(title = stringResource(R.string.rename), initial = l.name, onDismiss = { renaming = null }) { WallpaperPrefs.renameList(l.id, it); renaming = null } }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
	var name by remember { mutableStateOf(initial) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.list_name)) }, singleLine = true) },
		confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onOk(name) }) { Text(stringResource(R.string.ok)) } },
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.clear)) } },
	)
}

/** From a work's menu: pick a list (or make one) and drop the work in it. */
@Composable
fun AddToWallpaperListDialog(illust: Illust, onDismiss: () -> Unit, onResult: (String) -> Unit) {
	var newName by remember { mutableStateOf("") }
	val added = stringResource(R.string.added_to_list)
	val already = stringResource(R.string.already_in_list)
	val item = WallpaperItem(illust.id, illust.title, illust.user.name, illust.imageUrls.squareMedium.ifBlank { illust.imageUrls.medium })
	fun add(list: WallpaperList) {
		onResult((if (WallpaperPrefs.addToList(list.id, item)) added else already).format(list.name))
		onDismiss()
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.add_to_wallpaper_list)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				if (WallpaperPrefs.lists.isEmpty()) Text(stringResource(R.string.no_lists_yet), style = MaterialTheme.typography.bodySmall)
				WallpaperPrefs.lists.forEach { l ->
					TextButton(onClick = { add(l) }, modifier = Modifier.fillMaxWidth()) { Text("${l.name} · ${l.items.size}") }
				}
				OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.new_list)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
			}
		},
		confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) add(WallpaperPrefs.createList(newName)) }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.ok)) } },
		dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.clear)) } },
	)
}
