package com.westly.opay.backend

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Runs one backend call and turns every failure into a [BackendError]. */
internal suspend fun <T> backendCall(block: suspend () -> T): BackendResult<T> {
    if (!BackendConfig.isConfigured) return BackendResult.Failure(networkError())
    return try {
        BackendResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackendError) {
        BackendResult.Failure(e)
    } catch (e: RestException) {
        BackendResult.Failure(
            BackendErrorParser.fromResponseTexts(listOf(e.description, e.error, e.message), e.statusCode)
        )
    } catch (e: HttpRequestException) {
        BackendResult.Failure(networkError())
    } catch (e: IOException) {
        BackendResult.Failure(networkError())
    } catch (e: Exception) {
        BackendResult.Failure(BackendError(BackendCodes.UNKNOWN, BackendCodes.messageFor(BackendCodes.UNKNOWN)))
    }
}

private fun networkError(): BackendError =
    BackendError(BackendCodes.NETWORK, BackendCodes.messageFor(BackendCodes.NETWORK))
