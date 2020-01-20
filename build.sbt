import Settings._

name := "realworld"
organization := "de.rmgk"
scalaVersion_213

bloopExportJarClassifiers in Global := Some(Set("sources"))

libraryDependencies ++= Seq(
    "com.outr" %% "scribe-slf4j" % "2.7.10",
    "io.javalin" % "javalin" % "3.6.0"
)

Dependencies.toml
Dependencies.upickle
Dependencies.betterFiles
Dependencies.scribe


fork := true
javaOptions += "-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image"
graalVMNativeImageOptions ++= List(
  //"--allow-incomplete-classpath",
  //"--no-fallback",
  //"--report-unsupported-elements-at-runtime",
  //"--initialize-at-build-time",
  "-H:+ReportExceptionStackTraces"
  )


enablePlugins(JavaAppPackaging)
enablePlugins(GraalVMNativeImagePlugin)

lazy val nativeImage = taskKey[File]("calls graalvm native image")

nativeImage := (GraalVMNativeImage / packageBin).value


