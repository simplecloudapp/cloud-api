# Cloud API

![Banner][banner]

<div align="center">

[![Modrinth][badge-modrinth]][modrinth]
[![License][badge-license]][license]
<br>

[![Discord][badge-discord]][social-discord]
[![Follow @simplecloudapp][badge-x]][social-x]
[![Follow @simplecloudapp][badge-bluesky]][social-bluesky]
[![Follow @simplecloudapp][badge-youtube]][social-youtube]
<br>

[Report a Bug][issue-bug-report]
·
[Request a Feature][issue-feature-request]
<br>

🌟 Give us a star — your support means the world to us!
</div>
<br>

> All information about this project can be found in our detailed [documentation][docs-thisproject].

The official SDK for the SimpleCloud controller. Manage groups, servers, persistent servers and players and listen to events.

## Features

- [x] **Easy API Use**: Just add the plugin to your server and include the api as a provided artifact.
- [x] **Supported Server Software**: Supports every major platform.
- [x] **Easy API access**: Use every api method we provide, just from one interface.
- [x] **Go SDK**: A standalone Go SDK for use outside of Minecraft servers.

## Server Installation

- Put the Spigot, Paper, Folia, BungeeCord, and Velocity JAR in the server's `plugins` directory.
- Put the Fabric/Quilt or NeoForge JAR in the server's `mods` directory.
- Fabric, Quilt, and NeoForge support is server-side only, targets Minecraft 26.2 to 26.3, and does not require clients to install the mod.

## Usage

### Java & Kotlin

> For always up-to-date artifacts visit [dev artifacts][dev-artifacts] or [artifacts][artifacts].

> Note: If you want to use the dev version, you have to use the [snapshot repository][snapshots].

#### Gradle Kotlin

```kt
compileOnly("app.simplecloud.api:api:VERSION")
```

#### Gradle Groovy

```groovy
compileOnly 'app.simplecloud.api:api:VERSION'
```

#### Maven

```xml
<dependencies>
    <dependency>
        <groupId>app.simplecloud.api</groupId>
        <artifactId>api</artifactId>
        <version>VERSION</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Go

The standalone Go SDK lives in [`go/`](go/README.md). See the [Go SDK README](go/README.md) for installation and usage.

## Contributing

Contributions to SimpleCloud are welcome and highly appreciated. However, before you jump right into it, we would like
you to read our [Contribution Guide][docs-contribute].

## License

This repository is licensed under [Apache 2.0][license].


<!-- LINK GROUP -->

<!-- ✅ PLEASE EDIT -->

[banner]: https://raw.githubusercontent.com/simplecloudapp/branding/main/readme/banner/cloud-api.png

[issue-bug-report]: https://github.com/simplecloudapp/cloud-api/issues/new?labels=bug&projects=template=01_BUG-REPORT.yml&title=%5BBUG%5D+%3Ctitle%3E

[issue-feature-request]: https://github.com/simplecloudapp/cloud-api/discussions/new?category=ideas

[docs-thisproject]: https://simplecloud.app/docs/en/developer/overview

[docs-contribute]: https://simplecloud.app/docs/contribute

[modrinth]: https://modrinth.com/organization/simplecloud

[dev]: https://repo.simplecloud.app/#/snapshots/app/simplecloud/cloud-api


[artifacts]: https://repo.simplecloud.app/#/snapshots/app/simplecloud/api/cloud-api

[dev-artifacts]: https://repo.simplecloud.app/#/snapshots/app/simplecloud/cloud-api

[badge-maven-central]: https://img.shields.io/maven-central/v/app.simplecloud.api/cloud-api?labelColor=18181b&style=flat-square&color=65a30d&label=Release

[badge-dev]: https://repo.simplecloud.app/api/badge/latest/snapshots/app/simplecloud/api/cloud-api?name=Dev&style=flat-square&color=0ea5e9

<!-- ⛔ DON'T TOUCH -->

[license]: https://opensource.org/licenses/Apache-2.0

[snapshots]: https://repo.simplecloud.app/#/snapshots

[social-x]: https://x.com/simplecloudapp

[social-bluesky]: https://bsky.app/profile/simplecloud.app

[social-youtube]: https://www.youtube.com/@thesimplecloud9075

[social-discord]: https://discord.simplecloud.app

[badge-modrinth]: https://img.shields.io/badge/modrinth-18181b.svg?style=flat-square&logo=modrinth

[badge-license]: https://img.shields.io/badge/apache%202.0-blue.svg?style=flat-square&label=license&labelColor=18181b&style=flat-square&color=e11d48

[badge-discord]: https://img.shields.io/badge/Community_Discord-d95652.svg?style=flat-square&logo=discord&color=27272a

[badge-x]: https://img.shields.io/badge/Follow_@simplecloudapp-d95652.svg?style=flat-square&logo=x&color=27272a

[badge-bluesky]: https://img.shields.io/badge/Follow_@simplecloud.app-d95652.svg?style=flat-square&logo=bluesky&color=27272a

[badge-youtube]: https://img.shields.io/badge/youtube-d95652.svg?style=flat-square&logo=youtube&color=27272a
