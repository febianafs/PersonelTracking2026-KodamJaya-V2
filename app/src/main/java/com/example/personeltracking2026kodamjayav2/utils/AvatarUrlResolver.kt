package com.example.personeltracking2026kodamjayav2.utils

object AvatarUrlResolver {
    private const val IMAGE_BASE_URL = "https://siskojaya.com/images/"

    fun resolve(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("http://") || value.startsWith("https://")) {
            if (value.startsWith(IMAGE_BASE_URL, ignoreCase = true)) return value

            val imagePath = value.substringAfter("/images/", missingDelimiterValue = "")
            return imagePath.takeIf { it.isNotBlank() }?.let { IMAGE_BASE_URL + it }
        }

        val relativePath = value.trimStart('/').removePrefix("images/")
        return IMAGE_BASE_URL + relativePath
    }
}
