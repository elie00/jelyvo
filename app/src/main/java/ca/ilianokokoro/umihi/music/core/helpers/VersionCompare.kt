package ca.ilianokokoro.umihi.music.core.helpers

import ca.ilianokokoro.umihi.music.core.Constants

/**
 * Comparaison de versions `x.y.z` pure JVM, testable sans Android. Un segment non numerique
 * compte pour 0 au lieu de faire tomber la comparaison (meme regle que lengthSeconds : jamais
 * de parse String->nombre non garde sous core/).
 */
object VersionCompare {

    /**
     * Vrai si [incoming] (la release publiee) est plus recente que [current] (la version
     * installee). Une build beta locale (`2.0.0-beta`) est consideree plus ancienne que la
     * release stable de memes chiffres (`2.0.0`).
     */
    fun isNewer(incoming: String, current: String): Boolean {
        val isCurrentBeta = current.endsWith(Constants.BETA_SUFFIX)
        val incomingParts = incoming.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.removeSuffix(Constants.BETA_SUFFIX)
            .split(".")
            .map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(incomingParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val incomingPart = incomingParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            when {
                incomingPart > currentPart -> return true
                incomingPart < currentPart -> return false
            }
        }

        return isCurrentBeta
    }
}
