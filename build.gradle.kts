plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT"
}

fun prop(name: String): String = project.property(name).toString()

version = prop("mod_version")
group = prop("maven_group")
base { archivesName.set(prop("archives_base_name")) }

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") { name = "FabricMC" }
    maven("https://maven.fallenbreath.me/releases") { name = "FallenBreath" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    maven("https://maven.terraformersmc.com/releases") { name = "TerraformersMC" }
    maven("https://masa.dy.fi/maven") { name = "Masa" }
    maven("https://masa.dy.fi/maven/sakura-ryoko") { name = "SakuraRyoko" }
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    modImplementation("com.terraformersmc:modmenu:${prop("modmenu")}")

    // 运行时依赖：原版 Litematica + MaLiLib（不打包）
    modImplementation("fi.dy.masa.malilib:${prop("malilib")}")
    modImplementation("fi.dy.masa.litematica:${prop("litematica")}")

    // 可选联动：Schematic Preview（编译期可见，运行时按需检测）
    modCompileOnly(files("libs/schematicpreview-0.0.17+1.21.11.jar"))

    // gametest 运行时：加载 Schematic Preview 与本作者的打印机，测试联动
    if (providers.gradleProperty("aleGameTest").isPresent) {
        modLocalRuntime(files("libs/schematicpreview-0.0.17+1.21.11.jar"))
        // 与用户实例一致的渲染环境：Sodium（可选再加 Iris）
        if (providers.gradleProperty("withSodium").isPresent) {
            modLocalRuntime(files("libs/sodium-fabric-0.8.7+mc1.21.11.jar"))
            if (providers.gradleProperty("withIris").isPresent) modLocalRuntime(files("libs/iris-fabric-1.10.7+mc1.21.11.jar"))
        }
        if (file("libs/litematica-printer-autyism-1.0.0.jar").exists() && providers.gradleProperty("withPrinter").isPresent) {
            modLocalRuntime(files("libs/litematica-printer-autyism-1.0.0.jar"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to prop("mod_id"),
        "mod_name" to prop("mod_name"),
        "mod_version" to prop("mod_version"),
        "minecraft_version" to prop("minecraft_version"),
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json")) { expand(props) }
}

tasks.jar {
    from("LICENSE.md") { rename { "${it}_${prop("archives_base_name")}" } }
}

loom {
    runs {
        named("client") {
            programArguments.addAll(listOf("--width", "1280", "--height", "720", "--username", "ALETest"))
            runDir("run/client")
        }
    }
}

// 客户端 GameTest：./gradlew runClientGameTest -PaleGameTest [-Pgt=name1,name2]
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
    }
}
