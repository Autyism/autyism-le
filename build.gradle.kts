plugins {
    // Applies fabric-loom-remap up to 1.21.11 and fabric-loom on 26.1+ (unobfuscated)
    id("dev.kikugie.loom-back-compat")
}

fun prop(name: String): String = project.property(name).toString()

val mc = sc.current.version
val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21
// Schematic Preview version on Modrinth's maven ("" where it has no build). Its code is All Rights Reserved: fetched, never committed
val schematicPreview = prop("deps.schematicpreview")
// Sodium release on Modrinth's maven for each version (glass compat mixin; test runs with -PwithSodium)
val sodiumBuild = mapOf("1.21.5" to "mc1.21.5-0.6.13-fabric", "1.21.8" to "mc1.21.8-0.7.3-fabric", "1.21.10" to "mc1.21.10-0.7.3-fabric",
    "1.21.11" to "mc1.21.11-0.8.7-fabric", "26.1.2" to "mc26.1.2-0.9.2-fabric", "26.2" to "mc26.2-0.9.2-fabric", "26.3" to "mc26.3-0.9.2-fabric")
// Syncmatica on Modrinth's maven, by version id (its version numbers repeat across Minecraft versions); only compiled
// against for the shared-schematic names (compat mixins applied when it is installed), loaded in tests with -PwithSyncmatica
val syncmaticaBuild = mapOf("1.21.5" to "7Ma32dRR", "1.21.8" to "x2GEse4M", "1.21.10" to "buY4n8tz", "1.21.11" to "jMRygmU0",
    "26.1.2" to "wsFbuMkX", "26.2" to "POv0nHxV", "26.3" to "uT7TJ2Wp")

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base { archivesName.set(prop("mod.archives_base_name")) }

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") { name = "FabricMC" }
    maven("https://maven.fallenbreath.me/releases") { name = "FallenBreath" }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    maven("https://maven.terraformersmc.com/releases") { name = "TerraformersMC" }
    maven("https://masa.dy.fi/maven") { name = "Masa" }
    maven("https://masa.dy.fi/maven/sakura-ryoko") { name = "SakuraRyoko" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    modImplementation("com.terraformersmc:modmenu:${prop("deps.modmenu")}")

    // 运行时依赖：原版 Litematica + MaLiLib（不打包）
    modImplementation("fi.dy.masa.malilib:malilib-fabric-$mc:${prop("deps.malilib")}")
    modImplementation("fi.dy.masa.litematica:litematica-fabric-$mc:${prop("deps.litematica")}")

    // 可选联动：Schematic Preview（编译期可见，运行时按需检测）
    if (schematicPreview.isNotEmpty()) modCompileOnly("maven.modrinth:schematicpreview:$schematicPreview")
    // 可选联动：Sodium（玻璃兼容 mixin 编译用，不打包，运行时装了才应用）。每个版本用 Modrinth 上那个版本的正式版
    sodiumBuild[mc]?.let { modCompileOnly("maven.modrinth:sodium:$it") }
    syncmaticaBuild[mc]?.let { modCompileOnly("maven.modrinth:syncmatica:$it") }

    // gametest 运行时：加载 Schematic Preview 与本作者的打印机，测试联动
    // -PnoSchematicPreview：不加载 Schematic Preview，测试 ALE 自己的预览、图标和替换
    if (providers.gradleProperty("aleGameTest").isPresent) {
        if (schematicPreview.isNotEmpty() && !providers.gradleProperty("noSchematicPreview").isPresent) {
            modLocalRuntime("maven.modrinth:schematicpreview:$schematicPreview")
        }
        // 渲染环境测试：Sodium（-PwithSodium），1.21.11 可再加 Iris（-PwithIris），都从 Modrinth 取，只在测试时加载
        if (providers.gradleProperty("withSodium").isPresent) sodiumBuild[mc]?.let { modLocalRuntime("maven.modrinth:sodium:$it") }
        if (providers.gradleProperty("withSyncmatica").isPresent) syncmaticaBuild[mc]?.let { modLocalRuntime("maven.modrinth:syncmatica:$it") }
        if (providers.gradleProperty("withIris").isPresent && mc == "1.21.11") modLocalRuntime("maven.modrinth:iris:1.10.7+1.21.11-fabric")
        // 打印机：1.21.11 用 libs 里的发布版，其他版本用打印机仓库各版本的构建
        val printer = if (mc == "1.21.11") rootProject.file("libs/litematica-printer-autyism-1.0.0.jar")
            else rootProject.file("libs/printer/litematica-printer-autyism-1.0.0+$mc.jar")
        if (printer.exists() && providers.gradleProperty("withPrinter").isPresent) {
            modLocalRuntime(files(printer))
        }
        // -Pquiet=<jar>: an extra local mod for the test client only (e.g. one that keeps the window off screen)
        providers.gradleProperty("quiet").orNull?.let { modLocalRuntime(files(it)) }
    }
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain { languageVersion.set(JavaLanguageVersion.of(requiredJava.majorVersion)) }
}

// Mixins for Litematica code that only exists on some versions
if (sc.current.parsed < "1.21.11") {
    sourceSets.named("main") { java.exclude("com/autyism/ale/mixin/litematica/ChunkManagerSchematicMixin.java") }
}
// Schematic previews on 1.21.5: no uniform buffers, texture views or GUI render states yet. These classes have a
// 1.21.5 version of their own in src/v1215 (same names, written for 1.21.5 only)
if (sc.current.parsed < "1.21.6") {
    sourceSets.named("main") {
        java.exclude("com/autyism/ale/preview/PreviewRenderer.java", "com/autyism/ale/preview/PreviewTarget.java",
            "com/autyism/ale/preview/SectionLayers.java", "com/autyism/ale/browser/InfoTextBounds.java")
    }
    // (added to the compile task: Stonecutter sets the source set's directories itself)
    tasks.named<JavaCompile>("compileJava") { source(rootProject.file("src/v1215/java")) }
}

// Block entities in schematic previews: render states from 1.21.9 on, drawn directly before
if (sc.current.parsed >= "1.21.9") {
    sourceSets.named("main") { java.exclude("com/autyism/ale/preview/LegacyBlockEntities.java",
        "com/autyism/ale/mixin/render/PreviewChestRendererMixin.java", "com/autyism/ale/mixin/render/PreviewBedRendererMixin.java") }
} else {
    sourceSets.named("main") { java.exclude("com/autyism/ale/preview/BlockEntitySubmit.java") }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava.majorVersion.toInt())
}

// ALE also runs on servers (exact rail placement and fast breaking need the server). Litematica and MaLiLib are only
// needed on the client: those dependencies sit in a small nested mod that only loads on clients (clientRequirementsJar)

// Without a Schematic Preview build (1.21.5) its compat mixin is not compiled either; its line is left out of the mixin config below
if (schematicPreview.isEmpty()) {
    sourceSets.named("main") { java.exclude("com/autyism/ale/mixin/compat/schematicpreview/**") }
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to prop("mod.id"),
        "mod_name" to prop("mod.name"),
        "mod_version" to prop("mod.version"),
        "minecraft_version" to prop("mod.mc_compat"),
        "loader_compat" to prop("mod.loader_compat"),
        "malilib_compat" to prop("mod.malilib_compat"),
        "litematica_compat" to prop("mod.litematica_compat"),
        "mixin_java" to "JAVA_${requiredJava.majorVersion}",
        "preview_description" to " The schematic browser gets layouts with 3D thumbnails, a rotatable 3D preview with full screen and free camera, and folder icons, and material lists get a Replace button; these follow DimasKama's Schematic Preview and step aside when it is installed.",
        "v12_description" to " Containers that still need filling get an F marker, and with ALE on the server too, Easy Place puts rails down in the schematic's exact shape.",
        "environment" to "*",
    )
    inputs.properties(props)
    // The Litematica / MaLiLib requirements move from fabric.mod.json into the nested client part (clientRequirementsJar below)
    val dropLines = listOf("\"malilib\": \"", "\"litematica\": \"")
    filesMatching("fabric.mod.json") { filter { line -> if (dropLines.any { line.contains(it) }) "" else line } }
    inputs.property("schematic_preview", schematicPreview)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    // Mixins that only exist on some versions (their classes are compiled only there): leave them out of the config elsewhere
    val absentMixins = buildList {
        if (schematicPreview.isEmpty()) add("compat.schematicpreview.")
        if (sc.current.parsed < "26.1") addAll(listOf("render.RenderTypeAccessor", "render.RenderSetupAccessor"))
        if (sc.current.parsed < "26.2") add("render.GameRendererCameraAccessor")
        // Litematica's chunk race (getChunkState) only exists from 1.21.11 on
        if (sc.current.parsed < "1.21.11") add("litematica.ChunkManagerSchematicMixin")
        // Schematic previews before 1.21.9 (no render states): chests and beds drawn outside a world
        if (sc.current.parsed >= "1.21.9") addAll(listOf("render.PreviewChestRendererMixin", "render.PreviewBedRendererMixin"))
        // ... and on 1.21.5 (no GUI render states) the browser's info text is recorded while it is drawn (src/v1215)
        if (sc.current.parsed >= "1.21.6") add("render.InfoTextRecorderMixin")
        // Dev only: -PaleNoMixins=a.B,c.D leaves those mixins out (to find which one breaks something)
        providers.gradleProperty("aleNoMixins").orNull?.split(",")?.filter { it.isNotBlank() }?.let { addAll(it) }
    }
    inputs.property("absent_mixins", absentMixins.joinToString())
    if (absentMixins.isNotEmpty()) {
        filesMatching("autyism-le.mixins.json") { filter { line -> if (absentMixins.any { line.contains("\"$it") }) "" else line } }
    }
}

tasks.withType<Jar>().configureEach {
    val baseName = prop("mod.archives_base_name")
    from(rootProject.file("LICENSE.md")) { rename { "${it}_$baseName" } }
}

// The client part: a mod of its own that only loads on clients and requires Litematica and MaLiLib there. Nested into
// the release jar; a server skips it with its requirements
run {
    val clientRequirementsJar = tasks.register<Jar>("clientRequirementsJar") {
        archiveBaseName.set("autyism-le-client-requirements")
        archiveVersion.set("${prop("mod.version")}+$mc")
        destinationDirectory.set(layout.buildDirectory.dir("client-requirements"))
        val props = mapOf("mod_id" to prop("mod.id"), "mod_name" to prop("mod.name"), "mod_version" to prop("mod.version"),
            "malilib_compat" to prop("mod.malilib_compat"), "litematica_compat" to prop("mod.litematica_compat"))
        inputs.properties(props)
        from(rootProject.file("src/client-requirements")) { expand(props) }
    }
    if (sc.current.parsed >= "26.1") {
        // 26.x is not remapped: the plain jar task is the release jar
        tasks.named<Jar>("jar") { net.fabricmc.loom.task.NestJarsAction.addToTask(this, files(clientRequirementsJar)) }
    } else {
        tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") { nestedJars.from(clientRequirementsJar) }
    }
}

loom {
    runs {
        named("client") {
            programArguments.addAll(listOf("--width", "1280", "--height", "720", "--username", "ALETest"))
            runDir("../../run/client")
        }
    }
}

// Collects the release jars of all versions in build/libs/<mod version>/
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
}

// 客户端 GameTest：./gradlew :1.21.11:runClientGameTest -PaleGameTest [-Pgt=name1,name2]
if (providers.gradleProperty("aleGameTest").isPresent) {
    fabricApi {
        configureTests {
            createSourceSet.set(true)
            modId.set("autyism-le-gametest")
            enableGameTests.set(false)
            enableClientGameTests.set(true)
            eula.set(true)
            clearRunDirectory.set(true)
            username.set("ALEGameTest")
        }
    }
    tasks.matching { it.name == "runClientGameTest" }.configureEach {
        (this as JavaExec).systemProperty("ale.gt", (project.findProperty("gt") ?: "").toString())
        // -PnoNetSync: tests against a real server (fillservux) send commands and packets the network synchronizer can't follow
        if (project.findProperty("noNetSync") != null) (this as JavaExec).systemProperty("fabric.client.gametest.disableNetworkSynchronizer", "true")
        (this as JavaExec).systemProperty("ale.debugrail", (project.findProperty("debugrail") ?: "false").toString())
        (this as JavaExec).systemProperty("ale.frameshot", (project.findProperty("frameShot") != null).toString())
        // -PmeasureOnly: tests only log their measurements (for before/after comparisons)
        (this as JavaExec).systemProperty("ale.measureonly", (project.findProperty("measureOnly") != null).toString())
        (this as JavaExec).systemProperty("ale.reconnectWait", (project.findProperty("reconnectWait") ?: "0").toString())
        // -PtestMods=a.jar;b.jar: extra mod jars for this run, copied into the run directory's mods folder
        // (after the run directory is cleared, before the game starts)
        val extraMods = (project.findProperty("testMods") ?: "").toString().split(";").filter { it.isNotBlank() }.map { File(it) }
        if (extraMods.isNotEmpty()) {
            // Loom's client gametest run directory (the task's working directory is only set when it starts)
            val mods = project.file("build/run/clientGameTest/mods")
            doFirst {
                mods.mkdirs()
                extraMods.forEach { jar ->
                    val target = mods.resolve(jar.name)
                    jar.copyTo(target, overwrite = true)
                    if (!target.isFile) throw GradleException("test mod was not copied: $target")
                    logger.lifecycle("Test mod added: $target")
                }
            }
        }
    }
}
