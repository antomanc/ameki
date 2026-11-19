package dev.antomanc.ameki.feature.locations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.model.PlaceSearchResult

/** City search field with live results. Shared by onboarding and the places screen. */
@Composable
fun PlaceSearch(state: SearchState, onQuery: (String) -> Unit, onPick: (PlaceSearchResult) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shapes.large,
            placeholder = { Text(stringResource(R.string.search_city)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                when {
                    state.searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.query.isNotEmpty() -> IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.clear_search))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        if (state.failed) {
            Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        } else if (!state.searching && state.query.trim().length >= 2 && state.results.isEmpty()) {
            Text(stringResource(R.string.search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        if (state.results.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    state.results.forEachIndexed { index, r ->
                        ListItem(
                            headlineContent = { Text(r.name) },
                            supportingContent = { listOfNotNull(r.region, r.country).joinToString(", ").takeIf { it.isNotEmpty() }?.let { Text(it) } },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clickable {
                                keyboard?.hide()
                                onPick(r)
                            },
                        )
                        if (index < state.results.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

