plugins {
  id("quran.android.library.android")
  alias(libs.plugins.anvil)
}

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

android.namespace = "com.quran.labs.androidquran.pages.tajweed"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:pages"))
  implementation(project(":common:audio"))
  implementation(project(":common:upgrade"))

  implementation(project(":pages:common:tajweed"))
  api(project(":pages:data:tajweed"))

  implementation(libs.dagger.runtime)
}
