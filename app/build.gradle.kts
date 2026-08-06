import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 릴리스 서명 정보는 깃에 올리지 않는 keystore.properties 에서 읽는다.
// 파일이 없으면(예: 다른 사람이 클론했을 때) 릴리스 서명 없이 빌드만 되게 둔다.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let {
    rootProject.file(it).exists()
} == true

android {
    namespace = "com.kkeutmal.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kkeutmal.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.3.11"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 코드 축소는 켜지 않는다. 용량의 대부분(29MB)이 사전·뜻풀이 에셋이라
            // 코드를 줄여도 이득이 거의 없고, AvatarView 가 resources.getIdentifier 로
            // 드로어블을 이름으로 찾기 때문에 리소스 축소를 켜면 조용히 깨진다.
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                null // 키스토어가 없으면 서명되지 않은 산출물이 나온다(업로드 불가)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true // AboutActivity 가 BuildConfig.VERSION_NAME 을 쓴다
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
}
