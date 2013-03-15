// ********************************************
// The basics
// ********************************************
name := "Piper"

organization := "molmed"

version := "0.1-SNAPSHOT"

scalaVersion := "2.9.2"

// ********************************************
// Tests
// ********************************************
seq(testNGSettings:_*)

testNGSuites := Seq("src/test/resources/testng.xml")

// The jvm needs to fork at testing for gatk pipelinetest to work.

fork in test := true

//javaOptions in test += "-Dpipeline.run=run"