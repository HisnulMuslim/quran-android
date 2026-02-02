plugins {
  id("quran.android.library.android")
}

android.namespace = "com.quran.labs.androidquran.pages.data.tajweed"

dependencies {
  implementation(project(":common:data"))
}
