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
    if (sc.current.parsed >= "1.21.11") sodiumBuild[mc]?.let { modCompileOnly("maven.modrinth:sodium:$it") }

    // gametest 运行时：加载 Schematic Preview 与本作者的打印机，测试联动
    // -PnoSchematicPreview：不加载 Schematic Preview，测试 ALE 自己的预览、图标和替换
    if (providers.gradleProperty("aleGameTest").isPresent) {
        if (schematicPreview.isNotEmpty() && !providers.gradleProperty("noSchematicPreview").isPresent) {
            modLocalRuntime("maven.modrinth:schematicpreview:$schematicPreview")
        }
        // 渲染环境测试：Sodium（-PwithSodium），1.21.11 可再加 Iris（-PwithIris），都从 Modrinth 取，只在测试时加载
        if (providers.gradleProperty("withSodium").isPresent) sodiumBuild[mc]?.let { modLocalRuntime("maven.modrinth:sodium:$it") }
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

// Schematic browser previews, icons and Replace are not ported to 1.21.10 and older yet. Classes without version
// conditions of their own are wrapped in a whole-file >=1.21.11 condition; these have conditions inside, so the
// build leaves them out instead (a condition must not sit inside an inactive one)
val previewFilesWithConditions = listOf(
    "com/autyism/ale/browser/FolderIconScreen.java", "com/autyism/ale/browser/InfoTextBounds.java",
    "com/autyism/ale/preview/FullscreenPreviewScreen.java", "com/autyism/ale/preview/GuiCompat.java",
    "com/autyism/ale/preview/MeshVertexSink.java", "com/autyism/ale/preview/PreviewCamera.java",
    "com/autyism/ale/preview/PreviewInput.java", "com/autyism/ale/preview/PreviewRenderer.java",
    "com/autyism/ale/preview/PreviewTarget.java", "com/autyism/ale/preview/SceneMesher.java",
    "com/autyism/ale/preview/SchematicView.java", "com/autyism/ale/replace/ReplaceBlockScreen.java",
    // no conditions of their own, but they need the ones above (were whole-file >=1.21.11 conditions)
    "com/autyism/ale/browser/BrowserEntryWidget.java", "com/autyism/ale/browser/BrowserLayout.java", "com/autyism/ale/browser/FolderIcons.java",
    "com/autyism/ale/browser/SchematicBrowserWidget.java", "com/autyism/ale/config/AleHotkeys.java", "com/autyism/ale/mixin/malilib/GuiListBaseAccess.java",
    "com/autyism/ale/mixin/malilib/WidgetContainerAccess.java", "com/autyism/ale/mixin/without/schematicpreview/**",
    "com/autyism/ale/preview/MeshLayer.java", "com/autyism/ale/preview/PreviewModel.java", "com/autyism/ale/preview/PreviewPanel.java",
    "com/autyism/ale/preview/Previews.java", "com/autyism/ale/preview/PreviewSession.java", "com/autyism/ale/preview/PreviewWorkers.java",
    "com/autyism/ale/preview/SchematicFiles.java", "com/autyism/ale/preview/ThumbnailCache.java", "com/autyism/ale/replace/MaterialRefresh.java",
    "com/autyism/ale/replace/MaterialReplacer.java"
)
if (sc.current.parsed < "1.21.11") {
    sourceSets.named("main") { java.exclude(previewFilesWithConditions) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava.majorVersion.toInt())
}

// 1.2.0 features are on 1.21.11 and 26.x; 1.21.10 and older leave these classes (and their mixins, below) out for now
val onlyOn12111 = listOf(
    "com/autyism/ale/render/OverlayDepth.java", "com/autyism/ale/verifier/ContainerVerifierGui.java",
    "com/autyism/ale/mixin/litematica/SchematicVerifierInvoker.java", "com/autyism/ale/mixin/litematica/TaskBaseAccessor.java",
    "com/autyism/ale/mixin/litematica/SchematicVerifierSelectMixin.java", "com/autyism/ale/mixin/litematica/VerifierResultListMixin.java",
    "com/autyism/ale/mixin/litematica/GuiSchematicVerifierMixin.java", "com/autyism/ale/mixin/litematica/VerificationResultWidgetMixin.java",
    "com/autyism/ale/mixin/malilib/ConfigButtonStringListMixin.java", "com/autyism/ale/gui/WorldPick.java",
    "com/autyism/ale/mixin/compat/schematicpreview/OpenReplaceDialogMixin.java", "com/autyism/ale/replace/ReplaceDialogs.java",
    "com/autyism/ale/verifier/OpenedContainers.java", "com/autyism/ale/mixin/client/MultiPlayerGameModeMixin.java",
    // Exact rail placement: the server part (ALE runs on a server too) and Easy Place / the API on the client
    "com/autyism/ale/AleCommon.java", "com/autyism/ale/rail/**", "com/autyism/ale/api/**", "com/autyism/ale/mixin/common/**",
    // Fast breaking of deepslate for the printer (server part and the API)
    "com/autyism/ale/mining/**",
    "com/autyism/ale/mixin/litematica/EasyPlaceRailMixin.java", "com/autyism/ale/mixin/litematica/LegacyEasyPlaceRailMixin.java",
    // Container contents in multiplayer: only data the server really sent
    "com/autyism/ale/verifier/ServerContainerData.java", "com/autyism/ale/mixin/litematica/EntityDataReceivedMixin.java",
    "com/autyism/ale/mixin/litematica/ChunkManagerSchematicMixin.java",
    // Render Through Glass with Sodium (compiled against Sodium for 1.21.11 only)
    "com/autyism/ale/mixin/compat/sodium/**"
)
val onlyOn12111Mixins = listOf("litematica.SchematicVerifierInvoker", "litematica.TaskBaseAccessor", "litematica.SchematicVerifierSelectMixin",
    "litematica.VerifierResultListMixin", "litematica.GuiSchematicVerifierMixin", "litematica.VerificationResultWidgetMixin",
    "malilib.ConfigButtonStringListMixin", "compat.schematicpreview.OpenReplaceDialogMixin", "client.MultiPlayerGameModeMixin",
    "litematica.EasyPlaceRailMixin", "litematica.LegacyEasyPlaceRailMixin", "litematica.EntityDataReceivedMixin", "litematica.ChunkManagerSchematicMixin",
    "compat.sodium.SodiumGlassPassMixin")
if (sc.current.parsed < "1.21.11") {
    sourceSets.named("main") { java.exclude(onlyOn12111) }
}
// 1.21.11 also runs on servers (exact rail placement needs the server). Litematica and MaLiLib are only needed on the
// client: those dependencies sit in a small nested mod that only loads on clients
val runsOnServer = sc.current.parsed >= "1.21.11"

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
        // the schematic browser previews are only in 1.21.11 and 26.x so far
        "preview_description" to if (sc.current.parsed >= "1.21.11") " The schematic browser gets layouts with 3D thumbnails, a rotatable 3D preview with full screen and free camera, and folder icons, and material lists get a Replace button; these follow DimasKama's Schematic Preview and step aside when it is installed." else "",
        "v12_description" to if (runsOnServer) " Containers that still need filling get an F marker, and with ALE on the server too, Easy Place puts rails down in the schematic's exact shape." else "",
        "environment" to if (runsOnServer) "*" else "client",
    )
    inputs.properties(props)
    inputs.property("runs_on_server", runsOnServer)
    // fabric.mod.json lists everything; each version drops what does not apply to it: client-only versions have no
    // server part, and the version that runs on servers moves the Litematica / MaLiLib requirements into the nested
    // client part (clientRequirementsJar below)
    val dropLines = if (runsOnServer) listOf("\"malilib\": \"", "\"litematica\": \"")
        else listOf("\"com.autyism.ale.AleCommon\"", "\"autyism-le.common.mixins.json\"")
    filesMatching("fabric.mod.json") { filter { line -> if (dropLines.any { line.contains(it) }) "" else line } }
    if (!runsOnServer) exclude("autyism-le.common.mixins.json")
    inputs.property("schematic_preview", schematicPreview)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    // Mixins that only exist on some versions (their classes are compiled only there): leave them out of the config elsewhere
    val absentMixins = buildList {
        if (schematicPreview.isEmpty()) add("compat.schematicpreview.")
        if (sc.current.parsed < "26.1") addAll(listOf("render.RenderTypeAccessor", "render.RenderSetupAccessor"))
        if (sc.current.parsed < "26.2") add("render.GameRendererCameraAccessor")
        // Schematic browser previews, icons and Replace: 1.21.11 and 26.x; 1.21.10 and older follow later
        if (sc.current.parsed < "1.21.11") addAll(listOf("malilib.WidgetContainerAccess", "malilib.GuiListBaseAccess", "without.schematicpreview."))
        if (sc.current.parsed < "1.21.11") addAll(onlyOn12111Mixins)
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

// The client part of a mod that also runs on servers (see runsOnServer): a mod of its own that only loads on clients
// and requires Litematica and MaLiLib there. Nested into the release jar; a server skips it with its requirements
if (runsOnServer) {
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
    // The preview tests' helpers have version conditions inside: left out on 1.21.10 and older, like the previews
    if (sc.current.parsed < "1.21.11") {
        sourceSets.matching { it.name == "gametest" }.configureEach {
            java.exclude("com/autyism/ale/gametest/PreviewFixtures.java", "com/autyism/ale/gametest/UiDriver.java",
                "com/autyism/ale/gametest/mixin/FrameCounterMixin.java", "com/autyism/ale/gametest/BrowserPreviewGameTest.java",
                "com/autyism/ale/gametest/CompareGameTest.java", "com/autyism/ale/gametest/FolderIconGameTest.java",
                "com/autyism/ale/gametest/MaterialReplaceGameTest.java", "com/autyism/ale/gametest/SpBlackBoxGameTest.java",
                "com/autyism/ale/gametest/StepAsideGameTest.java")
        }
        val previewTests = listOf("SpBlackBoxGameTest", "BrowserPreviewGameTest", "FolderIconGameTest", "MaterialReplaceGameTest",
            "StepAsideGameTest", "CompareGameTest", "FrameCounterMixin")
        tasks.matching { it.name == "processGametestResources" }.configureEach {
            (this as ProcessResources).filesMatching(listOf("fabric.mod.json", "autyism-le-gametest.mixins.json")) {
                filter { line -> if (previewTests.any { line.contains("\"$it\"") || line.contains(".$it\"") }) "" else line }
            }
        }
    }
    // The 1.2.0 tests run on 1.21.11 and 26.x for now
    if (sc.current.parsed < "1.21.11") {
        val newTests = listOf("OverlayVisibilityGameTest", "MarkerGameTest", "VerifierContentsGameTest", "FillMarkerGameTest", "ReplaceUiGameTest", "StillFramesMixin", "NoParticlesMixin",
            "RailGameTest", "RailPacketCounterMixin", "GuiOverlayAccessor", "FastBreakGameTest", "DoubleChestFillGameTest",
            "SignHighlightGameTest")
        sourceSets.matching { it.name == "gametest" }.configureEach {
            java.exclude(newTests.map { "com/autyism/ale/gametest/**/$it.java" } + "com/autyism/ale/gametest/ViewShots.java")
        }
        tasks.matching { it.name == "processGametestResources" }.configureEach {
            (this as ProcessResources).filesMatching(listOf("fabric.mod.json", "autyism-le-gametest.mixins.json")) {
                filter { line -> if (newTests.any { line.contains("\"$it\"") || line.contains(".$it\"") }) "" else line }
            }
        }
    }
    tasks.matching { it.name == "runClientGameTest" }.configureEach {
        (this as JavaExec).systemProperty("ale.gt", (project.findProperty("gt") ?: "").toString())
        (this as JavaExec).systemProperty("ale.debugrail", (project.findProperty("debugrail") ?: "false").toString())
        (this as JavaExec).systemProperty("ale.frameshot", (project.findProperty("frameShot") != null).toString())
        // -PmeasureOnly: tests only log their measurements (for before/after comparisons)
        (this as JavaExec).systemProperty("ale.measureonly", (project.findProperty("measureOnly") != null).toString())
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
