package edu.sharif.sqrlab.more_coverage.models


data class TestCase(
    val name: String,
    val description: String,
    val expectedLines: Set<Int>,
    val body: String
)