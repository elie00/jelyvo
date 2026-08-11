package ca.ilianokokoro.umihi.music.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.desktop.account.DesktopYoutubeSession

@Composable
fun AccountScreen(
    session: DesktopYoutubeSession?,
    checkResult: String?,
    busy: Boolean,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    onCheck: () -> Unit,
) {
    var pasted by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Compte YouTube", style = MaterialTheme.typography.headlineMedium)

        if (session == null) {
            Text(
                text = "Le compte porte vos titres likés, vos albums enregistrés, vos artistes " +
                    "suivis et vos playlists. Connecter ce Mac au même compte que votre téléphone " +
                    "les rend identiques des deux côtés.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Compose Desktop n'a pas de navigateur intégré, il n'y a donc pas d'écran " +
                    "de connexion Google ici. Connectez-vous à music.youtube.com dans votre " +
                    "navigateur, ouvrez les outils de développement, copiez l'en-tête Cookie " +
                    "complet d'une requête, et collez-le ci-dessous. Il est enregistré dans le " +
                    "Trousseau macOS, jamais en clair sur le disque.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("En-tête Cookie") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSignIn(pasted) }, enabled = !busy && pasted.isNotBlank()) {
                Text("Connecter")
            }
        } else {
            Text("Connecté · ${session.summary}", style = MaterialTheme.typography.bodyLarge)
            if (!session.looksAuthenticated) {
                Text(
                    text = "Aucun cookie SAPISID : YouTube traitera ces requêtes comme anonymes. " +
                        "L'en-tête copié est probablement incomplet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCheck, enabled = !busy) { Text("Tester la connexion") }
                OutlinedButton(onClick = onSignOut, enabled = !busy) { Text("Se déconnecter") }
            }
        }

        checkResult?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
