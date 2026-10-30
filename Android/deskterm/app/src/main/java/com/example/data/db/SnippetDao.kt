package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CommandHistoryItem
import com.example.data.model.Snippet
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM command_snippets ORDER BY category ASC, id ASC")
    fun getAllSnippetsFlow(): Flow<List<Snippet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: Snippet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippets(snippets: List<Snippet>)

    @Delete
    suspend fun deleteSnippet(snippet: Snippet)

    @Query("SELECT COUNT(*) FROM command_snippets")
    suspend fun getSnippetCount(): Int

    // Command History
    @Query("SELECT * FROM command_history ORDER BY id DESC LIMIT 50")
    fun getRecentHistoryFlow(): Flow<List<CommandHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: CommandHistoryItem)

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()
}
