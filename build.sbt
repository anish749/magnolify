/*
 * Copyright 2019 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
name := "magnolify"
description := "A collection of Magnolia add-on modules"

val magnoliaVersion = "0.12.0"

val avroVersion = "1.9.1"
val bigqueryVersion = "v2-rev20181104-1.27.0"
val catsVersion = "2.0.0"
val datastoreVersion = "1.6.3"
val guavaVersion = "28.1-jre"
val jacksonVersion = "2.10.1"
val jodaTimeVersion = "2.10.5"
val protobufVersion = "3.10.0"
val scalacheckVersion = "1.14.2"
val tensorflowVersion = "1.15.0"

val commonSettings = Seq(
  organization := "com.spotify",
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1"),
  scalacOptions ++= Seq("-target:jvm-1.8", "-deprecation", "-feature", "-unchecked"),
  scalacOptions ++= (scalaBinaryVersion.value match {
    case "2.11" => Seq("-language:higherKinds")
    case "2.12" => Seq("-language:higherKinds")
    case "2.13" => Nil
  }),
  libraryDependencies += {
    if (scalaBinaryVersion.value == "2.11") {
      "me.lyh" %% "magnolia" % "0.10.1-jto"
    } else {
      "com.propensive" %% "magnolia" % magnoliaVersion
    }
  },
  // https://github.com/typelevel/scalacheck/pull/427#issuecomment-424330310
  // FIXME: workaround for Java serialization issues
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  // Release settings
  publishTo := Some(
    if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging
  ),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  Test / publishArtifact := false,
  sonatypeProfileName := "com.spotify",
  licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/spotify/magnolify")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/spotify/magnolify.git"),
      "scm:git:git@github.com:spotify/magnolify.git"
    )
  ),
  developers := List(
    Developer(
      id = "sinisa_lyh",
      name = "Neville Li",
      email = "neville.lyh@gmail.com",
      url = url("https://twitter.com/sinisa_lyh")
    ),
    Developer(
      id = "andrewsmartin",
      name = "Andrew Martin",
      email = "andrewsmartin.mg@gmail.com",
      url = url("https://twitter.com/andrew_martin92")
    ),
    Developer(
      id = "daikeshi",
      name = "Keshi Dai",
      email = "keshi.dai@gmail.com",
      url = url("https://twitter.com/daikeshi")
    ),
    Developer(
      id = "clairemcginty",
      name = "Claire McGinty",
      email = "clairem@spotify.com",
      url = url("http://github.com/clairemcginty")
    )
  )
)

val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val root: Project = project
  .in(file("."))
  .settings(
    commonSettings ++ noPublishSettings
  )
  .aggregate(
    shared,
    scalacheck,
    cats,
    // FIXME: implement these
    // diffy,
    guava,
    avro,
    bigquery,
    datastore,
    tensorflow,
    test
  )

lazy val shared: Project = project
  .in(file("shared"))
  .settings(
    commonSettings,
    moduleName := "magnolify-shared",
    description := "Shared code for Magnolify",
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test
    )
  )

// shared code for unit tests
lazy val test: Project = project
  .in(file("test"))
  .settings(
    commonSettings ++ noPublishSettings,
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test,
      "org.typelevel" %% "cats-core" % catsVersion % Test
    )
  )

lazy val scalacheck: Project = project
  .in(file("scalacheck"))
  .settings(
    commonSettings,
    moduleName := "magnolify-scalacheck",
    description := "Magnolia add-on for ScalaCheck",
    libraryDependencies += "org.scalacheck" %% "scalacheck" % scalacheckVersion
  )
  .dependsOn(
    shared,
    test % "test->test"
  )

lazy val cats: Project = project
  .in(file("cats"))
  .settings(
    commonSettings,
    moduleName := "magnolify-cats",
    description := "Magnolia add-on for Cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-laws" % catsVersion % Test
    )
  )
  .dependsOn(
    shared,
    scalacheck % Test,
    test % "test->test"
  )

lazy val diffy: Project = project
  .in(file("diffy"))
  .settings(
    commonSettings,
    moduleName := "magnolify-diffy",
    description := "Magnolia add-on for diffing data"
  )
  .dependsOn(
    scalacheck % Test,
    test % "test->test"
  )

lazy val guava: Project = project
  .in(file("guava"))
  .settings(
    commonSettings,
    moduleName := "magnolify-guava",
    description := "Magnolia add-on for Guava",
    libraryDependencies ++= Seq(
      "com.google.guava" % "guava" % guavaVersion % Provided
    )
  )
  .dependsOn(
    shared,
    scalacheck % Test,
    test % "test->test"
  )

lazy val avro: Project = project
  .in(file("avro"))
  .settings(
    commonSettings,
    moduleName := "magnolify-avro",
    description := "Magnolia add-on for Apache Avro",
    libraryDependencies ++= Seq(
      "org.apache.avro" % "avro" % avroVersion % Provided
    )
  )
  .dependsOn(
    shared,
    cats % Test,
    scalacheck % Test,
    test % "test->test"
  )

lazy val bigquery: Project = project
  .in(file("bigquery"))
  .settings(
    commonSettings,
    moduleName := "magnolify-bigquery",
    description := "Magnolia add-on for Google Cloud BigQuery",
    libraryDependencies ++= Seq(
      "com.google.apis" % "google-api-services-bigquery" % bigqueryVersion % Provided,
      "joda-time" % "joda-time" % jodaTimeVersion % Provided,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion % Test
    )
  )
  .dependsOn(
    shared,
    cats % Test,
    scalacheck % Test,
    test % "test->test"
  )

lazy val datastore: Project = project
  .in(file("datastore"))
  .settings(
    commonSettings,
    moduleName := "magnolify-datastore",
    description := "Magnolia add-on for Google Cloud Datastore",
    libraryDependencies ++= Seq(
      "com.google.cloud.datastore" % "datastore-v1-proto-client" % datastoreVersion % Provided
    )
  )
  .dependsOn(
    shared,
    cats % Test,
    scalacheck % Test,
    test % "test->test"
  )

lazy val tensorflow: Project = project
  .in(file("tensorflow"))
  .settings(
    commonSettings,
    moduleName := "magnolify-tensorflow",
    description := "Magnolia add-on for TensorFlow",
    libraryDependencies ++= Seq(
      "org.tensorflow" % "proto" % tensorflowVersion % Provided
    )
  )
  .dependsOn(
    shared,
    cats % Test,
    scalacheck % Test,
    test % "test->test"
  )
