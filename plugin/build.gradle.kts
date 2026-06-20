plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.github.developer"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("com.intellij.git")
        bundledPlugin("platform-images")
    }
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
        kotlin.srcDirs("src/main/kotlin")
        resources.srcDirs("src/main/resources")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.developer.aiworktreereviewer"
        name = "AI Worktree Reviewer"
        version = project.version.toString()
        
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "242"
        }
        
        description = "AI-powered worktree reviewer for Android Studio"
        changeNotes = "Initial release"
        
        vendor {
            name = "Developer"
            email = "developer@github.com"
            url = "https://github.com"
        }
    }
    
    signing {
        certificateChain = System.getenv("CERTIFICATE_CHAIN") ?: ""
        privateKey = System.getenv("PRIVATE_KEY") ?: ""
        password = System.getenv("PRIVATE_KEY_PASSWORD") ?: ""
    }
    
    publishing {
        token = System.getenv("PUBLISH_TOKEN") ?: ""
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
