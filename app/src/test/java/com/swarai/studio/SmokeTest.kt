package com.swarai.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun devanagariRoundTrips() = assertEquals("गीत", String("गीत".toByteArray(Charsets.UTF_8), Charsets.UTF_8))
}
