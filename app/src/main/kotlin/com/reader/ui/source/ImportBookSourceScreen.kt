package com.reader.ui.source

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBookSourceScreen(
    onDone: () -> Unit,
    vm: ImportBookSourceViewModel = viewModel()
) {
    val json by vm.json.collectAsState()
    val state by vm.uiState.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("导入书源") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = json,
                onValueChange = vm::updateJson,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                placeholder = { Text("粘贴 Legado 书源 JSON") }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = vm::import,
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is ImportUiState.Loading
            ) { Text("导入") }
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                is ImportUiState.Success -> Text("导入成功", color = MaterialTheme.colorScheme.primary)
                is ImportUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                else -> {}
            }
            if (state is ImportUiState.Success) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDone) { Text("完成") }
            }
        }
    }
}
