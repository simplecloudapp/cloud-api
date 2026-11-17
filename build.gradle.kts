plugins {
    id("cloud-api.base-conventions") apply false
}

val baseVersion = "0.1.0-platform.62"
val commitHash = providers.environmentVariable("COMMIT_HASH").orNull
val isSnapshot = commitHash != null

fun devBuildId(): String {
    val githubRunNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull
    val githubRunAttempt = providers.environmentVariable("GITHUB_RUN_ATTEMPT").orNull

    if (!githubRunNumber.isNullOrBlank()) {
        return listOfNotNull(
            githubRunNumber,
            githubRunAttempt?.takeIf { it.isNotBlank() }
        ).joinToString(".")
    }

    return providers.environmentVariable("BUILD_NUMBER")
        .orElse(providers.environmentVariable("CI_PIPELINE_ID"))
        .getOrElse(System.currentTimeMillis().toString())
}

val buildVersion = if (isSnapshot) "${baseVersion}-dev.${devBuildId()}-${commitHash}" else baseVersion

allprojects {
    group = "app.simplecloud.api"
    version = buildVersion
}
