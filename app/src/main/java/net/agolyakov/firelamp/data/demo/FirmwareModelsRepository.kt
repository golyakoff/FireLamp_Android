package net.agolyakov.firelamp.data.demo

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import net.agolyakov.firelamp.protocol.Chip
import net.agolyakov.firelamp.protocol.Controls
import net.agolyakov.firelamp.protocol.DeviceInfo
import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.protocol.EffectParam
import net.agolyakov.firelamp.protocol.ParamType
import javax.inject.Inject
import javax.inject.Singleton

/** One bundled firmware snapshot, for the developer demo of the lamp's screens. */
data class DemoFirmware(
    val info: DeviceInfo,
    val effects: List<EffectInfo>,
)

@Singleton
class FirmwareModelsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Model(val version: String, val effectsCount: Int)

    /** The snapshots in `assets/demo`. Add a file and a row here when a firmware is released. */
    val models: List<Model> = listOf(Model(version = "0.11.1", effectsCount = 45))

    fun load(version: String): DemoFirmware {
        val asset = "demo/firmware-$version.json"
        val json = context.assets.open(asset).bufferedReader().use { it.readText() }
        return parse(json)
    }

    companion object {
        /** Parses a bundled snapshot; kept separate from the asset so a JVM test can check it. */
        internal fun parse(json: String): DemoFirmware {
        val dto = Gson().fromJson(json, JsonFirmware::class.java)
            ?: throw IllegalArgumentException("Empty demo firmware")
        val info = DeviceInfo(
            protocolVersion = dto.protocol,
            chip = Chip.entries.firstOrNull { it.code == dto.chip } ?: Chip.UNKNOWN,
            flashSizeMb = dto.flashMb,
            features = dto.features,
            matrixWidth = dto.matrixWidth,
            matrixHeight = dto.matrixHeight,
            effectCount = dto.effects.size,
            controls = Controls.entries.firstOrNull { it.code == dto.controls } ?: Controls.UNKNOWN,
            firmwareVersion = dto.version,
            buildDate = dto.buildDate,
            buildVariant = dto.buildVariant,
            previousFirmware = "",
        )
        val effects = dto.effects.map { e ->
            EffectInfo(
                position = e.position,
                count = dto.effects.size,
                code = e.code,
                name = e.name,
                inDemo = e.inDemo,
                params = e.params.map { p ->
                    EffectParam(
                        code = p.code,
                        name = p.name,
                        type = ParamType.entries.firstOrNull { it.code == p.type } ?: ParamType.RANGE,
                        min = p.min,
                        max = p.max,
                        default = p.default,
                        value = p.value,
                    )
                },
            )
        }
        return DemoFirmware(info, effects)
        }
    }

    private data class JsonFirmware(
        val version: String,
        val buildDate: String,
        val buildVariant: String,
        val protocol: Int,
        val chip: Int,
        val flashMb: Int,
        val features: Int,
        val matrixWidth: Int,
        val matrixHeight: Int,
        val controls: Int,
        val effects: List<JsonEffect>,
    )

    private data class JsonEffect(
        val position: Int,
        val code: String,
        val name: String,
        val inDemo: Boolean,
        val params: List<JsonParam>,
    )

    private data class JsonParam(
        val code: String,
        val name: String,
        val type: Int,
        val min: Int,
        val max: Int,
        val default: Int,
        val value: Int,
    )
}
