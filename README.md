<pre>
  ___ _               
 | _ (_)_ __  ___ _ _ 
 |  _/ | '_ \/ -_) '_|
 |_| |_| .__/\___|_|  
       |_|  
</pre>
-----------------------------

[![Build Status](https://travis-ci.org/johandahlberg/piper.png?branch=master)](https://travis-ci.org/johandahlberg/piper)

A pipeline project for the [SNP&SEQ Technology platform](http://www.molmed.medsci.uu.se/SNP+SEQ+Technology+Platform/) built on top of [GATK Queue](http://www.broadinstitute.org/gatk/guide/topic?name=intro#intro1306). Note that this project is under heavy development and might not be entirely stable at this point. It's also worth noting that this project has the primary goal of analyzing sequencing data from the SNP&SEQ Technology platform and therefore has dependencies on metadata files which are created in the workflow of the platform, such as the `report.xml` which is created by [Sisyphus](https://github.com/Molmed/sisyphus) and delivered with sequencing data from our facility. I'd however be more than happy to support anyone interested in extending the pipeline to other contexts.

Piper builds on the concept of standardized workflows for different next-generation sequencing applications. At the moment Piper supports the following workflows:

* WholeGenome: For human whole genome sequencing data. This goes through alignment, alignment quality control, dataprocessing, variant calling and variant filtration according to the [best practice recommended by the Broad Institute](http://www.broadinstitute.org/gatk/guide/topic?name=best-practices), using primarily the GATK.
* TruSeq and SureSelect human exome sequencing: These use basically the same pipeline as the whole genome pipeline, but with the modifications suggested in the [best practice document](http://www.broadinstitute.org/gatk/guide/topic?name=best-practices) for exome studies.
* Haloplex: Haloplex targeted sequencing analysis. Including alignment, data processing and variant calling.
* RNACounts: Which produces [FPKMs](http://cufflinks.cbcb.umd.edu/faq.html#fpkm) for transcripts of an existing reference annotation using Tophat for mapping and Cufflinks to produce the FPKMs.
* RNADifferentialExpression: This performs differential expression studies of transcripts of an existing reference annotation using Tophat for mapping and Cuffdiff for differential expression analysis. This can then be visualized using the [cummeRbund](http://compbio.mit.edu/cummeRbund/) R package.
* Additionally Piper contains a DNAGeneralPipeline and a RNAGeneralPipeline which are used as templates for creating new workflows. These can also be used for creating workflows for species other than human - however they are not expected to work out of the box. You have been warned.

All supported workflows are available in the `workflows` directory in the project root.

Prerequisites and installation
==============================

Piper has been tested on the Java(TM) SE Runtime Environment (build 1.7.0_25) on the [UPPMAX](http://www.uppmax.uu.se) cluster Kalkyl. It might run in other environments, but this is untested. Besides the JVM Piper depends on [Maven](http://maven.apache.org/) for building (the GATK) and [git](http://git-scm.com/) to checkout the source. To install piper, make sure that these programs are in you path, then clone this repository and run the setup script:

    git clone https://github.com/Molmed/piper.git
    cd piper
    ./setup.sh
    
As Piper acts as a wrapper for several standard bioinformatics programs it requires that these are installed. At this point it requires that the following programs are installed (depending somewhat on the application):

* [bwa](http://bio-bwa.sourceforge.net/) 0.6.2
* [samtools](http://samtools.sourceforge.net/) 0.1.12-10
* [tophat](http://tophat.cbcb.umd.edu/) 2.0.4
* [cutadapt](https://code.google.com/p/cutadapt/) 1.2.1
* [cufflinks](http://cufflinks.cbcb.umd.edu/) 2.1.1

The paths for these programs are setup in the `globalConfig.sh` file. If you are running on UPPMAX these should already be pointing to the correct locations. But if not, you need to change them there.

Resource files
==============

For the standard application of alignment, data processing and variant calling in human relies on data available in the GATK bundle from the Broad Institute. This is available for download at their [website](http://gatkforums.broadinstitute.org/discussion/1213/what-s-in-the-resource-bundle-and-how-can-i-get-it). If you are working on UPPMAX these resources are available at `/pica/data/uppnex/reference/biodata/GATK/ftp.broadinstitute.org/bundle/2.2/`, however you might want to create your own directory for these in which you soft link the files, as you will be required to create for example bwa indexes.

The path to the GATK bundle needs to be setup in the `globalConfig.sh` file. For MolMed users this has been setup to reasonable defaults.

Running the pipeline
====================

There are a number of workflows currently supported by Piper (See below). For most users these should just be called accoring to the run examples below. Currently there are two ways which the workflows are setup. The RNA-related scripts are setup in the following way:

* A number of bash functions which wrap QScripts with their parameters, some simple log redirecting etc.
* A Run template, where parameters such as reference genome, interval file (e.g. for targeted sequencing) are set.
* A section where the different QScripts are chained together so that for example: variant calling follows data processing, etc. If you want to change the order of the analysis, or skip some part entirely, comment these lines out and change their input/outputs accordingly. (Note that not all workflows are setup this way, and if they are not you will have to change the qscript to solve this)

This has however been significantly simplified for the DNA workflows, which are now just wrappers around a single QScript to spare the end user the need to specify all of Pipers (many) parameters.

Setup for run
-------------

All workflows start with an xml file, for example: `pipelineSetup.xml`. This contains information about the raw data (run folders) that you want to run in the project. This is created using the `createSetupXml.sh` script. Before running this make sure that your run folders are located (or linked) from a common folder (e.g. the runfolders directory under your project), then run this: 

    ./createSetupXml.sh pipelineSetup.xml

and answer the questions. This will create your setup file, which should look something like this:

    <Project Name="TestProject" SequencingCenter="UU-SNP"
        Platform="Illumina" UppmaxProjectId="a2009002">
	
	    <RunFolder Report="src/test/resources/testdata/runFoldersForMultipleSample/runfolder1/report.xml">
		    <SampleFolder Name="1" Path="src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/Sample_1" Reference="src/test/resources/testdata/exampleFASTA.fasta"></SampleFolder>
	    </RunFolder>
		
	    <RunFolder Report="src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/report.xml">
		    <SampleFolder Name="1" Path="src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/Sample_1" Reference="src/test/resources/testdata/exampleFASTA.fasta"></SampleFolder>
	    </RunFolder>
    </Project>


This is the file you should assign to the `PIPELINE_SETUP_XML` variable to in the workflow scripts.

The `createSetupXml.sh` script will look for a file in each run folder named `report.xml` or `report.tsv`. The `report.xml` file should have the standard format used at the SNP&SEQ Technology platform, the tsv file format is provided to make it easier for project which have not been sequenced at the SNP&SEQ Technology platform to use Piper. This is a simple tab separated file which should look like the following example:

	#SampleName	Lane	ReadLibrary	FlowcellId
	MyFirstSample	1	FirstLib	9767892AVF
	MyFirstSample   2	SecondLib	9767892AVF
	MySecondSample	1	SomeOtherLib	9767892AVF

Running
-------

Pick the workflow that you want to run, e.g. haloplex. Then initiate it (by simply running it, or if you do not have access to node where you can continually run a JVM by using `sbatch` to send it to a node) accoding to the examples below.

Note that all workflows are by default setup to be run with the human_g1k_v37.fasta reference, and associated annotations. This means that if you need some other reference, you will have to set it up manually by configuring the "Run Template" part of the workflow script (and depending somewhat on the use case, make changes to the qscripts themself). 

It's also worth mentioning that all the scripts have a optional `alignments_only` flag which can be set if you are only interested in running the aligments. This is useful what you are working on a project where data is added over time, but you want to create the aligments as data comes in, and then join across the sequencing runs and continue with the downstream analysis.

Unless the `run` flag is added to the workflow commandline the pipeline will only dry run (i.e. it will not actually run any jobs), this is useful to make sure that all commandline have been setup up properly. Once you are happy with the setup add `run` to the commandline to run the pipeline.


**Haloplex**

    ./workflows/Haloplex.sh --xml_input <setup.xml> --intervals <regions file> --amplicons <amplicon file> [--alignments_only] [--run]

The files associated with the Haloplex design can be downloaded from Agilents homepage. Please note that the design files will be converted to interval files to work with Picard. In this process the names in the files are converted to work with the "b37" genome reference, rather than "hg19" which is the reference used by agilent. This means that if you want to use "hg19" you have to specify the `--do_not_convert` flag in the qscript.

**RNACounts**

    ./workflows/RNACounts.sh --xml_input <setup.xml> --library_type <fr-secondstrand/fr-firststrand/fr-unstranded> [--alignments_only] [--run]

Library types depends on the protcol used. For ScriptSeq, it's for example `fr-secondstrand`.

**RNADifferentialExpression**

    ./workflows/RNADifferentialExpression.sh --xml_input <setup.xml> --library_type <fr-secondstrand/fr-firststrand/fr-unstranded> [--replicates <replicate_file>] [--alignments_only] [--run]

To be able to run this workflow samtools needs to be available on the `PATH`, you can get this by loading the samtools module in your `.bashrc` or by explicitly adding it to the `PATH` variable.

If you have replicates in you cohort specify them in a file (under `--replicates`) accoring to the following: On each line should be the label (e.g. the name of the condition) and sample names of the samples included in that condition seperated by tabs. Please note that only samples which have replicates need to be specified. The default is one sample - one replicate.


**Exome**
    
    ./workflows/Exome.sh --xml_input <setup.xml> <--sureselect> || <--truseq> [--alignments_only] [--run]

Pick one if either `--sureselect` or `--truseq` to set which exome intervals should be used.

**WholeGenome**

    ./workflows/WholeGenome.sh --xml_input <setup.xml> [--alignments_only] [--run]


Monitoring progress
-------------------

To follow the progress of the run look in the `pipeline_output/logs` folder. There you will find the logs for the different scripts. By searching the file for "Run", you can see how many jobs are currently running, how many have finished, and how many have failed. A recommendation is to use e.g. `less -S` to view the file with unwrapped lines, as it is quite difficult to read otherwise.


Development
===========

The heavy lifting in Piper is primarilly done in Scala, with Bash glueing together the different scripts to into workflows. Some additional Java and the occasional Perl component is used, but the main body of the code is written in Scala.

Coding
------

For an introduction to Queue, on which Piper is built, see: http://gatkforums.broadinstitute.org/discussion/1306/overview-of-queue

To work on the Piper project I recommend using the [Scala IDE](http://scala-ide.org/). To start developing follow the installation procedure outlined above. When you have finised the installation you can set the project up for your IDE by running:

    sbt eclipse

This will create the necessary project file for you to be able to import the project into the Scala IDE and start developing.

Although the Scala IDE will compile the code as you type, you will probably also want to get the hang of a few basic SBT commands (which you can either run from the interactive sbt console which you start by typing `sbt` in the project root folder, or by typing `sbt <command>` to run it straight from the CLI):

    compile

Will compile your project.

    package

Will produce the jars (look under the `target` dir and in the dir for the Scala version that your build targets)

    clean

If something looks strange it's probably a good idea to run this. It deletes all of your class files so that you can be sure you have a totally clean build.

    test

Run the tests (for more on testing, see the testing chapter) - note that by default this only dry runs the qscript integration tests, which basically makes sure that they compile, but giving you no guarantees for runtime functionality.

### Project organization

This is an (incomplete) overview of Pipers project organization, describing the most important parts of the setup.

<pre>
|-.travis.yml       # Travis setup file
|-.gitignore        # file which git should ignore
|-build.sbt         # The primary build definition file for sbt (there is additional build info under project)
|-globalConfig.sh   # Global setup with e.g. paths to programs etc.
|-piper             # Basic runscript
|-README.md         # This readme
|----sbt            # The sbt compiler - included for the users convinience
|----lib            # Unmanaged dependecencies
|----project        # Build stuff for sbt
|----resources      # Unmanaged additional dependecencies which are manually downloaded by setup script, and a perl hack which is currently used to sync reads
|----src            # The source of piper
    |----main
        |----java
        |----resources
        |----scala
    |----test
        |----java
        |----resources
        |----scala
|----target         # Generated build files
|----workflows      # The workflow file which are used to actually run piper
</pre>


### Making Piper generate graph files
Queue includes functionallity to generate dot files to visualize the jobs graph. This is highly useful when debugging new qscripts as it lets you see how the jobs connect to one another, so if you have made some mistake in the chaining of the dependencies it is easy to spot. ".dot" files can be opened with e.g. [xdot](https://github.com/jrfonseca/xdot.py).


### Using the XML binding compiler (xjc):
To generate the xml read classes I use xjc, which uses an xml schema in xsd format to generate a number of java classes, which can then be used to interact with the setup and report xml files. These classes are used by the SetupXMLReader and the SetupFileCreator. An example of how to generate the classes is seen below:

	 xjc -d src/main/java/ src/main/resources/PipelineSetupSchema.xsd

Testing
-------

### Running pipeline tests
Running the tests is done by `sbt test`. However there are some things which need to be noted. As the pipeline tests take a long time and have dependencies on outside programs (such as bwa for alignment, etc.) these can only be run on machine which have all the required programs installed, and which have all the correct resources. This means that by default the tests are setup to just compile the qscripts, but not run them. If you want to run the qscripts you need to go into `src/test/resources/testng.xml` and set the value of the runpipeline parameter to 'true'.

### Writing pipeline tests
Pipeline tests are setup to run a certain QScript and check the md5sums of the outputs. If md5sums do not match, it will show you what the differences between the files are so that you can decide if the changes to the output are reasonable concidering the changes to the code you have made. At the moment pipeline tests are just setup to accutally run (with all the necessary resources, etc) on my workstation. In furture versions I hope to be able to make this more portable.

### Continuous integration using Travis:
Piper uses [Travis](https://travis-ci.org/) for continious integration. For instruction on how to set this up with a github repository see: http://about.travis-ci.org/docs/user/getting-started/

Troubleshooting
===============

Old projects
------------
In projects where data was generated before the spring of 2013, the report.xml files do not fulfill the current specification. To fix this you need to find the following row in the `report.xml`:
    
    <SequencingReport>

and substitute it for:

    <SequencingReport  xmlns="illuminareport.xml.molmed">

Licence
=======

The MIT License (MIT)

Copyright (c) 2013  The SNP&SEQ Technology Platform, Uppsala

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
