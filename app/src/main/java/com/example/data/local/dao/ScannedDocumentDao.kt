package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.ScannedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedDocumentDao {

    @Query("SELECT * FROM scanned_documents ORDER BY createdAtTimestamp DESC")
    fun getAllDocuments(): Flow<List<ScannedDocumentEntity>>

    @Query(
        """
        SELECT * FROM scanned_documents 
        WHERE title LIKE '%' || :query || '%' 
           OR originalFileName LIKE '%' || :query || '%'
           OR ocrExtractedText LIKE '%' || :query || '%'
        ORDER BY createdAtTimestamp DESC
        """
    )
    fun searchDocuments(query: String): Flow<List<ScannedDocumentEntity>>

    @Query("SELECT * FROM scanned_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): ScannedDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: ScannedDocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: ScannedDocumentEntity)

    @Delete
    suspend fun deleteDocument(doc: ScannedDocumentEntity)

    @Query("DELETE FROM scanned_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("SELECT COUNT(*) FROM scanned_documents")
    fun getDocumentCount(): Flow<Int>
}
