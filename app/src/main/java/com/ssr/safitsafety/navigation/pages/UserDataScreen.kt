package com.ssr.safitsafety.navigation.pages

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ssr.safitsafety.MainActivity.Companion.popUpToTop
import com.ssr.safitsafety.data.UserData
import com.ssr.safitsafety.data.UserDataStoreManager
import com.ssr.safitsafety.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDataScreen(navController: NavHostController) {
    var weight by remember { mutableStateOf<Float?>(null) }
    var age by remember { mutableStateOf<Int?>(null) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Data") },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        weightError = validateWeight(weight)
                        ageError = validateAge(age)

                        if (weightError == null && ageError == null && weight != null && age != null) {
                            isLoading = true
                            scope.launch {
                                try {
                                    UserDataStoreManager.saveUserData(
                                        context,
                                        UserData(
                                            weight = weight!!,
                                            age = age!!
                                        )
                                    )
                                    navController.navigate(Screen.Data.route) {
                                        popUpToTop(navController)
                                    }
                                } catch (e: Exception) {
                                    // Handle error
                                    Toast.makeText(context, "Failed to save data", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Submit")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = weight?.toString() ?: "",
                onValueChange = {
                    weight = it.toFloatOrNull()
                    weightError = null
                },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = weightError != null,
                supportingText = weightError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = age?.toString() ?: "",
                onValueChange = {
                    age = it.toIntOrNull()
                    ageError = null
                },
                label = { Text("Age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = ageError != null,
                supportingText = ageError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                enabled = !isLoading
            )
        }
    }
}

private fun validateWeight(weight: Float?): String? {
    return when {
        weight == null -> "Weight is required"
        weight <= 0f -> "Weight must be greater than 0"
        weight > 500f -> "Please enter a realistic weight"
        else -> null
    }
}

private fun validateAge(age: Int?): String? {
    return when {
        age == null -> "Age is required"
        age <= 0 -> "Age must be greater than 0"
        age > 150 -> "Please enter a realistic age"
        else -> null
    }
}