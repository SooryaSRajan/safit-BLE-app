package com.ssr.safitsafety.navigation.pages

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
    var weight by remember { mutableStateOf<Int?>(null) }
    var age by remember { mutableStateOf<Int?>(null) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val submitButtonFocusRequester = remember { FocusRequester() }

    // Load saved data
    LaunchedEffect(Unit) {
        UserDataStoreManager.getUserData(context).collect { userData ->
            if (userData != null) {
                weight = userData.weight.takeIf { it > 0 }
                age = userData.age.takeIf { it > 0 }
            }
        }
    }

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
                                    Toast.makeText(context, "Failed to save data", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(submitButtonFocusRequester),
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
                    weight = it.toIntOrNull()
                    weightError = null
                },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        submitButtonFocusRequester.requestFocus()
                    }
                ),
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

private fun validateWeight(weight: Int?): String? {
    return when {
        weight == null -> "Weight is required"
        weight <= 0 -> "Weight must be greater than 0"
        weight > 500 -> "Please enter a realistic weight"
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