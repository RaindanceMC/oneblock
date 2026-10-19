# RaindanceMC/oneblock

- **`oneblock-module`** — Rain's Flexible Modules — OneBlock: OneBlock islands on their own server of the network, each its own world with a nether and an end, unloaded while nobody plays; /island on every server: going there, and team and guest invites wherever players are.
- **`oneblock-standalone`** — Rain's OneBlock: Rain's OneBlock, as a plugin of its own. Nothing here but a build file and a descriptor: the oneblock module and the standard wrapper are shaded together, and that is the whole plugin.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `oneblock-standalone/target/RainsOneBlock-0.5.1.jar` — also published as `de.raindancer.modules:oneblock-standalone:0.5.1`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
