package com.ismartcoding.plain.platform

/** The device's primary LAN IPv4 address (e.g. "192.168.1.5"), or null if not on a network. */
expect fun lanIpAddress(): String?
