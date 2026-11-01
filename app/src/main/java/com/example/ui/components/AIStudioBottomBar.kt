package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.AppTab

@Composable
fun AIStudioBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.WEB_STUDIO,
            onClick = { onTabSelected(AppTab.WEB_STUDIO) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Web Studio",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Web Studio") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("tab_web_studio")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.NATIVE_PLAYGROUND,
            onClick = { onTabSelected(AppTab.NATIVE_PLAYGROUND) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Native Chat",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Native Chat") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("tab_native_chat")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.PROMPT_LIBRARY,
            onClick = { onTabSelected(AppTab.PROMPT_LIBRARY) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = "Prompts",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Prompts") },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("tab_prompts")
        )

        NavigationBarItem(
            selected = false,
            onClick = onOpenSettings,
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text("Settings") },
            modifier = Modifier.testTag("tab_settings")
        )
    }
}
