package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.BondedPhone
import net.agolyakov.firelamp.protocol.BondedPhones
import net.agolyakov.firelamp.ui.component.SettingsSection

/**
 * The phones the lamp remembers (characteristic 0x0016, firmware >= 0.11.0): the name the phone
 * sent (its Bluetooth name), the MAC and "this phone".
 */
@Composable
fun BondedPhonesSection(phones: BondedPhones) {
    SettingsSection(stringResource(R.string.bonded_phones)) {
        Text(
            stringResource(R.string.bonded_phones_count, phones.phones.size, phones.capacity),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        phones.phones.forEachIndexed { index, phone ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
            BondedPhoneRow(phone)
        }
        Text(
            stringResource(R.string.bonded_phones_hint, phones.capacity),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun BondedPhoneRow(phone: BondedPhone) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(phone.name.ifBlank { phone.address }, style = MaterialTheme.typography.bodyLarge)
            if (phone.name.isNotBlank()) {
                Text(
                    phone.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (phone.isCurrent) {
            Text(
                stringResource(R.string.bonded_phones_this),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
