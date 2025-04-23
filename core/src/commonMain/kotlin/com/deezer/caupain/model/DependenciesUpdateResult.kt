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