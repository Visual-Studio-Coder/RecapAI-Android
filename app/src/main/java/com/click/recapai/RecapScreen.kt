package com.click.recapai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    viewModel: GeminiAPIViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf(TextFieldValue("")) }
    var urlList by remember { mutableStateOf(mutableListOf<String>()) }
    var imageUris by remember { mutableStateOf(mutableListOf<Uri>()) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris != null) {
            imageUris = uris.take(5).toMutableList() // Limit to 5 images
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Recap AI") },
                // ... other parameters
                actions = {
                    // ... other actions

                    IconButton(onClick = { /* Handle settings click */
                        showBottomSheet = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )

        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                    },
                    sheetState = sheetState
                ) {
                    // Sheet content
                    Button(onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    }) {
                        Text("Hide bottom sheet")
                    }
                }
            }
            Text(
                text = "Upload Images (up to 5)",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Button(onClick = { imageLauncher.launch("image/*") }) {
                    Text(text = "Select Images")
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(imageUris) { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(model = uri),
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .border(BorderStroke(1.dp, Color.Gray)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Enter Text") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "Enter your notes here") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Add URLs (up to 5)",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            urlList.forEachIndexed { index, url ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    TextField(
                        value = url,
                        onValueChange = { newUrl -> urlList[index] = newUrl },
                        label = { Text("URL ${index + 1}") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { urlList.removeAt(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove URL")
                    }
                }
            }

            if (urlList.size < 5) {
                Button(onClick = { urlList.add("") }) {
                    Text(text = "Add URL")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Placeholder for a send button to be implemented later
            Button(
                onClick = {
                    /* Implement send logic here */

                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Send", fontSize = 18.sp)
            }
        }

    }
}