package ca.ilianokokoro.umihi.music.core.helpers

/**
 * Reecriture des URL de pochette Google vers une taille adaptee au lecteur.
 *
 * Les lignes de liste (recherche, playlist, album) ne portent qu'une miniature de 60 a 120 px ;
 * affichee plein ecran dans le lecteur, elle est floue. Or les URL `googleusercontent`/`ggpht`
 * encodent la taille dans leur suffixe (`=w120-h120-l90-rj` ou `=s120`) et le serveur sert
 * n'importe quelle taille demandee : il suffit de reecrire le suffixe, sans appel reseau
 * supplementaire.
 *
 * Les autres hotes (i.ytimg.com, fichiers locaux, content://) sont laisses intacts : leurs
 * variantes de taille sont des chemins fixes dont la plus grande peut ne pas exister (404).
 */
object ArtworkUrl {
    /** Cote demande : suffisant pour la pochette plein ecran, sans gonfler le cache disque. */
    const val PLAYER_SIZE = 544

    private val RESIZABLE_HOSTS = listOf("googleusercontent.com", "ggpht.com")
    private val WIDTH_HEIGHT_PARAM = Regex("=w(\\d+)-h(\\d+)")
    private val SQUARE_PARAM = Regex("=s(\\d+)")

    /**
     * L'URL reecrite pour demander au moins [targetSize] px de cote, ou l'URL inchangee quand
     * l'hote n'est pas redimensionnable, que la taille est deja suffisante, ou qu'aucun parametre
     * de taille n'est reconnu. Jamais de reduction.
     */
    fun upgrade(url: String, targetSize: Int = PLAYER_SIZE): String {
        if (RESIZABLE_HOSTS.none { url.contains(it) }) {
            return url
        }

        WIDTH_HEIGHT_PARAM.find(url)?.let { match ->
            val width = match.groupValues[1].toIntOrNull() ?: return url
            if (width >= targetSize) return url
            return url.replaceRange(match.range, "=w$targetSize-h$targetSize")
        }

        SQUARE_PARAM.find(url)?.let { match ->
            val size = match.groupValues[1].toIntOrNull() ?: return url
            if (size >= targetSize) return url
            return url.replaceRange(match.range, "=s$targetSize")
        }

        return url
    }

    /**
     * Rewrites a Google-hosted thumbnail to the exact requested square size.
     *
     * Unlike [upgrade], this is intentionally allowed to reduce the source size. It is only for
     * bounded list thumbnails, where downloading the full-player artwork wastes bandwidth and
     * decode time. Local files and hosts with fixed thumbnail paths remain untouched.
     */
    fun thumbnail(url: String, targetSize: Int): String {
        if (targetSize <= 0 || RESIZABLE_HOSTS.none { url.contains(it) }) {
            return url
        }

        WIDTH_HEIGHT_PARAM.find(url)?.let { match ->
            return url.replaceRange(match.range, "=w$targetSize-h$targetSize")
        }

        SQUARE_PARAM.find(url)?.let { match ->
            return url.replaceRange(match.range, "=s$targetSize")
        }

        return url
    }
}
