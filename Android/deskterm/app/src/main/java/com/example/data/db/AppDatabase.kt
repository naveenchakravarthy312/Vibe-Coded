package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CommandHistoryItem
import com.example.data.model.Snippet
import com.example.data.model.SshHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SshHost::class, Snippet::class, CommandHistoryItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sshHostDao(): SshHostDao
    abstract fun snippetDao(): SnippetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deskterm_database"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default command snippets
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).snippetDao().insertSnippets(defaultSnippets)
                            }
                        }
                    })
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val defaultSnippets = listOf(
            Snippet(title = "Disk Usage", command = "df -h", category = "System", isPreset = true),
            Snippet(title = "Memory Stats", command = "free -h", category = "System", isPreset = true),
            Snippet(title = "Uptime & Load", command = "uptime", category = "System", isPreset = true),
            Snippet(title = "Kernel & OS Info", command = "uname -a", category = "System", isPreset = true),
            Snippet(title = "Top CPU Processes", command = "ps aux --sort=-%cpu | head -n 10", category = "Processes", isPreset = true),
            Snippet(title = "Top RAM Processes", command = "ps aux --sort=-%mem | head -n 10", category = "Processes", isPreset = true),
            Snippet(title = "Active Network Ports", command = "ss -tuln", category = "Network", isPreset = true),
            Snippet(title = "IP Addresses", command = "ip addr show", category = "Network", isPreset = true),
            Snippet(title = "Ping Gateway", command = "ping -c 4 1.1.1.1", category = "Network", isPreset = true),
            Snippet(title = "Docker Containers", command = "docker ps -a", category = "Dev & Docker", isPreset = true),
            Snippet(title = "Systemd Failed Units", command = "systemctl --failed", category = "System", isPreset = true),
            Snippet(title = "Recent System Logs", command = "journalctl -n 20 --no-pager", category = "System", isPreset = true)
        )
    }
}
