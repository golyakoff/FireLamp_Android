package net.agolyakov.firelamp.domain.usecase

import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.domain.repository.PreferencesRepository
import javax.inject.Inject

class LoadDeviceWithNameUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(device: FireLampDevice): FireLampDevice {
        val friendlyName = preferencesRepository.getFriendlyName(device.macAddress)
        return device.copy(friendlyName = friendlyName)
    }
}