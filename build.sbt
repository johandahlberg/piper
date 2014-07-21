// ********************************************
// The basics
// ********************************************
name := "Piper"

organization := "molmed"

version := "v1.2.0-beta12"

scalaVersion := "2.10.1"

// ********************************************
// Tests
// ********************************************
Seq(testNGSettings:_*)

testNGSuites := Seq("src/test/resources/testng.xml")
