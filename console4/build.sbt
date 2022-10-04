// use the manually created jar incl. it's deps
name := "console4"

enablePlugins(JavaAppPackaging)
scalaVersion := "3.2.0"

Compile/mainClass := Some("ammonite.AmmoniteMain")

libraryDependencies ++= Seq(
  "io.joern" %% "ammonite-fat" % "2.5.4+3-fatterjar" cross CrossVersion.full,
)
