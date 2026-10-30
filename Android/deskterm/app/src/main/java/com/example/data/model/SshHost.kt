package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuthType {
    PASSWORD,
    PRIVATE_KEY
}

@Entity(tableName = "ssh_hosts")
data class SshHost(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val isAutoConnect: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null
)
