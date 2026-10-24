package io.github.thomasvanriel.winnable.engine

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EngineInfoTest {
    @Test
    fun versionIsSet() {
        assertTrue(EngineInfo.VERSION.isNotBlank())
    }
}
