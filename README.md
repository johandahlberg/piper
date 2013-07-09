<pre>
  ___ _               
 | _ (_)_ __  ___ _ _ 
 |  _/ | '_ \/ -_) '_|
 |_| |_| .__/\___|_|  
       |_|  
</pre>
-----------------------------

A pipeline project for the SNP&SEQ platform built on top of GATK Queue. Note that this project is under heavy development and might not be entirely stable. It's also worth noting that this project has the primary goal of analyzing sequencing data from the SNP&SEQ platform and therefore has dependencies on metadata files which are unique created in the workflow of the platform, such as the `report.xml` which are delivered with sequencing data from our facility. I'd however be more than happy to support anyone interested in extending the pipeline to other contexts, if there is any interest.

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

For the standard application of alignment, data processing and variant calling in human relies on data available in the GATK bundle from the Broad Institute. This is available for download at their [website](http://gatkforums.broadinstitute.org/discussion/1213/what-s-in-the-resource-bundle-and-how-can-i-get-it). If you are working on UPPMAX these resources are available at `/bubo/nobackup/uppnex/reference/biodata/GATK/ftp.broadinstitute.org/bundle/2.2/b37/`, however you might want to create your own directory for these in which you soft link these files, as you will be required to create for example bwa indexes.

The path to the GATK bundle needs to be setup in the `globalConfig.sh` file.

Running the pipeline
====================

Right now two primary workflows are supported by Piper (though a more fine grained division of there are in the works at the moment), DNA variant analysis (alignment, qc, data processing and variant calling) and RNA differential expression analysis (as well as producing raw FPKMs). There two workflows are supported by `dna-pipeline.sh` and `rna-pipeline.sh` respectively. Both scripts share a structure which looks like this:

* A number of bash functions which wrap QScripts with there parameters some simply log redirecting etc.
* A Run template (this is probably where you want to start looking), where parameters such as reference genome, interval file (e.g. for targeted sequencing) are set.
* A section where the different QScripts are chained together so that for example: variant calling follows data processing, etc. If you want to change the order of the analysis, or skip some part entirely, comment these lines out and change their input/outputs accordingly.

Setup for run
-------------

Both of workflows above start with a xml file, for example: `pipelineSetup.xml`. This contains information about the raw data (run folders) that you which to run in the project. This is created using the `createSetupXml.sh` script. Before running this make sure that are you run folders are located (or linked) from a common folder (e.g. the runfolders directory under you project), then run this: 

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

NOTE: THE FOLLOWING PARAGRAPH CONCERING RUNNING WILL SOON BE OUTDATE - THE WORKFLOW CONCEPT WILL REPLACE IT.
To run the pipeline you setup the workflow you want according to the above. The you run it with for example `dna-pipeline.sh`. Please note that this assumes that you are allowed to run jobs without a time limit on the machine that you are running on. This will not be the case if you are running on a normal UPPMAX node. Then the you wall time will be limited. In that case you might want to submit the pipeline script as a batch job to the cluster. A sbatch variable template is available at the to of the workflow scripts if you need to to this. Note that you will have to fill this in with your own user names, etc.

Pick the workflow that you want to run, e.g. haloplex. Open the corresponding file in the `workflow` directory with your favorite text editor and edit the last part with the parameters you want to use. Then start the correponding workflow script with for example:
	./workflow/haloplex.sh # OR sbatch workflow/haloplex.sh to sending it to a node

Development
===========

Coding
------

Testing
-------

Licence
=======

