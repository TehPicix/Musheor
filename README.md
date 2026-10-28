# Musheor 1.7.1 — reconstructed source

Buildable source reconstructed from `musheor-1.7.1 1.21.11.jar`, a Meteor Client addon for
Minecraft 1.21.11 by musheck. The jar was only decompiled and analyzed; it was never run.

| | |
|---|---|
| Input jar | `musheor-1.7.1 1.21.11.jar`, 1,399,798 bytes, SHA-256 `5C76EAD1FCCC644CD4B79D50837882CF7240BC1AC4A74127296692BB90EB6CC8` |
| Built with (per its manifest) | Gradle 9.3.1, Loom 1.15.5, Fabric Loader 0.16.9, static mixin remapping, intermediary output |
| This project | Gradle 9.7.1 wrapper, Loom 1.17.21, Yarn `1.21.11+build.3`, JDK 21 toolchain |
| Result | `gradlew build` succeeds; 243/243 classes rebuilt and checked against the original bytecode |

## Building

```bash
./gradlew build
```

Output: `build/libs/musheor-1.7.1.jar` (intermediary namespace, Java-WebSocket 1.5.4 nested as in the original).

- Every dependency comes from public Maven repositories: Meteor and Baritone `1.21.11-SNAPSHOT`
  from maven.meteordev.org, and Xaero's Minimap/World Map, XaeroPlus, Litematica and malilib from
  Modrinth's Maven (versions in `gradle.properties`). The first build needs network access; after
  that `--offline` works from the Gradle cache.
- Loom is 1.17 rather than the original's 1.15.5 because Litematica, malilib and XaeroPlus were
  built with Loom 1.16/1.17, which older Loom refuses to remap. Litematica must stay ≤ 0.26.12:
  the shipped code calls `FileType.fromFile(java.io.File)`, which 0.26.16 removed.

### GitHub Actions

`.github/workflows/release.yml` builds on every push to any branch and publishes the jar as a
GitHub release tagged `v<mod_version>-build.<run number>`. Pushes to branches other than the
default one are marked pre-release, and re-running a workflow run replaces its jar.

## What is original and what is reconstructed

Everything that determines behavior comes straight from the bytecode: control flow, string
literals, constants, Meteor setting names and descriptions, mixin targets, and local variable and
parameter names (the obfuscator left those intact).

The obfuscator renamed 2,660 identifiers to random dictionary strings (`fStlZIvY9lS8`, `gVOhtBej1x`, ...):

| Identifiers | Count | How the name was obtained |
|---|---|---|
| Enum constants, record components | 233 | **Exact originals** recovered from bytecode: the name strings passed to `Enum.<init>` in each enum's static initializer, and the `"a;b;c"` names in each record's `ObjectMethods` bootstrap |
| Compiler-generated members | 400 | Standard javac names (`$VALUES`, `$values`, `$assertionsDisabled`, `this$0`, ...); lambdas are inlined by the decompiler |
| Overrides | 18 | Same name as the interface/base method they implement |
| Classes, fields, methods | 2,009 (39 classes) | **Inferred** by reading what the code does. Meteor settings are named from their `.name("...")` string |

The obfuscator also moved some classes into a package called `obf`; those were placed under
`musheor/utils/{highway,mapart,placement,printer,network,baritone}`. See [CLASSES.md](CLASSES.md)
for a one-line description of every class, and `reconstruction/class-renames.tsv` for old → new names.

The obfuscator stripped the `Record` attributes; they were rebuilt from the `ObjectMethods`
bootstrap so the 16 records decompile as real records.

### Hand-written or adjusted code

Applied by `reconstruction/tools/postfix.ps1` after decompiling (`reconstruction/postfix.log` lists every edit):

- `HighwayStatsUtils.getDistanceToNextSection(int)`: Vineflower 1.11.1 crashes on this method's
  enum pattern switch. It was rebuilt by hand from the bytecode (the `enumSwitch` bootstrap labels,
  the `tableswitch` arms, and the null case falling into default).
- `ReKit`: local record `1MoveOp` (javac's binary name) → `MoveOp`.
- Mixins: `(Target)(Object)this` casts that the decompiler dropped the `(Object)` from.
- `EnumSetting.Builder<T>` chains: Vineflower's raw casts unwrapped and the type argument restored.
- `KekNuker.sortTargets`: a trailing `case null:` → `case null, default:` (the bytecode routes
  null and unmatched values to the same exit).
- `Printer.invokeGetPlacementState`: restored the labeled `break` the original uses instead of the
  decompiler's duplicated `return`.

## Verification

`reconstruction/tools/Verify.java` compares the recompiled classes with the original bytecode
after both are given the same names (`reconstruction/verify-*.txt`):

- **Classes and members:** 243/243 classes are present, and no field or method is missing.
- **Methods:** 1,901 of 1,916 have the same multiset of calls, field accesses, constants and type
  operations, with lambda bodies folded in.
- **The 15 that differ** are compiler/decompiler shape, not behavior:
  - Switch-map field layout, including one unused `HighwayDirection` map left in the original by the obfuscator's shrinker.
  - Fastutil vs `java.util` iterator interface on the same objects.
  - try-with-resources `close()` emitted per exit; still called exactly once per path.
  - Synthetic accessors.
  - `EnumSetting.set` vs `Setting.set`, which resolve to the same method.
- **Extra members:** 18 that the obfuscator had stripped and javac regenerates (record accessors and empty constructors).
- **Mixins:** every mixin injects into the same intermediary target as the original. Loom 1.17
  writes five targets with their descriptor (`method_10743(Lnet/minecraft/class_2596;)V` instead of
  `method_10743`); each of those names has a single overload, so they select the same method.
- **Resources:** `fabric.mod.json`, `musheor.mixins.json` and the access widener in the built jar
  are identical to the original's.

## External services

Every outbound request in this jar, read from the bytecode. Nothing else opens a connection: there
are no sockets, no raw IPs and no encrypted or computed hostnames. The bundled Java-WebSocket
library is never referenced.

| Endpoint | When | Method and headers | What it sends |
|---|---|---|---|
| `https://mutuals.musheck.dev` | Automatically, once, the first time a player's cape is checked in-world | `GET`<br>`User-Agent: MusheorAddon` | Nothing beyond the request itself (your IP). Receives `kek`/`post` UUID lists used for capes and glow outlines. |
| `https://highways.musheck.dev` | Once, the first time a highway feature is used | `GET`<br>`User-Agent: MusheorAddon` | Nothing beyond the request. Receives ring/diamond highway distances per server. |
| `https://api.musheck.dev/stats` | Every `post-interval` seconds while HighwayBuilder runs, **only if `post-api-key` is set** (default empty) | `POST`<br>`Authorization: Bearer <post-api-key>`<br>`Content-Type: application/json`<br>`Accept: */*`<br>`User-Agent: Mozilla/5.0 … Chrome/112.0.0.0 Safari/537.36` | JSON: your player UUID, username, server address, obsidian placed/mined, ender chests mined, netherrack mined, total blocks mined, timestamp. |
| `https://mc-heads.net/avatar/<name>/64` | MoreTags, once per player name it renders a face for | `GET`<br>`User-Agent: musheor-addon` | The names of players near you, to a third-party site. |
| Your `webhook-link` (Dispatcher) | Only if you set a webhook URL (default empty) | `POST`<br>`Content-Type: application/json`<br>`User-Agent: Mozilla` | Discord embeds for illegal bedrock, visual range, stash and pearl alerts, optionally with coordinates and a role ping. |
| Discord Rich Presence | HighwayBuilder with `discord-rpc` on (default on) | Local IPC to the Discord app (application ID `1346947991059300434`), not an HTTP request | Paver stats as your Discord activity. |

The `api.musheck.dev` request goes through Meteor's `Http` helper, which supplies the Chrome
`User-Agent` and `Accept`. The others use `HttpURLConnection`, which also adds its standard
`Host`, `Accept` and `Connection` headers.

### Authorization and your Minecraft access token

The only `Authorization` header this jar sends is `Bearer <post-api-key>` to `api.musheck.dev`.
That value is whatever you typed into the `post-api-key` setting. The setting's own description
says to get a key from the Crankers Discord, and the server rejects it with 401/403 when the key is
invalid, blacklisted or the UUID isn't yours.

The jar never reads your Minecraft access token. In the original bytecode the only call into the
game session is `Session.getUsername()`, and nothing references `getAccessToken`, the XUID or the
client ID, directly or through reflection.

This covers this jar only. `musheor.plus.*` classes (Musheor+) are loaded by reflection when
present, but they aren't in this jar and weren't analyzed.

## Notes on the original

- **Missing Musheor+ classes.** `musheor.plus.PlusInitializer`, `PlusChatScreenInit` and
  `PlusPlayerUtils` are loaded by reflection but aren't in this jar (a separate "Musheor+" add-on).
- **Dead code kept as shipped.** Six mixins exist but aren't registered in `musheor.mixins.json`
  (see CLASSES.md), and `IceRailBuilder` is unfinished.
- **Apparent bugs in the original, left unchanged:**
  - HighwayBuilder's error messages swap the silk-touch and non-silk pickaxe counts.
  - `LitematicaHelperImpl` throws `NoSuchMethodError` on Litematica ≥ 0.26.16.
- **License.** The earlier copy of this jar declared `"license": "All-Rights-Reserved"` in
  `fabric.mod.json`; the analyzed copy is identical except that line was removed. This source is a
  reconstruction for local analysis, not a licensed release.

## Layout

```
src/main/java          reconstructed source (141 files, 243 classes incl. inner)
src/main/resources     fabric.mod.json, musheor.mixins.json, access widener (Yarn names), assets
reconstruction/        mapping, recovered names, scripts and verification reports (git-ignored)
```

`reconstruction/clean.tiny` is the full Tiny v2 mapping from the Yarn-remapped obfuscated names
(`named`) to the names used here (`clean`). The scripts in `reconstruction/tools` reproduce the pipeline:

1. Remap intermediary → Yarn with tiny-remapper 0.12.3 (`--mixin`).
2. Run `Recover.java names` for the exact enum/record names.
3. Run `build-mapping.ps1` to merge the names and write `clean.tiny`.
4. Remap again with `clean.tiny`.
5. Run `Recover.java records` to rebuild the `Record` attributes.
6. Decompile with Vineflower 1.11.1.
7. Run `postfix.ps1`.
8. Run `Verify.java`.

The scripts' paths point at the scratch folder they were run from.

---

*Autonomously reconstructed by Reaper - a custom harness for reconstructing source from obfuscated bytecode.*