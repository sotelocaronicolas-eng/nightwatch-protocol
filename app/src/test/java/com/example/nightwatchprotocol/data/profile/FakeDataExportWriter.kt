package com.example.nightwatchprotocol.data.profile

class FakeDataExportWriter : DataExportWriter {
    var result: Result<Unit> = Result.success(Unit)
    var writes = mutableListOf<Pair<String, String>>()

    override suspend fun write(destination: String, content: String) {
        writes += destination to content
        result.getOrThrow()
    }
}
