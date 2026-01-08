package com.deezer.caupain.internal

import okio.buffer
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JVMSinkTest {

    private val baseOutputStream = System.out
    private val testOuputStream = ByteArrayOutputStream()

    @BeforeTest
    fun setup() {
        System.setOut(PrintStream(testOuputStream))
    }

    @AfterTest
    fun teardown() {
        System.setOut(baseOutputStream)
    }

    @Test
    fun testStandardOutputSink() {
        val expected = "test\ntest"
        systemSink().buffer().use { it.writeUtf8(expected) }
        assertEquals(expected, testOuputStream.toString(Charsets.UTF_8))
    }
}