plugins {
  id("quran.android.library.android")
  alias(libs.plugins.anvil)
}

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

android.namespace = "com.quran.labs.androidquran.pages.madani_1439"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:pages"))
  implementation(project(":common:audio"))
  implementation(project(":common:upgrade"))
  implementation(project(":pages:common:madani_1439"))

  api(project(":pages:data:madani_1439"))

    implementation(libs.dagger.runtime)
}
