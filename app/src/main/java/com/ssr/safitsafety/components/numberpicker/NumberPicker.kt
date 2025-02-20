package com.ssr.safitsafety.components.numberpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumberInput(
    modifier: Modifier = Modifier,
    initialPhoneNumber: String = "",
    onPhoneNumberChanged: (String) -> Unit,
) {

    val initialState = remember(initialPhoneNumber) {
        if (initialPhoneNumber.isNotEmpty()) {
            val parts = initialPhoneNumber.split("-")
            if (parts.size == 2) {
                val code = parts[0]
                val number = parts[1]
                // Use the companion object to find the country
                val country = CountryData.phoneCodeMap[code] ?: CountryData.India
                Pair(country, number)
            } else {
                Pair(CountryData.India, "")
            }
        } else {
            Pair(CountryData.India, "")
        }
    }

    var selectedCountry by remember { mutableStateOf(initialState.first) }
    var phoneNumber by remember { mutableStateOf(initialState.second) }

    var isExpanded by remember { mutableStateOf(false) }

    val countries = remember { enumValues<CountryData>().sortedBy { it.name } }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Country Code Dropdown
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(56.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = it }
            ) {
                TextField(
                    value = selectedCountry.countryPhoneCode,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false }
                ) {
                    countries.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Text("${country.countryIso} ${country.countryPhoneCode}")
                            },
                            onClick = {
                                selectedCountry = country
                                isExpanded = false
                                // Update phone number with new country code
                                if (phoneNumber.isNotEmpty()) {
                                    onPhoneNumberChanged("${selectedCountry.countryPhoneCode}-$phoneNumber")
                                }
                            }
                        )
                    }
                }
            }
        }

        // Phone Number TextField
        TextField(
            value = phoneNumber,
            onValueChange = { newValue ->
                // Only allow digits
                if (newValue.all { it.isDigit() || it.isWhitespace() }) {
                    phoneNumber = newValue.filter { it.isDigit() }
                    if (phoneNumber.isNotEmpty()) {
                        onPhoneNumberChanged("${selectedCountry.countryPhoneCode}-$phoneNumber")
                    } else {
                        onPhoneNumberChanged("")
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            placeholder = { Text("Phone Number") }
        )
    }
}
