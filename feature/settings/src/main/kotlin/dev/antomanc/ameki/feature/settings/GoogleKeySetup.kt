package dev.antomanc.ameki.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.antomanc.ameki.core.data.sync.SyncError
import dev.antomanc.ameki.core.model.AndroidAppIdentity

/**
 * Step-by-step guide to a Google Weather API key, plus the field and the "Verify" button.
 * Only reachable in builds with the Google source (docs/adr/0002).
 */
@Composable
fun GoogleKeySetup(
    hasKey: Boolean,
    check: KeyCheck,
    identity: AndroidAppIdentity?,
    onSubmit: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
        if (hasKey && check !is KeyCheck.Invalid) {
            Text(stringResource(R.string.key_saved), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRemove) { Text(stringResource(R.string.key_remove)) }
            return@Column
        }
        Text(stringResource(R.string.key_guide_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        listOf(R.string.key_step_1, R.string.key_step_2, R.string.key_step_3, R.string.key_step_4, R.string.key_step_5).forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                Text(stringResource(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (identity != null) {
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.key_restrict_values), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Google's form has two separate fields, so each value gets its own copy button.
            CopyRow(stringResource(R.string.key_package), identity.packageName)
            CopyRow(stringResource(R.string.key_sha1), identity.certSha1)
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/library/weather.googleapis.com")))
        }) { Text(stringResource(R.string.key_open_console)) }
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text(stringResource(R.string.key_field)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            isError = check is KeyCheck.Invalid,
            supportingText = (check as? KeyCheck.Invalid)?.let { { Text(stringResource(keyError(it.error))) } },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSubmit(key) }, enabled = key.isNotBlank() && check != KeyCheck.Checking) { Text(stringResource(R.string.key_verify)) }
            when (check) {
                KeyCheck.Checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                KeyCheck.Valid -> Text(stringResource(R.string.key_valid), color = MaterialTheme.colorScheme.primary)
                else -> {}
            }
        }
    }
}

private fun keyError(e: SyncError) = when (e) {
    SyncError.INVALID_KEY, SyncError.MISSING_KEY -> R.string.key_err_invalid
    SyncError.ACCESS_DENIED -> R.string.key_err_denied
    SyncError.NETWORK -> R.string.key_err_network
    SyncError.QUOTA -> R.string.key_err_quota
    else -> R.string.key_err_unknown
}

@Composable
private fun CopyRow(label: String, value: String) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
        }) { Text(stringResource(R.string.copy)) }
    }
}
