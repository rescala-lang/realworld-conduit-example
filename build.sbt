import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import Settings._
import Dependencies._


inThisBuild(scalaVersion_212)
inThisBuild(strictCompile)
ThisBuild / organization := "de.rmgk"

bloopSources

lazy val server = project
.in(file("server"))
.settings(
  name := "server",
  toml,
  upickle,
  betterFiles,
  scribe,
  scalatags,
  vbundleDef,
  fetchJSDependenciesDef,
  (Compile / compile) := ((Compile / compile) dependsOn vbundle).value,
  libraryDependencies ++= Seq(
    "com.outr" %% "scribe-slf4j" % "2.7.10",
    "io.javalin" % "javalin" % "3.6.0"
    ),
  fork := true,
  // must run sbt with graalvm to work
// javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
  graalVMNativeImageOptions ++= List(
    "--allow-incomplete-classpath",
    "--no-fallback",
    //"--report-unsupported-elements-at-runtime",
    "--initialize-at-build-time",
    "-H:+ReportExceptionStackTraces",
    "-H:+JNI"
    ),
  )
.enablePlugins(JavaServerAppPackaging)
.enablePlugins(GraalVMNativeImagePlugin)
.dependsOn(sharedJVM)
.dependsOn(rescalaJVM)
.dependsOn(lociCommunicatorWsJavalinJVM)


lazy val app = project
.in(file("app"))
.enablePlugins(ScalaJSPlugin)
.settings(
  name := "app",
  scalaJSUseMainModuleInitializer := true,
  scalatags,
  upickle,
  )
.dependsOn(sharedJS)
.dependsOn(rescalatags)
.dependsOn(lociCommunicatorWsJavalinJS)
.enablePlugins(SbtSassify)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
.crossType(CrossType.Pure).in(file("common"))
.settings(
  name := "common",
  )

lazy val sharedJVM = shared.jvm
lazy val sharedJS  = shared.js







lazy val nativeImage = taskKey[File]("calls graalvm native image")

nativeImage := (server / GraalVMNativeImage / packageBin).value




lazy val rescalaRepo = uri("../REScala")
lazy val rescalatags = ProjectRef(rescalaRepo, "rescalaJS")
lazy val rescalaJVM  = ProjectRef(rescalaRepo, "rescalaJVM")
lazy val crdtsJVM    = ProjectRef(rescalaRepo, "crdtsJVM")
lazy val crdtsJS     = ProjectRef(rescalaRepo, "crdtsJS")

lazy val lociRepo                     = uri("../loci")
lazy val lociCommunicatorWsJavalinJVM = ProjectRef(lociRepo, "lociCommunicatorWsJavalinJVM")
lazy val lociCommunicatorWsJavalinJS  = ProjectRef(lociRepo, "lociCommunicatorWsJavalinJS")




val fetchJSDependencies    = TaskKey[File]("fetchJSDependencies",
                                           "manually fetches JS dependencies")
val fetchJSDependenciesDef = fetchJSDependencies := {
  val dependencies = List(
    ("localforage.min.js",
    "https://cdn.jsdelivr.net/npm/localforage@1.7.3/dist/localforage.min.js",
    "c071378e565cc4e2c6c34fca6f3a74f32c3d96cb")
    )

  val sha1digester: MessageDigest = MessageDigest.getInstance("SHA-1")

  def sha1hex(b: Array[Byte]): String =
    sha1digester.clone().asInstanceOf[MessageDigest].digest(b)
                .map { h => f"$h%02x" }.mkString

  val dependenciesTarget = target.value.toPath.resolve("resources/jsdependencies")
  Files.createDirectories(dependenciesTarget)
  dependencies.map { case (name, urlStr, sha1) =>
    val url      = new URL(urlStr)
    val filepath = dependenciesTarget.resolve(name)
    val fos      = Files.newOutputStream(filepath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    try IO.transferAndClose(url.openStream(), fos) finally fos.close()
    val csha1 = sha1hex(Files.readAllBytes(filepath))
    if (sha1 != csha1) {
      Files.delete(filepath)
      throw new AssertionError(s"sha1 of »$urlStr« did not match »$sha1«")
    }
    filepath
  }

  dependenciesTarget.toFile
}

val vbundle    = TaskKey[File]("vbundle", "bundles all the viscel resources")
val vbundleDef = vbundle := {
  (app / Compile / fastOptJS).value
  val jsfile       = (app / Compile / fastOptJS / artifactPath).value
  val styles       = (app / Assets / SassKeys.sassify).value
  val bundleTarget = target.value.toPath.resolve("resources/static")
  Files.createDirectories(bundleTarget)

  def gzipToTarget(f: File): Unit = IO.gzip(f, bundleTarget.resolve(f.name + ".gz").toFile)

  gzipToTarget(jsfile)
  gzipToTarget(jsfile.toPath.getParent.resolve(jsfile.name + ".map").toFile)
  styles.foreach(gzipToTarget)

  def sourcepath(p: String) = sourceDirectory.value.toPath.resolve(p).toFile

  val sources = IO.listFiles(sourcepath("main/vid"))
  sources.foreach { f => IO.copyFile(f, bundleTarget.resolve(f.name).toFile) }

  IO.listFiles(sourceDirectory.value.toPath.resolve("main/static").toFile)
    .foreach(gzipToTarget)


  //val swupdated = IO.read(sourcepath("main/js/serviceworker.js"))
  //                  .replaceAllLiterally("[inserted app cache name]", System.currentTimeMillis().toString)
  //IO.gzipFileOut(bundleTarget.resolve("serviceworker.js.gz").toFile) { os =>
  //  os.write(swupdated.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  //}

  IO.listFiles(fetchJSDependencies.value).foreach(gzipToTarget)

  bundleTarget.toFile
}
