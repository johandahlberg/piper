<pre>
  ___ _               
 | _ (_)_ __  ___ _ _ 
 |  _/ | '_ \/ -_) '_|
 |_| |_| .__/\___|_|  
       |_|  
</pre>
-----------------------------

A pipeline project for the SNP&SEQ platform built on top of GATK Queue. Note that this project is under heavy development and might not be entirely stable. It's also worth noting that this project has the primary goal of analyzing sequencing data from the SNP&SEQ platform and therefore has dependencies on metadata files which are unique created in the workflow of the platform, such as the `report.xml` which are delivered with sequencing data from our facility. I'd however be more than happy to support anyone interested in extending the pipeline to other contexts, if there is any interest.

Piper builds on the concept of standardized workflows for different next-generation sequencing applications. At the moment Piper supports the following workflows:

* WholeGenome: Which is used for human whole genome sequencing data. This goes through alignment, alignment quality control, dataprocessing, variant calling and variant filtration according to the [best practice recommended by the Broad Institute](http://www.broadinstitute.org/gatk/guide/topic?name=best-practices), using primarily the GATK.
* TruSeq and SureSelect human exome sequencing: These use basically the same pipeline as the whole genome pipeline, but with the modifications suggested in the [best practice document](http://www.broadinstitute.org/gatk/guide/topic?name=best-practices) for exome studies.
* RNACounts: Which produces [FPKMs](http://cufflinks.cbcb.umd.edu/faq.html#fpkm) for transcripts of an existing reference annotation using Tophat for mapping and Cufflinks to produce the FPKMs.
* RNADifferentialExpression: This performs differential expression studies of transcripts of an existing reference annotation using Tophat for mapping and Cuffdiff for differential expression analysis. This can then be visualized using the [cummeRbund](http://compbio.mit.edu/cummeRbund/) R package.
* Additionally Piper contains a DNAGeneralPipeline and a RNAGeneralPipeline which are used as templates for creating new workflows. These can also be used for creating workflows for species other that human - however they are not expected to work out of the box. You have been warned.

All supported workflows are available in the `workflows` directory in the project root.

Prerequisites and installation
==============================

Piper runs has been tested on the Java(TM) SE Runtime Environment (build 1.6.0_18) on the UPPMAX cluster. It might run on in other environments, but this is untested. Besides the JVM Piper depends on the [sbt](http://www.scala-sbt.org/) and [ant](http://ant.apache.org/), and [git](http://git-scm.com/) to checkout the source. To install piper, make sure that there programs are on you path, then clone this repository and run the setup script:

    git clone https://github.com/johandahlberg/piper.git
    cd piper
    ./setup.sh
    
Further more as Piper acts as a wrapper for several standard bioinformatics programs it requires that these are installed. At this point it requires that the following programs are installed (depending somewhat on the application):

* [bwa](http://bio-bwa.sourceforge.net/) 0.6.2
* [samtools](http://samtools.sourceforge.net/) 0.1.12-10
* [tophat](http://tophat.cbcb.umd.edu/) 2.0.4
* [cutadapt](https://code.google.com/p/cutadapt/) 1.2.1
* [cufflinks](http://cufflinks.cbcb.umd.edu/) 2.1.1

The paths for these programs are setup in the `globalConfig.sh` file. If you are running on UPPMAX these should already be pointing to the correct locations. But if not, you need to change them there.

Resource files
==============

For the standard application of alignment, data processing and variant calling in human relies on data available in the GATK bundle from the Broad Institute. This is available for download at their [website](http://gatkforums.broadinstitute.org/discussion/1213/what-s-in-the-resource-bundle-and-how-can-i-get-it). If you are working on UPPMAX these resources are available at `/bubo/nobackup/uppnex/reference/biodata/GATK/ftp.broadinstitute.org/bundle/2.2/`, however you might want to create your own directory for these in which you soft link these files, as you will be required to create for example bwa indexes.

The path to the GATK bundle needs to be setup in the `globalConfig.sh` file. For MolMed users this has been setup to reasonable defaults.

Running the pipeline
====================

There are a number of workflows currently supported by Piper (See below). All workflow scripts share a similar structure which looks like this:

* A number of bash functions which wrap QScripts with their parameters some simple log redirecting etc.
* A Run template (this is probably where you want to start looking), where parameters such as reference genome, interval file (e.g. for targeted sequencing) are set.
* A section where the different QScripts are chained together so that for example: variant calling follows data processing, etc. If you want to change the order of the analysis, or skip some part entirely, comment these lines out and change their input/outputs accordingly. (Note that not all workflows are setup this way, and if they are not you will have to change the qscript to solve this)

Setup for run
-------------

All workflows start with a xml file, for example: `pipelineSetup.xml`. This contains information about the raw data (run folders) that you want to run in the project. This is created using the `createSetupXml.sh` script. Before running this make sure that are you run folders are located (or linked) from a common folder (e.g. the runfolders directory under you project), then run this: 

    ./createSetupXML.sh pipelineSetup.xml

and answer the questions. This will create you setup file, which should look something like this:

    <Project Name="TestProject" SequencingCenter="SnqSeq - Uppsala"
        Platform="Illumina" UppmaxProjectId="a2009002">
	
	    <RunFolder Report="src/test/resources/testdata/runFoldersForMultipleSample/runfolder1/report.xml">
		    <SampleFolder Name="1" Path="src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/Sample_1" Reference="src/test/resources/testdata/exampleFASTA.fasta"></SampleFolder>
	    </RunFolder>
		
	    <RunFolder Report="src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/report.xml">
		    <SampleFolder Name="1" Path="src/test/resources/testdata/runFoldersForMultipleSample/runfolder2/Sample_1" Reference="src/test/resources/testdata/exampleFASTA.fasta"></SampleFolder>
	    </RunFolder>
    </Project>


This is the file you should assign the `PIPELINE_SETUP_XML` variable to in the workflow scripts.

Run
---

Pick the workflow that you want to run, e.g. haloplex. Open the corresponding file in the `workflow` directory with your favorite text editor and edit the run template part (it's located towards the end of the file) with the parameters you want to use. Then start the correponding workflow script with for example:
	./workflow/haloplex.sh # OR sbatch workflow/haloplex.sh to sending it to a node

Development
===========

The heavy lifting in Piper is primarilly done in Scala, with Bash glueing together the different scripts to into workflows. Some additional Java and the occational Perl component is used, but the main body of the code is 

Coding
------
To work on the Piper project I recommend using the [Scala IDE](http://scala-ide.org/). To start developing follow the installation procedure outlined above. When you have finised the installation you can set the project up for you IDE by running:
	sbt eclipse
This will create the necessary project file for you to be able to import the project into the Scala IDE and start developing away.

Although the Scala IDE will compile the code as you type away, you will probably also want to get the hang of a few basic SBT commands (which you can either run from the interactive sbt console which you start by typing `sbt` in the project root folder, or by typing `sbt <command>` to run it straight from the CLI):

	compile
Will compile your project.

	package
Will produce you jars (look under the `target` dir and in the dir for the Scala version that you build targets)

	clean
If something looks strange it's probably a good idea to run this. It deletes all of your class files so that you can create be sure you have a totally clean build.

	test
Run the tests (for more on testing, see the testing chapter) - note that by default this only dry runs the qscript integration tests, which (basically making sure that they compile, but giving you no guarantees for runtime functionality).

### Making Piper generate graph files
TODO


### Using the XML binding compiler (xjc):
TODO

Testing
-------

### Running pipeline tests
 
### Continious integration using Travis:
TODO

Troubleshooting
===============

Licence
=======

