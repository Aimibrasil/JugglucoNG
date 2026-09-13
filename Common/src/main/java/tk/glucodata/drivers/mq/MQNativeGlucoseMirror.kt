package tk.glucodata.drivers.mq

internal object MQNativeGlucoseMirror {
    fun write(
        sampleMs: Long,
        result: MQAlgorithm.Result,
        sensorId: String,
        store: (Long, Float, String) -> Boolean,
    ): Boolean {
        if (sampleMs < 1_000L || sensorId.isBlank() || !result.mgdl.isFinite() || result.mgdl <= 0f) {
            return false
        }
        // addGlucoseStream expects mg/dL; C++ converts to its internal mg/dL-times-ten storage.
        return store(sampleMs / 1_000L, result.mgdl, sensorId)
    }
}
