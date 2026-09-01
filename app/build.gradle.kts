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

/**
 * AdMob 앱 ID. **매니페스트에 없으면 앱이 시작하자마자 죽는다** — 광고 SDK 가
 * 초기화 때 이 값을 찾고, 없으면 예외를 던진다.
 *
 * 지금 값은 구글이 공개한 **테스트용**이다. 실제 앱 ID(`ca-app-pub-6583185616347720~숫자`)를
 * 받으면 여기만 바꾸면 된다. 광고 단위 ID 는 Ads.kt 에 따로 있다.
 *
 * 개발 중에 진짜 광고를 띄우면 안 된다 — 자기 광고를 자기가 누르면 부정 클릭으로
 * 계정이 정지될 수 있다. 그래서 디버그 빌드는 항상 테스트 값을 쓴다.
 */
val admobAppIdTest = "ca-app-pub-3940256099942544~3347511713"
val admobAppIdReal = admobAppIdTest // TODO: 실제 앱 ID 를 받으면 여기를 바꾼다

android {
    namespace = "com.kkeutmal.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kkeutmal.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "1.4.0"
        manifestPlaceholders["admobAppId"] = admobAppIdTest
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
            manifestPlaceholders["admobAppId"] = admobAppIdReal
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
    implementation(libs.play.app.update)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    testImplementation(libs.junit)
}
