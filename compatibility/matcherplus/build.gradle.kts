plugins {
    id("quickshop.compat-conventions")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(project(":compatibility:common"))
    compileOnly(project(":quickshop-bukkit"))
    compileOnly("com.nexomc:nexo:1.1.0")
    compileOnly("com.dre.brewery:BreweryX:3.4.3")
    compileOnly(files("lib/CrazyCrates+1.11.14+Beta.jar"))
    implementation("de.dustplanet:silkspawners:8.3.0") { isTransitive = false }
    compileOnly("io.th0rgal:oraxen:1.189.0") {
        exclude("me.gabytm.util", "actions-spigot")
        exclude("org.jetbrains", "annotations")
        exclude("com.ticxo", "PlayerAnimator")
        exclude("com.github.stefvanschie.inventoryframework", "IF")
        exclude("io.th0rgal", "protectionlib")
        exclude("dev.triumphteam", "triumph-gui")
        exclude("org.bstats", "bstats-bukkit")
        exclude("com.jeff-media", "custom-block-data")
        exclude("com.jeff-media", "persistent-data-serializer")
        exclude("com.jeff_media", "MorePersistentDataTypes")
        exclude("gs.mclo", "java")
    }
    implementation("com.github.Slimefun:Slimefun4:RC-37")
    compileOnly("xyz.xenondevs.nova:nova-api:0.18")
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.0")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    archiveBaseName.set("Compat-MatcherPlus")
}
