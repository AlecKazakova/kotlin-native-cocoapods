package com.example

import kotlin.test.Test
import kotlin.test.assertEquals

class ATest {
  @Test
  fun doATest() {
    assertEquals("sup", SampleClass().someMethod())
  }
}