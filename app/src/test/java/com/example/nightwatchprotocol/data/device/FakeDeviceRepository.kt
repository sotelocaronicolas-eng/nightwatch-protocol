package com.example.nightwatchprotocol.data.device

class FakeDeviceRepository : DeviceRepository {
    /** uid -> (deviceId -> record). */
    val store = mutableMapOf<String, MutableMap<String, DeviceRecord>>()

    var getFails = false
    var listFails = false
    var saveFails = false
    var setRevokedFails = false
    var deleteFails = false

    val saveCalls = mutableListOf<SaveCall>()
    val deleteCalls = mutableListOf<String>()
    val revokeCalls = mutableListOf<String>()

    data class SaveCall(val deviceId: String, val name: String, val revoked: Boolean?)

    fun put(uid: String, record: DeviceRecord) {
        store.getOrPut(uid) { mutableMapOf() }[record.id] = record
    }

    fun record(uid: String, deviceId: String): DeviceRecord? = store[uid]?.get(deviceId)

    override suspend fun getDevice(uid: String, deviceId: String): DeviceRecord? {
        if (getFails) throw DeviceUnavailableException()
        return record(uid, deviceId)
    }

    override suspend fun listDevices(uid: String): List<DeviceRecord> {
        if (listFails) throw DeviceUnavailableException()
        return store[uid]?.values?.toList().orEmpty()
    }

    override suspend fun saveDevice(uid: String, deviceId: String, name: String, revoked: Boolean?) {
        if (saveFails) throw DeviceUnavailableException()
        saveCalls += SaveCall(deviceId, name, revoked)
        val existing = record(uid, deviceId)
        put(
            uid,
            DeviceRecord(
                id = deviceId,
                name = name,
                lastSeenMillis = 1_000L,
                revoked = revoked ?: existing?.revoked ?: false,
            ),
        )
    }

    override suspend fun setRevoked(uid: String, deviceId: String, revoked: Boolean) {
        if (setRevokedFails) throw DeviceUnavailableException()
        revokeCalls += deviceId
        val existing = record(uid, deviceId) ?: throw DeviceUnavailableException()
        put(uid, existing.copy(revoked = revoked))
    }

    override suspend fun deleteDevice(uid: String, deviceId: String) {
        if (deleteFails) throw DeviceUnavailableException()
        deleteCalls += deviceId
        store[uid]?.remove(deviceId)
    }
}

class FakeDeviceInfoProvider(private val name: String = "Pixel de prueba") : DeviceInfoProvider {
    override fun deviceName(): String = name
}
