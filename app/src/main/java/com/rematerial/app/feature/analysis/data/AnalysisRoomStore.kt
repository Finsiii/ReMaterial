package com.rematerial.app.feature.analysis.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.analysis.domain.AnalysisSession
import com.rematerial.app.feature.analysis.domain.AnalysisPersistenceSnapshot
import com.rematerial.app.feature.analysis.domain.AnalysisSessionRepository
import com.rematerial.app.feature.analysis.domain.SavedAnalysisIdea
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException

@Entity(tableName = "analysis_state")
data class AnalysisStateEntity(
    @PrimaryKey val recordId: String,
    val recordType: String,
    val payload: String,
    val mediaPath: String?,
    val updatedAtEpochMs: Long,
)

@Dao
interface AnalysisStateDao {
    @Query("SELECT * FROM analysis_state")
    suspend fun all(): List<AnalysisStateEntity>

    @Query("SELECT * FROM analysis_state WHERE recordId = :id LIMIT 1")
    suspend fun find(id: String): AnalysisStateEntity?

    @Query("SELECT * FROM analysis_state WHERE recordType = :type ORDER BY updatedAtEpochMs DESC")
    suspend fun byType(type: String): List<AnalysisStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnalysisStateEntity)

    @Query("DELETE FROM analysis_state WHERE recordId = :id")
    suspend fun delete(id: String)

    @Query("SELECT mediaPath FROM analysis_state WHERE mediaPath IS NOT NULL")
    suspend fun referencedMediaPaths(): List<String>
}

@Database(entities = [AnalysisStateEntity::class], version = 1, exportSchema = true)
abstract class AnalysisDatabase : RoomDatabase() {
    abstract fun analysisStateDao(): AnalysisStateDao
}

class RoomAnalysisSessionRepository(
    private val database: AnalysisDatabase,
    private val json: Json,
) : AnalysisSessionRepository {
    private val dao get() = database.analysisStateDao()

    override suspend fun loadSnapshot(): Result<AnalysisPersistenceSnapshot> = read {
        val rows = dao.all()
        val session = rows.firstOrNull { it.recordId == SESSION_ID }?.payload?.let {
            json.decodeFromString(AnalysisSession.serializer(), it)
        }
        val ideas = rows.filter { it.recordType == TYPE_IDEA }.sortedByDescending(AnalysisStateEntity::updatedAtEpochMs).map {
            json.decodeFromString(SavedAnalysisIdea.serializer(), it.payload)
        }
        AnalysisPersistenceSnapshot(session, ideas, rows.mapNotNullTo(linkedSetOf(), AnalysisStateEntity::mediaPath))
    }

    override suspend fun loadSession(): Result<AnalysisSession?> = read {
        dao.find(SESSION_ID)?.payload?.let { json.decodeFromString(AnalysisSession.serializer(), it) }
    }

    override suspend fun saveSession(session: AnalysisSession): Result<Unit> = write {
        dao.upsert(
            AnalysisStateEntity(
                SESSION_ID,
                TYPE_SESSION,
                json.encodeToString(AnalysisSession.serializer(), session),
                session.photo?.privatePath,
                System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearSession(): Result<Unit> = write { dao.delete(SESSION_ID) }

    override suspend fun saveIdea(idea: SavedAnalysisIdea): Result<Unit> = write {
        dao.upsert(
            AnalysisStateEntity(
                "idea:${idea.analysisId.value}:${idea.optionId.value}",
                TYPE_IDEA,
                json.encodeToString(SavedAnalysisIdea.serializer(), idea),
                idea.photo?.privatePath,
                System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun savedIdeas(): Result<List<SavedAnalysisIdea>> = read {
        dao.byType(TYPE_IDEA).map { json.decodeFromString(SavedAnalysisIdea.serializer(), it.payload) }
    }

    suspend fun referencedMediaPaths(): Set<String> = dao.referencedMediaPaths().toSet()

    private suspend fun <T> read(block: suspend () -> T): Result<T> = try {
        Result.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SQLiteException) {
        Result.Failure(DomainFailure.Unavailable)
    } catch (_: IOException) {
        Result.Failure(DomainFailure.Unavailable)
    } catch (_: SerializationException) {
        Result.Failure(DomainFailure.MalformedResponse)
    } catch (_: RuntimeException) {
        Result.Failure(DomainFailure.Unavailable)
    }

    private suspend fun write(block: suspend () -> Unit): Result<Unit> = try {
        database.withTransaction(block)
        Result.Success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SQLiteException) {
        Result.Failure(DomainFailure.Unavailable)
    } catch (_: IOException) {
        Result.Failure(DomainFailure.Unavailable)
    } catch (_: SerializationException) {
        Result.Failure(DomainFailure.MalformedResponse)
    } catch (_: IllegalStateException) {
        Result.Failure(DomainFailure.Unavailable)
    } catch (_: RuntimeException) {
        Result.Failure(DomainFailure.Unavailable)
    }

    private companion object {
        const val SESSION_ID = "active_session"
        const val TYPE_SESSION = "session"
        const val TYPE_IDEA = "idea"
    }
}

class InMemoryAnalysisSessionRepository : AnalysisSessionRepository {
    private var session: AnalysisSession? = null
    private val ideas = linkedMapOf<String, SavedAnalysisIdea>()
    override suspend fun loadSnapshot(): Result<AnalysisPersistenceSnapshot> = Result.Success(
        AnalysisPersistenceSnapshot(
            session,
            ideas.values.toList(),
            buildSet {
                session?.photo?.privatePath?.let(::add)
                ideas.values.mapNotNullTo(this) { it.photo?.privatePath }
            },
        ),
    )
    override suspend fun loadSession(): Result<AnalysisSession?> = Result.Success(session)
    override suspend fun saveSession(session: AnalysisSession): Result<Unit> { this.session = session; return Result.Success(Unit) }
    override suspend fun clearSession(): Result<Unit> { session = null; return Result.Success(Unit) }
    override suspend fun saveIdea(idea: SavedAnalysisIdea): Result<Unit> { ideas["${idea.analysisId.value}:${idea.optionId.value}"] = idea; return Result.Success(Unit) }
    override suspend fun savedIdeas(): Result<List<SavedAnalysisIdea>> = Result.Success(ideas.values.toList())
}
