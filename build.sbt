seq(testNGSettings:_*)

testNGSuites := Seq("src/test/resources/testng.xml")

mainClass in oneJar := Some("org.broadinstitute.sting.gatk.CommandLineGATK")

// The jvm needs to fork at testing for gatk pipelinetest to work.
fork in test := true

javaOptions in test += "-Dpipeline.run=run"