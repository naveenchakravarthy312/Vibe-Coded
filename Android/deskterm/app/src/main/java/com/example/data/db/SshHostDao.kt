package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.SshHost
import kotlinx.coroutines.flow.Flow

@Dao
interface SshHostDao {
    @Query("SELECT * FROM ssh_hosts ORDER BY isAutoConnect DESC, lastConnectedAt DESC, id DESC")
    fun getAllHostsFlow(): Flow<List<SshHost>>

    @Query("SELECT * FROM ssh_hosts WHERE id = :id LIMIT 1")
    suspend fun getHostById(id: Long): SshHost?

    @Query("SELECT * FROM ssh_hosts WHERE isAutoConnect = 1 LIMIT 1")
    suspend fun getAutoConnectHost(): SshHost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHost(host: SshHost): Long

    @Update
    suspend fun updateHost(host: SshHost)

    @Delete
    suspend fun deleteHost(host: SshHost)

    @Query("UPDATE ssh_hosts SET isAutoConnect = 0 WHERE id != :exceptId")
    suspend fun clearOtherAutoConnects(exceptId: Long)

    @Query("UPDATE ssh_hosts SET isAutoConnect = 0")
    suspend fun clearAllAutoConnects()

    @Query("UPDATE ssh_hosts SET lastConnectedAt = :timestamp WHERE id = :hostId")
    suspend fun updateLastConnected(hostId: Long, timestamp: Long)

    @Transaction
    suspend fun saveHostWithAutoConnect(host: SshHost): Long {
        val id = if (host.id == 0L) insertHost(host) else {
            updateHost(host)
            host.id
        }
        if (host.isAutoConnect) {
            clearOtherAutoConnects(id)
        }
        return id
    }
}
