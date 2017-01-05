lazy val futures = project
  .in(file("."))
  .enablePlugins(Cinnamon)
  .settings(
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.8",
      // for MDC progation, only need to add this dependency:
      Cinnamon.library.cinnamonSlf4jMdc,
      // instrumentation SPI for Scala Futures (normally internal only):
      Cinnamon.library.cinnamonScalaFutureSPI % "provided"
    ),
    cinnamon in run := true
  )
