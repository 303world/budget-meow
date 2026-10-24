package io.github.world303.budgetmeow.core.store

import io.github.world303.budgetmeow.core.model.Ids
import io.github.world303.budgetmeow.core.model.MeowSettings
import io.github.world303.budgetmeow.core.model.Profile
import io.github.world303.budgetmeow.core.model.TimeUtil
import io.github.world303.budgetmeow.core.model.toDomain
import io.github.world303.budgetmeow.core.model.toDto
import io.github.world303.budgetmeow.core.storage.LocalStorage
import io.github.world303.budgetmeow.core.storage.ProfileIndexDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class SettingsState(
    val loading: Boolean = true,
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
)

/**
 * 配置档（"账号"）与设置。
 *
 * v1 没有服务端：配置档就是本机多份互相隔离的账本，等价于 Web 端的多账号物理隔离。
 * 云端登录在 v2 接入，届时这里多一个 remoteProfileId 字段即可，数据层不用改。
 */
class SettingsStore(private val storage: LocalStorage) {

    private companion object {
        const val MAX_NICKNAME = 10
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var index: ProfileIndexDto = ProfileIndexDto()

    suspend fun bootstrap() = mutex.withLock {
        index = storage.loadIndex()
        if (index.profiles.isEmpty()) {
            val profile = Profile(id = Ids.newId(), nickname = "账本主人", createdAt = TimeUtil.nowIso())
            index = index.copy(profiles = listOf(profile.toDto()), activeProfileId = profile.id)
            storage.saveIndex(index)
        }
        val activeId = index.activeProfileId ?: index.profiles.first().id
        if (activeId != index.activeProfileId) {
            index = index.copy(activeProfileId = activeId)
            storage.saveIndex(index)
        }
        publish()
    }

    private fun publish() {
        val profiles = index.profiles.map { it.toDomain() }
        val active = profiles.firstOrNull { it.id == index.activeProfileId } ?: profiles.firstOrNull()
        _state.value = SettingsState(loading = false, profiles = profiles, activeProfile = active)
    }

    suspend fun settings(): MeowSettings = _state.value.activeProfile?.settings ?: MeowSettings()

    suspend fun createProfile(nickname: String, avatarSource: File? = null) = mutex.withLock {
        val cleanName = nickname.trim().ifBlank { "账本主人" }.take(MAX_NICKNAME)
        val profileId = Ids.newId()
        val avatarPath = avatarSource?.let { storage.copyAvatar(profileId, it) }
        val profile = Profile(
            id = profileId,
            nickname = cleanName,
            avatarPath = avatarPath,
            createdAt = TimeUtil.nowIso(),
        )
        index = index.copy(
            profiles = index.profiles + profile.toDto(),
            activeProfileId = profile.id,
        )
        storage.saveIndex(index)
        publish()
        profile
    }

    suspend fun switchProfile(profileId: String) = mutex.withLock {
        if (index.profiles.none { it.id == profileId }) return@withLock
        index = index.copy(activeProfileId = profileId)
        storage.saveIndex(index)
        publish()
    }

    suspend fun deleteProfile(profileId: String) = mutex.withLock {
        val remaining = index.profiles.filterNot { it.id == profileId }
        if (remaining.isEmpty()) return@withLock
        index = index.copy(
            profiles = remaining,
            activeProfileId = if (index.activeProfileId == profileId) remaining.first().id else index.activeProfileId,
        )
        storage.saveIndex(index)
        storage.deleteProfileData(profileId)
        publish()
    }

    suspend fun updateSettings(transform: (MeowSettings) -> MeowSettings) = mutex.withLock {
        val activeId = index.activeProfileId ?: return@withLock
        index = index.copy(
            profiles = index.profiles.map {
                if (it.id == activeId) it.copy(settings = transform(it.settings.toDomain()).toDto()) else it
            },
        )
        storage.saveIndex(index)
        publish()
    }

    suspend fun updateNickname(nickname: String) = mutex.withLock {
        val activeId = index.activeProfileId ?: return@withLock
        val clean = nickname.trim().take(MAX_NICKNAME).ifBlank { "账本主人" }
        index = index.copy(
            profiles = index.profiles.map { if (it.id == activeId) it.copy(nickname = clean) else it },
        )
        storage.saveIndex(index)
        publish()
    }

    suspend fun updateAvatar(source: File?) = mutex.withLock {
        val activeId = index.activeProfileId ?: return@withLock
        val path = if (source == null) null else storage.copyAvatar(activeId, source)
        index = index.copy(
            profiles = index.profiles.map { if (it.id == activeId) it.copy(avatarPath = path) else it },
        )
        storage.saveIndex(index)
        publish()
    }
}
