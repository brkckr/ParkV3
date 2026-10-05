package com.brkckr.parkv3.ui.language

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.brkckr.parkv3.R

/** Language button with a menu; choosing the current language does nothing. */
@Composable
fun LanguageMenu(current: AppLanguage, onSelect: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Language, contentDescription = stringResource(R.string.action_language))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                val isCurrent = language == current
                DropdownMenuItem(
                    text = { Text(label(language)) },
                    onClick = {
                        expanded = false
                        if (!isCurrent) onSelect(language)
                    },
                    trailingIcon = if (isCurrent) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { selected = isCurrent },
                )
            }
        }
    }
}

@Composable
private fun label(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.TURKISH -> stringResource(R.string.language_turkish)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
}
