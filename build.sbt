// ********************************************
// The basics
// ********************************************
name := "Piper"

organization := "molmed"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.1"

// ********************************************
// Tests
// ********************************************
Seq(testNGSettings:_*)

testNGSuites := Seq("src/test/resources/testng.xml")
