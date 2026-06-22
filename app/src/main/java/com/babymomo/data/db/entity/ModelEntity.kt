package com.babymomo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ModelRuntime { LLAMA_CPP, MEDIAPIPE_GENAI, MLC_LLM, ONNX_RUNTIME, REMOTE_OPENAI_COMPAT, MOCK }
enum class ModelStatus { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR, OBSOLETE }

@Entity(tableName = "models", indices = [Index("runtime"), Index("isActive"), Index("status")])
data class ModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val runtime: ModelRuntime,
    val huggingfaceRepo: String,
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val quantization: String = "Q4_K_M",
    val contextLength: Int = 4096,
    val minRamMb: Int = 4096,
    val license: String = "",
    val description: String = "",
    val localPath: String? = null,
    val status: ModelStatus = ModelStatus.NOT_DOWNLOADED,
    val isActive: Boolean = false,
    val downloadedAt: Long? = null,
    val lastUsedAt: Long? = null,
    val md5: String = ""
)
