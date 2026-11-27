package io.reader.rebuild.core

import kotlinx.coroutines.CancellationException

internal inline fun <ResultType> readerResult(body: () -> ResultType): Result<ResultType> =
    try { Result.success(body()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { Result.failure(failure) }
