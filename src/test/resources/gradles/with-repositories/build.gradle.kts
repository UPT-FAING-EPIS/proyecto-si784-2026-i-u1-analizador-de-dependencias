repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://jitpack.io")
    }
    maven {
        url = uri("https://nexus.example.com/repository/maven-private/")
        credentials {
            username = System.getenv("NEXUS_USER")
            password = System.getenv("NEXUS_PASS")
        }
    }
}
