package com.ssr.safitsafety.data

import java.util.UUID

data class PhoneNumberEntry(val id: String = UUID.randomUUID().toString(), val number: String)