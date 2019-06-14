
import java.io.File
import java.nio.file._

import $file.website.Website, Website.{Mdoc, Relativize, Util}

val sbtCmd = Seq("sbt", "++2.12.8")

lazy val version = Util.cached("version") {
  Util.outputOf(sbtCmd ++ Seq("export channels/version"))
    .linesIterator
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSeq
    .last
}

lazy val latestRelease = Util.cached("latest-release") {
  import sys.process._
  val v = Seq("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
    .!!
    .trim
    .stripPrefix("v")
  assert(v.nonEmpty, "Could not find latest release")
  v
}


lazy val scalaVersion = Util.cached("scala-version") {
  Util.outputOf(sbtCmd ++ Seq("export channels/scalaVersion"))
    .linesIterator
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSeq
    .last
}

lazy val ammoniteVersion = Util.cached("ammonite-version") {
  Util.runCmd(sbtCmd ++ Seq("interpreter-api/exportVersions"))
  new String(Files.readAllBytes(Paths.get("modules/shared/interpreter-api/target/ammonite-version")), "UTF-8").trim
}

lazy val mdocProps: Map[String, String] = {
  val isSnapshot = version.endsWith("SNAPSHOT")
  val extraSbt =
    if (isSnapshot)
      """resolvers += Resolver.sonatypeRepo("snapshots")""" + "\n"
    else
      ""
  val extraCoursierArgs =
    if (isSnapshot)
      "-r sonatype:snapshots "
    else
      ""
  Map(
    "VERSION" -> version,
    "LATEST_RELEASE" -> latestRelease,
    "EXTRA_SBT" -> extraSbt,
    "AMMONITE_VERSION" -> ammoniteVersion,
    "SCALA_VERSION" -> scalaVersion,
    "EXTRA_COURSIER_ARGS" -> extraCoursierArgs
  )
}

@main
def main(publishLocal: Boolean = false, npmInstall: Boolean = false, yarnRunBuild: Boolean = false, watch: Boolean = false, relativize: Boolean = false): Unit = {

  assert(!(watch && relativize), "Cannot specify both --watch and --relativize")

  if (publishLocal)
    // Staying in scala 2.12 for mdoc for now
    Util.runCmd(sbtCmd ++ Seq("set version in ThisBuild := \"" + version + "\"", "interpreter-api/publishLocal", "jupyter-api/publishLocal", "scala-kernel-api/publishLocal", "almond-spark/publishLocal"))

  // be sure to adjust that
  val websiteDir = new File("docs/website")

  val yarnRunBuildIn =
    if (yarnRunBuild)
      Some(websiteDir)
    else
      None

  if (npmInstall)
    Util.runCmd(Seq("npm", "install"), dir = websiteDir)

  val mdoc = new Mdoc(
    new File("docs/pages"),
    new File("docs/processed-pages"),
    scalaVersion,
    dependencies = Seq(
      s"sh.almond:scala-kernel-api_$scalaVersion:$version",
      "-r", "jitpack"
    ),
    mdocProps = mdocProps
  )

  if (watch)
    mdoc.watch(yarnRunStartIn = yarnRunBuildIn)
  else {
    mdoc.run(yarnRunBuildIn = yarnRunBuildIn)
    if (relativize)
      Relativize.relativize(websiteDir.toPath.resolve("build"))
  }
}
