package com.wagdytech.livesports

object M3UParser {

    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(content: String, isPro: Boolean = false): List<Channel> {
        val channels = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var country: String? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1] to it.groupValues[2] }
                    val title = line.substringAfterLast(',', "").trim()
                    name = title.ifEmpty { attrs["tvg-name"] ?: "قناة" }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "عام"
                    val tvgId = attrs["tvg-id"]
                    country = tvgId?.substringAfterLast('.')?.substringBefore('@')
                        ?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
                }
                line.isNotEmpty() && !line.startsWith("#") && name != null -> {
                    channels.add(
                        Channel(
                            name = name ?: "قناة",
                            url = line,
                            logo = logo,
                            group = group ?: "عام",
                            country = country,
                            isPro = isPro
                        )
                    )
                    name = null; logo = null; group = null; country = null
                }
            }
        }
        return channels.distinctBy { it.url }
    }
}
