package com.example.nightwatchprotocol.data.common

/** Why a data or storage call failed, in terms the UI can explain without technical details. */
enum class FailureKind { Offline, PermissionDenied, Unknown }

/** Every failure of a data or storage call, classified. The technical cause is kept only as [cause], never shown. */
class DataAccessException(
    val kind: FailureKind,
    cause: Throwable? = null,
) : Exception("El servidor de datos no respondió: $kind", cause)

/** How long a small database call may take before it is reported as [FailureKind.Offline]. */
const val DATA_TIMEOUT_MILLIS = 5_000L
