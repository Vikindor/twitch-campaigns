plugins {
    application
    id("java")
}

group = "io.github.vikindor"
version = "1.0"

application {
    mainClass = "io.github.vikindor.twitchcampaigns.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("tools.jackson.core:jackson-databind:3.1.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.1")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
