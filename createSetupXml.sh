#!/bin/bash
java -classpath target/scala-2.10/piper_2.10-0.1-SNAPSHOT.jar:lib/Queue.jar molmed.apps.SetupFileCreator "$@"
