package com.swarai.studio.engine

import java.io.File

/**
 * On-phone generation is NOT available. ACE-Step 1.5's smallest configuration (2B DiT, LM disabled) still needs
 * several GB of GPU memory and a CUDA/ROCm/MPS/XPU or desktop CPU runtime; there is no Android (NNAPI/Vulkan/
 * LiteRT) build. This engine exists so the app can say so honestly instead of hiding the option.
 */
class OnDeviceEngine : MusicEngine {
    override val id = ID
    override val resumable = false

    override suspend fun describe() = EngineInfo(
        id = ID,
        name = "On-device generation",
        kind = EngineKind.ON_DEVICE,
        model = "None available",
        sendsDataTo = "Nothing would leave the phone",
        available = false,
        unavailableReason = "No music model that sings full songs can currently run on Android phones. " +
            "Use \"Your computer\" for offline generation over Wi-Fi, or an online engine.",
    )

    override suspend fun testConnection(): String = throw EngineException(describe().unavailableReason!!)

    override suspend fun generate(
        spec: GenerationSpec,
        outputDir: File,
        resumeToken: String?,
        callbacks: GenerationCallbacks,
    ): List<GeneratedAudio> = throw EngineException(describe().unavailableReason!!)

    companion object {
        const val ID = "on_device"
    }
}
