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

package com.deezer.caupain.model

/**
 * This is the result of the dependency update process.
 *
 * @property gradleUpdateInfo Information about the Gradle update.
 * @property updateInfos Informations about the dependencies updates.
 */
public class DependenciesUpdateResult(
    public val gradleUpdateInfo: GradleUpdateInfo?,
    public val updateInfos: Map<UpdateInfo.Type, List<UpdateInfo>>
) {
    /**
     * Returns true if there are no updates available.
     */
    public fun isEmpty(): Boolean = gradleUpdateInfo == null
            && (updateInfos.isEmpty() || updateInfos.all { it.value.isEmpty() })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DependenciesUpdateResult

        if (gradleUpdateInfo != other.gradleUpdateInfo) return false
        if (updateInfos != other.updateInfos) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gradleUpdateInfo?.hashCode() ?: 0
        result = 31 * result + updateInfos.hashCode()
        return result
    }

    override fun toString(): String {
        return "DependencyUpdateResult(gradleUpdateInfo=$gradleUpdateInfo, updateInfos=$updateInfos)"
    }
}