plugins {
  id("quran.android.library.android")
  alias(libs.plugins.anvil)
}

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

android.namespace = "com.quran.labs.androidquran.pages.newmadani"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:pages"))
  implementation(project(":common:audio"))
  implementation(project(":common:upgrade"))

  implementation(project(":pages:common:new_madani_1439_lines"))
  api(project(":pages:data:new_madani_1439_lines"))

  implementation(libs.dagger.runtime)
}
