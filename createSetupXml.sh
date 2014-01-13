#!/bin/bash
java -classpath target/scala-2.9.2/piper_2.9.2-0.1-SNAPSHOT.jar:lib/Queue.jar molmed.apps.SetupFileCreator "$@"
