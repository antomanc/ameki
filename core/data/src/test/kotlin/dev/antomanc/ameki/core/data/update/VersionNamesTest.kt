package dev.antomanc.ameki.core.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionNamesTest {
    @Test
    fun `strips a leading v`() {
        assertThat(VersionNames.normalize("v0.2.1")).isEqualTo("0.2.1")
        assertThat(VersionNames.normalize("V0.2.1")).isEqualTo("0.2.1")
        assertThat(VersionNames.normalize("0.2.1")).isEqualTo("0.2.1")
    }

    @Test
    fun `compares semver-ish tags used by this project`() {
        assertThat(VersionNames.isNewer("v0.2.2", "0.2.1")).isTrue()
        assertThat(VersionNames.isNewer("0.2.1", "0.2.1")).isFalse()
        assertThat(VersionNames.isNewer("0.2.0", "0.2.1")).isFalse()
        assertThat(VersionNames.isNewer("0.3.0", "0.2.9")).isTrue()
        assertThat(VersionNames.isNewer("1.0.0", "0.9.9")).isTrue()
        assertThat(VersionNames.compare("0.2.10", "0.2.9")).isGreaterThan(0)
    }
}
