package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.common.DataAccessException

/**
 * The id of the signed-in user, read from the Supabase session at the moment of each call. The `uid` that the
 * repository interfaces still receive from the UI is never trusted: with row-level security it could not reach
 * another user's rows anyway, but the row owner must always be the session's user.
 *
 * No session is reported as [FailureKind.PermissionDenied] (as an unauthenticated request would be), before any request.
 */
fun requireSessionUserId(currentUserId: () -> String?): String =
    currentUserId() ?: throw DataAccessException(FailureKind.PermissionDenied)
