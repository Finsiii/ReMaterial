package com.rematerial.app.feature.identity.data

import android.content.Context
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.AccountRecord
import com.rematerial.app.feature.identity.domain.AccountStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InMemoryAccountStore : AccountStore {
    private val mutex = Mutex()
    private val records = linkedMapOf<String, AccountRecord>()

    override suspend fun seedIfEmpty(records: List<AccountRecord>) = mutex.withLock {
        if (this.records.isEmpty()) records.forEach { this.records[it.session.email.normalized()] = it }
    }

    override suspend fun findByEmail(email: String): AccountRecord? = mutex.withLock {
        records[email.normalized()]
    }

    override suspend fun create(record: AccountRecord): Result<Unit> = mutex.withLock {
        val key = record.session.email.normalized()
        if (records.containsKey(key)) Result.Failure(DomainFailure.Validation(listOf("Email sudah terdaftar.")))
        else {
            records[key] = record
            Result.Success(Unit)
        }
    }

    override suspend fun update(record: AccountRecord): Result<Unit> = mutex.withLock {
        val key = record.session.email.normalized()
        if (!records.containsKey(key)) Result.Failure(DomainFailure.Unauthorized)
        else {
            records[key] = record
            Result.Success(Unit)
        }
    }
}

/** Small mock-compatible password primitive. Replace with the API auth contract in production. */
object PasswordDigests {
    fun newSalt(): String = java.util.UUID.randomUUID().toString()

    fun digest(password: String, salt: String): String = MessageDigest.getInstance("SHA-256")
        .digest((salt + password).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun matches(password: String, salt: String, expected: String): Boolean =
        MessageDigest.isEqual(digest(password, salt).toByteArray(), expected.toByteArray())
}

@Singleton
class AndroidAccountStore @Inject constructor(@ApplicationContext context: Context) : AccountStore {
    private val preferences = context.getSharedPreferences("rematerial_accounts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    override suspend fun seedIfEmpty(records: List<AccountRecord>) = mutex.withLock {
        if (read().isEmpty()) write(records)
    }

    override suspend fun findByEmail(email: String): AccountRecord? = mutex.withLock {
        read().firstOrNull { it.session.email.equals(email.trim(), ignoreCase = true) }
    }

    override suspend fun create(record: AccountRecord): Result<Unit> = mutex.withLock {
        val records = read().toMutableList()
        if (records.any { it.session.email.equals(record.session.email, ignoreCase = true) }) {
            Result.Failure(DomainFailure.Validation(listOf("Email sudah terdaftar.")))
        } else {
            records += record
            write(records)
            Result.Success(Unit)
        }
    }

    override suspend fun update(record: AccountRecord): Result<Unit> = mutex.withLock {
        val records = read().toMutableList()
        val index = records.indexOfFirst { it.session.email.equals(record.session.email, ignoreCase = true) }
        if (index < 0) Result.Failure(DomainFailure.Unauthorized)
        else {
            records[index] = record
            write(records)
            Result.Success(Unit)
        }
    }

    private fun read(): List<AccountRecord> = preferences.getString(KEY, null)?.let {
        runCatching { json.decodeFromString<List<AccountRecord>>(it) }.getOrDefault(emptyList())
    }.orEmpty()

    private fun write(records: List<AccountRecord>) {
        // apply() avoids crashing the UI thread on a transient preference commit failure.
        preferences.edit().putString(KEY, json.encodeToString(records)).apply()
    }

    private companion object { const val KEY = "account_records_v1" }
}

private fun String.normalized(): String = trim().lowercase()
