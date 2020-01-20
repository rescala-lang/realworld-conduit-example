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
  libraryDependencies ++= Seq(
    "com.outr" %% "scribe-slf4j" % "2.7.10",
    "io.javalin" % "javalin" % "3.6.0"
    ),
  fork := true,
  // must run sbt with graalvm to work
javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image",
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
  scalaJSUseMainModuleInitializer := true
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

lazy val lociRepo = uri("../loci")
lazy val lociCommunicatorWsJavalinJVM = ProjectRef(lociRepo, "lociCommunicatorWsJavalinJVM")
lazy val lociCommunicatorWsJavalinJS = ProjectRef(lociRepo, "lociCommunicatorWsJavalinJS")

