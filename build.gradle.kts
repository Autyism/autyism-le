plugins {
    // Applies fabric-loom-remap up to 1.21.11 and fabric-loom on 26.1+ (unobfuscated)
    id("dev.kikugie.loom-back-compat")
}

fun prop(name: String): String = project.property(name).toString()

val mc = sc.current.version
val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21
// Schematic Preview version on Modrinth's maven ("" where it has no build). Its code is All Rights Reserved: fetched, never committed
val schematicPreview = prop("deps.schematicpreview")

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

    // gametest 运行时：加载 Schematic Preview 与本作者的打印机，测试联动
    if (providers.gradleProperty("aleGameTest").isPresent) {
        if (schematicPreview.isNotEmpty()) modLocalRuntime("maven.modrinth:schematicpreview:$schematicPreview")
        // 渲染环境测试：Sodium（-PwithSodium），1.21.11 可再加 Iris（-PwithIris），都从 Modrinth 取，只在测试时加载
        val sodium = mapOf("1.21.11" to "mc1.21.11-0.8.7-fabric", "26.1.2" to "mc26.1.2-0.9.2-fabric")
        if (providers.gradleProperty("withSodium").isPresent) sodium[mc]?.let { modLocalRuntime("maven.modrinth:sodium:$it") }
        if (providers.gradleProperty("withIris").isPresent && mc == "1.21.11") modLocalRuntime("maven.modrinth:iris:1.10.7+1.21.11-fabric")
        // 打印机：1.21.11 用 libs 里的发布版，其他版本用打印机仓库各版本的构建
        val printer = if (mc == "1.21.11") rootProject.file("libs/litematica-printer-autyism-1.0.0.jar")
            else rootProject.file("libs/printer/litematica-printer-autyism-1.0.0+$mc.jar")
        if (printer.exists() && providers.gradleProperty("withPrinter").isPresent) {
            modLocalRuntime(files(printer))
        }
    }
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain { languageVersion.set(JavaLanguageVersion.of(requiredJava.majorVersion)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava.majorVersion.toInt())
}

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
    )
    inputs.properties(props)
    inputs.property("schematic_preview", schematicPreview)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    // Mixins that only exist on some versions (their classes are compiled only there): leave them out of the config elsewhere
    val absentMixins = buildList {
        if (schematicPreview.isEmpty()) add("compat.schematicpreview.")
        if (sc.current.parsed < "26.1") addAll(listOf("render.RenderTypeAccessor", "render.RenderSetupAccessor"))
        if (sc.current.parsed < "26.2") add("render.GameRendererCameraAccessor")
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
        (this as JavaExec).systemProperty("ale.debugrail", (project.findProperty("debugrail") ?: "false").toString())
        (this as JavaExec).systemProperty("ale.frameshot", (project.findProperty("frameShot") != null).toString())
    }
}
