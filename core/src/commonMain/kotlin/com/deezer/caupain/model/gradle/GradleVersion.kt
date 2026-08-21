/*
 * MIT License
 *
 * Copyright (c) 2026 Deezer
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
 *
 */

package com.deezer.caupain.model.gradle

import dev.drewhamilton.poko.Poko
import kotlinx.serialization.Serializable

/**
 * Gradle version
 */
@Poko
@Serializable
public class GradleVersion(
    public val version: String,
    public val isSnapshot: Boolean = isGradleSnapshotVersion(version)
) {
    private companion object {
        val GRADLE_VERSION_PATTERN =
            Regex("((\\d+)(\\.\\d+)+)(-(\\p{Alpha}+)-(\\w+))?(-(SNAPSHOT|\\d{14}([-+]\\d{4})?))?")

        fun isGradleSnapshotVersion(version: String): Boolean {
            val matchResult = GRADLE_VERSION_PATTERN.matchEntire(version)
            return when {
                matchResult == null -> false

                matchResult
                    .groups[9]
                    ?.value
                    ?.let { it == "snapshot" || it == "commit" } == true -> true

                matchResult.groups[8] != null -> true

                else -> false
            }
        }
    }
}
