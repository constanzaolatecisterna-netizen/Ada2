package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.FamilyProfile
import com.example.ui.theme.AdaColors

@Composable
fun CalmaRedTabContent(
    activeProfile: FamilyProfile,
    profileCount: Int,
    networkCode: String,
    userEmail: String?,
    calmSeconds: Int,
    calmRunning: Boolean,
    weatherDetail: String,
    onStartCalm: () -> Unit,
    onResetCalm: () -> Unit,
    onRefreshWeather: () -> Unit,
    onChangeNetworkCode: (String) -> Unit,
    onDeleteActiveProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    var netInput by remember(networkCode) { mutableStateOf(networkCode) }
    val mins = (calmSeconds / 60).toString().padStart(2, '0')
    val secs = (calmSeconds % 60).toString().padStart(2, '0')

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Calm Breathing Timer
        Surface(
            color = AdaColors.Background.copy(alpha = 0.72f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AdaColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Espacio de calma y respiración",
                        style = MaterialTheme.typography.titleSmall,
                        color = AdaColors.EmeraldCalm,
                    )
                    Text(
                        text = "$mins:$secs",
                        style = MaterialTheme.typography.displaySmall,
                        color = AdaColors.TextPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "Inhala 4s · Mantén 4s · Exhala 6s",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdaColors.TextMuted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStartCalm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdaColors.EmeraldCalm,
                            contentColor = Color(0xFF031A12),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("start_calm_button"),
                    ) {
                        Text(if (calmRunning) "En curso" else "Iniciar", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onResetCalm,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Reiniciar")
                    }
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Real-time Weather
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Clima en tiempo real",
                    style = MaterialTheme.typography.titleSmall,
                    color = AdaColors.TextPrimary,
                )
                TextButton(
                    onClick = onRefreshWeather,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("refresh_weather_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = AdaColors.SkyPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Actualizar", color = AdaColors.SkyPrimary)
                }
            }
            Text(
                text = weatherDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = AdaColors.TextSecondary,
            )
        }

        HorizontalDivider(color = AdaColors.Border)

        // Family Network Code
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Código de Red Alianza",
                style = MaterialTheme.typography.titleSmall,
                color = AdaColors.TextPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = netInput,
                    onValueChange = { netInput = it.uppercase() },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (netInput.isNotBlank()) onChangeNetworkCode(netInput.trim())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdaColors.SkyPrimary,
                        contentColor = Color(0xFF04111F),
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Conectar", fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = AdaColors.Border)

        // Google Account Sign Out
        Surface(
            color = AdaColors.Background.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AdaColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cuenta Google sincronizada",
                        style = MaterialTheme.typography.labelLarge,
                        color = AdaColors.TextPrimary,
                    )
                    Text(
                        text = userEmail ?: "Sesión activa",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdaColors.TextMuted,
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sign_out_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = AdaColors.RoseSoft,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salir", color = AdaColors.RoseSoft)
                }
            }
        }

        if (profileCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Eliminar perfil activo (${activeProfile.name})",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdaColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onDeleteActiveProfile,
                    border = BorderStroke(1.dp, AdaColors.RoseAlert.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Eliminar perfil", color = AdaColors.RoseSoft)
                }
            }
        }
    }
}
