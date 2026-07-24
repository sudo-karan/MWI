package com.ismartcoding.plain.platform

import java.util.UUID

actual fun newId(): String = UUID.randomUUID().toString()
