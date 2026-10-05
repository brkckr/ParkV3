package com.brkckr.parkv3.ui.language

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `stored locale tags map to the shipped languages`() {
        assertThat(AppLanguage.fromTags("")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTags("tr")).isEqualTo(AppLanguage.TURKISH)
        assertThat(AppLanguage.fromTags("tr-TR")).isEqualTo(AppLanguage.TURKISH)
        assertThat(AppLanguage.fromTags("TR")).isEqualTo(AppLanguage.TURKISH)
        assertThat(AppLanguage.fromTags("en-US,tr-TR")).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun `a language the app does not ship counts as the system default`() {
        assertThat(AppLanguage.fromTags("de-DE")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromTags(" ")).isEqualTo(AppLanguage.SYSTEM)
    }
}
