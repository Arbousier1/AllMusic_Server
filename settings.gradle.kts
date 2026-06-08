rootProject.name = "AllMusic"

fun includeAt(path: String, dir: String) {
    include(path)
    project(path).projectDir = file(dir)
}

fun includeClient(name: String) {
    includeAt(":client:$name", "client/client/$name")
}

includeAt(":codec", "client/codec")
includeAt(":codec:buffercodec", "client/codec/buffercodec")
includeAt(":client", "client/client")
includeClient("fabric_1_16_5")
includeClient("fabric_1_20_1")
includeClient("fabric_1_21")
includeClient("fabric_1_21_6")
includeClient("fabric_1_21_11")
includeClient("fabric_26_1")
//include(":client:forge_1_7_10")
//project(":client:forge_1_7_10").projectDir = file("client/client/forge_1_7_10")
//include(":client:forge_1_12_2")
//project(":client:forge_1_12_2").projectDir = file("client/client/forge_1_12_2")
includeClient("forge_1_16_5")
includeClient("forge_1_20_1")
includeClient("neoforge_1_21")
includeClient("neoforge_1_21_6")
includeClient("neoforge_1_21_11")
includeClient("neoforge_26_1")

include(":server")

include(":server:fabric_1_16_5")
include(":server:fabric_1_20_1")
include(":server:fabric_1_21")
include(":server:fabric_1_21_6")
include(":server:fabric_1_21_11")
include(":server:fabric_26_1")

//include(":server:forge_1_7_10")
//include(":server:forge_1_12_2")
include(":server:forge_1_16_5")
include(":server:forge_1_20_1")

include(":server:neoforge_1_21")
include(":server:neoforge_1_21_6")
include(":server:neoforge_1_21_11")
include(":server:neoforge_26_1")

include(":server:spigot")
include(":server:paper")
include(":server:folia")
include(":server:velocity")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.architectury.dev/")
        maven("https://nexus.gtnewhorizons.com/repository/public/")
    }
}
