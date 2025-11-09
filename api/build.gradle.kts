plugins {
    id("java")
    `maven-publish`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
