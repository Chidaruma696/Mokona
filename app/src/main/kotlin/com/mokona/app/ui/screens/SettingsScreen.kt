package com.mokona.app.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mokona.app.BuildConfig
import com.mokona.app.R
import com.mokona.app.data.PixivAuth
import com.mokona.app.ui.AppPrefs
import com.mokona.app.ui.ThemeMode
import com.mokona.app.data.PageTranslator
import com.mokona.app.data.TranslationModels
import androidx.compose.runtime.LaunchedEffect
import com.mokona.app.data.Updates
import com.mokona.app.data.WallpaperPrefs
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogin: () -> Unit, onWallpaperLists: () -> Unit = {}) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	Column(Modifier.fillMaxSize()) {
		TopAppBar(title = { Text(stringResource(R.string.settings)) })
		Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			SectionTitle(stringResource(R.string.account))
			Card(Modifier.padding(horizontal = 12.dp)) {
				if (PixivAuth.isLoggedIn) {
					ListItem(
						headlineContent = { Text(PixivAuth.userName ?: "") },
						supportingContent = { Text(stringResource(R.string.logged_in)) },
						trailingContent = { TextButton(onClick = { PixivAuth.logout() }) { Text(stringResource(R.string.logout)) } },
					)
				} else {
					ListItem(
						headlineContent = { Text(stringResource(R.string.not_logged_in)) },
						supportingContent = { Text(stringResource(R.string.login_needed_body)) },
						trailingContent = { Button(onClick = onLogin) { Text(stringResource(R.string.login)) } },
					)
				}
			}

			SectionTitle(stringResource(R.string.appearance))
			Card(Modifier.padding(horizontal = 12.dp)) {
				ListItem(
					headlineContent = { Text(stringResource(R.string.language)) },
					supportingContent = { Row(modifier = Modifier.padding(top = 6.dp)) { LanguageChips() } },
				)
				ListItem(
					headlineContent = { Text(stringResource(R.string.theme_mode)) },
					supportingContent = {
						Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
							FilterChip(selected = AppPrefs.themeMode == ThemeMode.SYSTEM, onClick = { AppPrefs.updateThemeMode(ThemeMode.SYSTEM) }, label = { Text(stringResource(R.string.mode_system)) })
							FilterChip(selected = AppPrefs.themeMode == ThemeMode.LIGHT, onClick = { AppPrefs.updateThemeMode(ThemeMode.LIGHT) }, label = { Text(stringResource(R.string.mode_light)) })
							FilterChip(selected = AppPrefs.themeMode == ThemeMode.DARK, onClick = { AppPrefs.updateThemeMode(ThemeMode.DARK) }, label = { Text(stringResource(R.string.mode_dark)) })
						}
					},
				)
				ListItem(
					headlineContent = { Text(stringResource(R.string.amoled)) },
					supportingContent = { Text(stringResource(R.string.amoled_summary)) },
					trailingContent = { Switch(checked = AppPrefs.amoled, onCheckedChange = { AppPrefs.updateAmoled(it) }) },
				)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					ListItem(
						headlineContent = { Text(stringResource(R.string.dynamic_color)) },
						supportingContent = { Text(stringResource(R.string.dynamic_color_summary)) },
						trailingContent = { Switch(checked = AppPrefs.dynamicColor, onCheckedChange = { AppPrefs.updateDynamicColor(it) }) },
					)
				}
				ListItem(
					headlineContent = { Text(stringResource(R.string.grid_columns)) },
					supportingContent = {
						Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
							for (n in 1..4) FilterChip(selected = AppPrefs.gridColumns == n, onClick = { AppPrefs.updateGridColumns(n) }, label = { Text("$n") })
						}
					},
				)
			}

			SectionTitle(stringResource(R.string.content))
			Card(Modifier.padding(horizontal = 12.dp)) {
				ListItem(
					headlineContent = { Text(stringResource(R.string.show_adult)) },
					supportingContent = { Text(stringResource(R.string.show_adult_summary)) },
					trailingContent = { Switch(checked = AppPrefs.showAdult, onCheckedChange = { AppPrefs.updateShowAdult(it) }) },
				)
			}

			SectionTitle(stringResource(R.string.reader_section))
			Card(Modifier.padding(horizontal = 12.dp)) {
				ListItem(
					headlineContent = { Text(stringResource(R.string.translation_style)) },
					supportingContent = {
						Column {
							Text(stringResource(R.string.translation_style_summary))
							Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
								FilterChip(selected = AppPrefs.translationNotes, onClick = { AppPrefs.updateTranslationNotes(true) }, label = { Text(stringResource(R.string.style_notes)) })
								FilterChip(selected = !AppPrefs.translationNotes, onClick = { AppPrefs.updateTranslationNotes(false) }, label = { Text(stringResource(R.string.style_overlay)) })
							}
						}
					},
				)
				ListItem(
					headlineContent = { Text(stringResource(R.string.translation_text_size)) },
					supportingContent = {
						Column {
							Text(stringResource(R.string.translation_text_size_summary))
							Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
								listOf(10 to R.string.size_small, 14 to R.string.size_normal, 18 to R.string.size_large, 24 to R.string.size_huge).forEach { (sp, label) ->
									FilterChip(selected = AppPrefs.translationTextSize == sp, onClick = { AppPrefs.updateTranslationTextSize(sp) }, label = { Text(stringResource(label)) })
								}
							}
						}
					},
				)
				ListItem(
					headlineContent = { Text(stringResource(R.string.translate_mode)) },
					supportingContent = {
						Column {
							Text(stringResource(R.string.translate_mode_summary))
							Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
								FilterChip(selected = !AppPrefs.translateOffline, onClick = { AppPrefs.updateTranslateOffline(false) }, label = { Text(stringResource(R.string.translate_online)) })
								FilterChip(selected = AppPrefs.translateOffline, onClick = { AppPrefs.updateTranslateOffline(true) }, label = { Text(stringResource(R.string.translate_offline)) })
							}
						}
					},
				)
				LaunchedEffect(Unit) { TranslationModels.refresh() }
				ListItem(
					headlineContent = { Text(stringResource(R.string.dictionaries)) },
					supportingContent = { Text(stringResource(R.string.dictionaries_summary, TranslationModels.name(PageTranslator.target()))) },
				)
				val tags = (PageTranslator.Source.entries.map { it.tag } + PageTranslator.target()).distinct().filter { it != "en" }
				tags.forEach { tag ->
					val have = tag in TranslationModels.downloaded
					val busy = TranslationModels.downloading[tag] == true
					ListItem(
						headlineContent = { Text(TranslationModels.name(tag)) },
						supportingContent = { Text(if (busy) stringResource(R.string.checking) else if (have) stringResource(R.string.model_on_phone) else stringResource(R.string.model_not_on_phone)) },
						trailingContent = {
							if (have) TextButton(onClick = { TranslationModels.delete(tag) }) { Text(stringResource(R.string.delete)) }
							else TextButton(onClick = { TranslationModels.downloadInBackground(tag) }, enabled = !busy) { Text(stringResource(R.string.download)) }
						},
					)
				}
				TranslationModels.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
			}

			SectionTitle(stringResource(R.string.wallpaper))
			WallpaperSection(onWallpaperLists)

			SectionTitle(stringResource(R.string.about))
			Card(Modifier.padding(horizontal = 12.dp)) {
				val update = Updates.available
				ListItem(
					headlineContent = { Text(stringResource(R.string.check_updates)) },
					supportingContent = {
						Text(
							when {
								Updates.checking -> stringResource(R.string.checking)
								update != null -> stringResource(R.string.update_available, update.version)
								Updates.lastResult == "" -> stringResource(R.string.up_to_date)
								Updates.lastResult != null && Updates.isNewer(Updates.lastResult!!, BuildConfig.VERSION_NAME) -> stringResource(R.string.update_available, Updates.lastResult!!)
								Updates.lastResult != null -> stringResource(R.string.update_check_failed, Updates.lastResult!!)
								else -> stringResource(R.string.check_updates_summary)
							},
						)
					},
					trailingContent = {
						if (update != null) Button(onClick = { context.openUrl(update.apkUrl ?: update.pageUrl) }) { Text(stringResource(R.string.update_download)) }
						else TextButton(onClick = { scope.launch { Updates.check(force = true) } }, enabled = !Updates.checking) { Text(stringResource(R.string.search)) }
					},
				)
			}
			AboutSection()
			Text(
				"Mokona ${BuildConfig.VERSION_NAME}",
				style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.fillMaxWidth().padding(16.dp),
			)
		}
	}
	// keeps the context import honest when the About rows need it
	context.hashCode()
}

@Composable
fun SectionTitle(text: String) {
	Text(
		text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
	)
}

/** Settings › Wallpaper: the switch, the source, how often, where, and the last result. */
@Composable
private fun WallpaperSection(onWallpaperLists: () -> Unit) {
	val context = LocalContext.current
	Card(Modifier.padding(horizontal = 12.dp)) {
		ListItem(
			headlineContent = { Text(stringResource(R.string.wallpaper_enabled)) },
			supportingContent = { Text(stringResource(R.string.wallpaper_enabled_summary)) },
			trailingContent = { Switch(checked = WallpaperPrefs.enabled, onCheckedChange = { WallpaperPrefs.updateEnabled(context, it) }) },
		)
		ListItem(
			headlineContent = { Text(stringResource(R.string.wallpaper_source)) },
			supportingContent = {
				Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
					Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						FilterChip(selected = WallpaperPrefs.source == WallpaperPrefs.Source.RECOMMENDED, onClick = { WallpaperPrefs.updateSource(WallpaperPrefs.Source.RECOMMENDED) }, label = { Text(stringResource(R.string.source_recommended)) })
						FilterChip(selected = WallpaperPrefs.source == WallpaperPrefs.Source.FOLLOWING, onClick = { WallpaperPrefs.updateSource(WallpaperPrefs.Source.FOLLOWING) }, label = { Text(stringResource(R.string.source_following)) })
					}
					Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						FilterChip(selected = WallpaperPrefs.source == WallpaperPrefs.Source.BOOKMARKS, onClick = { WallpaperPrefs.updateSource(WallpaperPrefs.Source.BOOKMARKS) }, label = { Text(stringResource(R.string.source_bookmarks)) })
						FilterChip(selected = WallpaperPrefs.source == WallpaperPrefs.Source.LIST, onClick = { WallpaperPrefs.updateSource(WallpaperPrefs.Source.LIST) }, label = { Text(stringResource(R.string.source_list)) })
					}
					if (WallpaperPrefs.source == WallpaperPrefs.Source.LIST) {
						Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
							WallpaperPrefs.lists.forEach { l ->
								FilterChip(selected = WallpaperPrefs.listId == l.id, onClick = { WallpaperPrefs.updateListId(l.id) }, label = { Text("${l.name} · ${l.items.size}") })
							}
						}
					}
				}
			},
		)
		ListItem(
			headlineContent = { Text(stringResource(R.string.wallpaper_lists)) },
			supportingContent = { Text(stringResource(R.string.wallpaper_lists_summary, WallpaperPrefs.lists.size)) },
			trailingContent = { TextButton(onClick = onWallpaperLists) { Text("→") } },
		)
		ListItem(
			headlineContent = { Text(stringResource(R.string.wallpaper_interval)) },
			supportingContent = {
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
					WallpaperPrefs.intervals.forEach { m ->
						val label = when { m < 60 -> stringResource(R.string.every_minutes, m); m < 1440 -> stringResource(R.string.every_hours, m / 60); else -> stringResource(R.string.every_day) }
						FilterChip(selected = WallpaperPrefs.intervalMinutes == m, onClick = { WallpaperPrefs.updateInterval(context, m) }, label = { Text(label) })
					}
				}
			},
		)
		ListItem(
			headlineContent = { Text(stringResource(R.string.wallpaper_target)) },
			supportingContent = {
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
					FilterChip(selected = WallpaperPrefs.target == WallpaperPrefs.Target.HOME, onClick = { WallpaperPrefs.updateTarget(WallpaperPrefs.Target.HOME) }, label = { Text(stringResource(R.string.target_home)) })
					FilterChip(selected = WallpaperPrefs.target == WallpaperPrefs.Target.LOCK, onClick = { WallpaperPrefs.updateTarget(WallpaperPrefs.Target.LOCK) }, label = { Text(stringResource(R.string.target_lock)) })
					FilterChip(selected = WallpaperPrefs.target == WallpaperPrefs.Target.BOTH, onClick = { WallpaperPrefs.updateTarget(WallpaperPrefs.Target.BOTH) }, label = { Text(stringResource(R.string.target_both)) })
				}
			},
		)
		ListItem(
			headlineContent = { Text(stringResource(R.string.wifi_only)) },
			trailingContent = { Switch(checked = WallpaperPrefs.wifiOnly, onCheckedChange = { WallpaperPrefs.updateWifiOnly(context, it) }) },
		)
		ListItem(
			headlineContent = { Text(stringResource(R.string.change_now)) },
			supportingContent = {
				val err = WallpaperPrefs.lastError
				val last = WallpaperPrefs.lastTitle
				if (err != null) Text(stringResource(R.string.wallpaper_error, err), color = MaterialTheme.colorScheme.error)
				else if (last != null) Text(stringResource(R.string.wallpaper_last, last))
			},
			trailingContent = { Button(onClick = { WallpaperPrefs.changeNow(context) }, enabled = PixivAuth.isLoggedIn) { Text(stringResource(R.string.change_now)) } },
		)
	}
}
