package com.ssr.safitsafety.navigation.pages

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.ssr.safitsafety.components.numberpicker.PhoneNumberInput
import com.ssr.safitsafety.data.PhoneNumberEntry
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
                                            age = age!!,
                                            phoneNumber = ArrayList()
                                        )
                                    )
                                    navController.navigate(Screen.Data.route) {
                                        popUpToTop(navController)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Failed to save data",
                                        Toast.LENGTH_SHORT
                                    ).show()
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

            //TODO: replace with store fetch and save
            MultiPhoneNumberInput(initialPhoneNumbers = listOf(
                "+1-5555555555",
                "+44-1234567890"
            ),
                onPhoneNumbersChanged = {})
        }
    }
}

@Composable
fun MultiPhoneNumberInput(
    modifier: Modifier = Modifier,
    initialPhoneNumbers: List<String> = listOf(),
    onPhoneNumbersChanged: (List<String>) -> Unit
) {

    fun List<String>.toPhoneNumberEntries(): List<PhoneNumberEntry> {
        return this.map { PhoneNumberEntry(number = it) }
    }

    var phoneNumbers by remember(initialPhoneNumbers) { mutableStateOf(initialPhoneNumbers.toPhoneNumberEntries()) }
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        )
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(state = scrollState)
        ) {
            Text(
                text = "Emergency Contacts",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            phoneNumbers.forEachIndexed { index, phoneNumber ->
                key(phoneNumber.id) {  // Add key for proper recomposition
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        PhoneNumberInput(
                            onPhoneNumberChanged = { newNumber ->
                                phoneNumbers = phoneNumbers.toMutableList().apply {
                                    this[index] = this[index].copy(number = newNumber)
                                }
                                onPhoneNumbersChanged(phoneNumbers.map { it.number })
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 12.dp),
                            initialPhoneNumber = phoneNumber.number
                        )

                        if (phoneNumbers.size > 1) {
                            Box(
                                modifier = Modifier
                                    .offset(y = (-12).dp)
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        phoneNumbers =
                                            phoneNumbers.filterIndexed { i, _ -> i != index }
                                        onPhoneNumbersChanged(phoneNumbers.map { it.number })
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove phone number",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Add button
            TextButton(
                onClick = {
                    phoneNumbers = phoneNumbers + PhoneNumberEntry(number = "")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add phone number",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Phone Number")
            }
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