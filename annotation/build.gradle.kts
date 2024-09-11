plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "com.cakcaraka.databinarycompatible"

dependencies {
    implementation(kotlin("stdlib"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.cakcaraka.databinarycompatible"
            artifactId = "annotation"
            version = "1.0"
            from(components["kotlin"])
        }
    }
}