/*
 * MIT License
 *
 * Copyright (c) 2025 Deezer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.deezer.caupain.model.gradle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the stability level of a Gradle version.
 *
 * @property key The name of the stability level in command line options and other user-facing
 * inputs
 */
@Serializable
public enum class GradleStabilityLevel(
    internal val urlSuffix: String,
    public val key: String,
) {
    @SerialName("stable")
    STABLE("current", "stable"),

    @SerialName("rc")
    RC("release-candidate", "rc"),

    @SerialName("milestone")
    MILESTONE("milestone", "milestone"),

    @SerialName("releaseNightly")
    RELEASE_NIGHTLY("release-nightly", "release-nightly"),

    @SerialName("nightly")
    NIGHTLY("nightly", "nightly");

    public companion object {
        /**
         * Returns the stability level with the given [key] (ignoring case), or null if there is
         * none.
         */
        public fun fromKey(key: String): GradleStabilityLevel? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}
