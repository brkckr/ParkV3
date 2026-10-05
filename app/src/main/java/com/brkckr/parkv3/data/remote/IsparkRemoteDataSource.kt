package com.brkckr.parkv3.data.remote

import com.brkckr.parkv3.data.remote.parse.DetailParseResult
import com.brkckr.parkv3.data.remote.parse.ListParseResult
import com.brkckr.parkv3.data.remote.parse.ParkJsonParser
import com.brkckr.parkv3.domain.model.RefreshError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import javax.inject.Inject

interface IsparkApi {
    @GET("Park")
    suspend fun parks(): JsonElement

    @GET("ParkDetay")
    suspend fun parkDetail(@Query("id") parkId: Int): JsonElement
}

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val error: RefreshError) : RemoteResult<Nothing>
}

/**
 * Turns transport outcomes into typed results. Server bodies of failed responses are never
 * read or surfaced; only the HTTP status code is kept. Cancellation is always rethrown.
 */
class IsparkRemoteDataSource @Inject constructor(private val api: IsparkApi) {

    suspend fun fetchParks(): RemoteResult<ListParseResult> =
        call { ParkJsonParser.parseList(api.parks()) }

    suspend fun fetchDetail(parkId: Int, fetchedAtMillis: Long): RemoteResult<DetailParseResult> =
        call { ParkJsonParser.parseDetail(api.parkDetail(parkId), parkId, fetchedAtMillis) }

    private inline fun <T> call(block: () -> T): RemoteResult<T> = try {
        RemoteResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        RemoteResult.Failure(RefreshError.Http(e.code()))
    } catch (e: IOException) {
        RemoteResult.Failure(RefreshError.Network)
    } catch (e: SerializationException) {
        RemoteResult.Failure(RefreshError.Malformed)
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization decoding errors that are not SerializationException.
        RemoteResult.Failure(RefreshError.Malformed)
    }
}
