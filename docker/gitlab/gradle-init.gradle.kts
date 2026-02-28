// Gradle init script для CI с Nexus proxy
// Копируется в ~/.gradle/init.d/ или передаётся через --init-script
//
// Использование в CI:
//   ./gradlew build --init-script /path/to/gradle-init.gradle.kts
//
// Или через GRADLE_USER_HOME:
//   GRADLE_USER_HOME=/cache/gradle (где init.d/nexus.gradle.kts)

val nexusUrl: String? = System.getenv("NEXUS_URL")

if (nexusUrl != null) {
    println("Nexus proxy enabled: $nexusUrl")

    settingsEvaluated {
        pluginManagement {
            repositories {
                maven {
                    name = "nexus-gradle-plugins"
                    url = uri("$nexusUrl/repository/gradle-plugins-proxy/")
                    isAllowInsecureProtocol = true
                }
                maven {
                    name = "nexus-maven-central"
                    url = uri("$nexusUrl/repository/maven-central-proxy/")
                    isAllowInsecureProtocol = true
                }
                gradlePluginPortal()
            }
        }
    }

    allprojects {
        repositories {
            maven {
                name = "nexus-maven-central"
                url = uri("$nexusUrl/repository/maven-central-proxy/")
                isAllowInsecureProtocol = true
            }
            mavenCentral()
        }
    }
} else {
    println("Nexus proxy not configured (NEXUS_URL not set)")
}
