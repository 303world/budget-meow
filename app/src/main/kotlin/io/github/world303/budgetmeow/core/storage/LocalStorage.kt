package io.github.world303.budgetmeow.core.storage

import io.github.world303.budgetmeow.core.model.BudgetDataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * 本地文件存储。全部是原子写：先写 .tmp，fsync 后再 rename 覆盖，
 * 中途断电最多丢最后一次操作，不会把已有账目写坏。
 */
class LocalStorage(val baseDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    private val writeMutex = Mutex()

    private val indexFile: File get() = File(baseDir, "profiles.json")

    fun budgetFile(profileId: String): File = File(File(baseDir, "data/$profileId"), "budget.json")

    fun settingsFile(profileId: String): File = File(File(baseDir, "data/$profileId"), "settings.json")

    private fun avatarFile(profileId: String, ext: String = "png"): File =
        File(File(baseDir, "avatars/$profileId"), "avatar.$ext")

    fun avatarPath(profileId: String): String? {
        val file = avatarFile(profileId)
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun copyAvatar(profileId: String, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = avatarFile(profileId)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            target.absolutePath
        }.getOrNull()
    }

    // ---------- 壁纸 ----------

    private fun wallpaperFile(): File = File(baseDir, "wallpapers/wallpaper.png")

    fun wallpaperPath(): String? = wallpaperFile().takeIf { it.exists() }?.absolutePath

    suspend fun setWallpaper(source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = wallpaperFile()
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            target.absolutePath
        }.getOrNull()
    }

    suspend fun clearWallpaper() = withContext(Dispatchers.IO) {
        wallpaperFile().delete()
    }

    // ---------- 配置档索引 ----------

    suspend fun loadIndex(): ProfileIndexDto = readJson(indexFile, ProfileIndexDto.serializer(), ProfileIndexDto())

    suspend fun saveIndex(index: ProfileIndexDto) = writeJson(indexFile, ProfileIndexDto.serializer(), index)

    // ---------- 账目 ----------

    suspend fun loadBudget(profileId: String): BudgetDataset {
        val dto = readJson(budgetFile(profileId), BudgetFileDto.serializer(), BudgetFileDto())
        return dto.toDomain()
    }

    suspend fun saveBudget(profileId: String, dataset: BudgetDataset) {
        writeJson(budgetFile(profileId), BudgetFileDto.serializer(), dataset.toDto())
    }

    // ---------- 设置 ----------

    suspend fun loadSettings(profileId: String): SettingsDto =
        readJson(settingsFile(profileId), SettingsDto.serializer(), SettingsDto())

    suspend fun saveSettings(profileId: String, settings: SettingsDto) {
        writeJson(settingsFile(profileId), SettingsDto.serializer(), settings)
    }

    // ---------- 其他 ----------

    suspend fun deleteProfileData(profileId: String) = withContext(Dispatchers.IO) {
        File(baseDir, "data/$profileId").deleteRecursively()
        File(baseDir, "avatars/$profileId").deleteRecursively()
    }

    /** 导出备份时临时落盘用 */
    fun exportDir(): File = File(baseDir, "exports").apply { mkdirs() }

    fun importDir(): File = File(baseDir, "imports").apply { mkdirs() }

    private suspend fun <T> readJson(file: File, serializer: KSerializer<T>, fallback: T): T =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext fallback
            runCatching { json.decodeFromString(serializer, file.readText()) }.getOrElse { fallback }
        }

    private suspend fun <T> writeJson(file: File, serializer: KSerializer<T>, value: T) {
        val text = json.encodeToString(serializer, value)
        writeTextAtomic(file, text)
    }

    suspend fun writeTextAtomic(file: File, text: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                // 少数文件系统不支持覆盖式 rename，退化为先删再改
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
