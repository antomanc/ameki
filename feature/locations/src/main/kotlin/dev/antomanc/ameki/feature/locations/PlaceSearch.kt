package dev.antomanc.ameki.feature.locations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme.shapes
import dev.antomanc.ameki.core.model.PlaceSearchResult

/** City search field with live results. Shared by onboarding and the places screen. */
@Composable
fun PlaceSearch(state: SearchState, onQuery: (String) -> Unit, onPick: (PlaceSearchResult) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = shapes.large,
            placeholder = { Text(stringResource(R.string.search_city)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (state.searching) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (state.failed) {
            Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        } else if (!state.searching && state.query.trim().length >= 2 && state.results.isEmpty()) {
            Text(stringResource(R.string.search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        state.results.forEach { r ->
            ListItem(
                headlineContent = { Text(r.name) },
                supportingContent = { listOfNotNull(r.region, r.country).joinToString(", ").takeIf { it.isNotEmpty() }?.let { Text(it) } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { onPick(r) },
            )
        }
    }
}
