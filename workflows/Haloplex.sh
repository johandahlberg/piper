#!/bin/bash -l
#SBATCH -A a2009002
#SBATCH -p core
#SBATCH -n 1
#SBATCH -t 120:00:00
#SBATCH -J snp_seq_pipeline_controller
#SBATCH -o pipeline-%j.out
#SBATCH -e pipeline-%j.error
#SBATCH --qos=seqver

# NOTE
# LOOK AT THE BOTTOM OF THE SCRIPT TO SEE SETUP ETC.

#------------------------------------------------------------------------------------------
# Functions below run the different qscripts and return end by echoing a path to the
# output cohort file (or something similar). This can then be feed to the next part of
# the pipeline  to chain the scripts together, provided that they have compatiable
# output types.
#------------------------------------------------------------------------------------------

#------------------------------------------------------------------------------------------
# Align fastq files using bwa - outputs bam files.
#------------------------------------------------------------------------------------------
function haloplex {
    source piper -S ${SCRIPTS_DIR}/Haloplex.scala \
			    --input $1 \
			    --resources ${GATK_BUNDLE_HG19} \
			    -intervals ${INTERVALS} \
			    -outputDir ${OUTPUT_DIR}/ \
			    -bwa ${PATH_TO_BWA} \
			    -samtools ${PATH_TO_SAMTOOLS} \
			    -cutadapt ${PATH_TO_CUTADAPT} \
			    --nbr_of_threads ${NBR_OF_THREADS} \
	            -jobRunner ${JOB_RUNNER} \
        		-jobNative "${JOB_NATIVE_ARGS}" \
			    --job_walltime 36000 \
			    -run \
			    ${DEBUG} >> ${LOGS}/haloplex.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from haloplex. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi

    echo "NoOutputFromThisFunction"
}

# Load the correct java engine
module load java/sun_jdk1.6.0_18
module load R/2.15.0

#---------------------------------------------
# Run template - setup which files to run etc
#---------------------------------------------

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

PIPELINE_SETUP_XML="pipelineSetup.xml"
INTERVALS="" # Your design bed-file
QOS="" # e.g. --qos=seqver
OUTPUT_DIR="pipeline_output/haloplex"

#---------------------------------------------
# The actual running of the script
# Modify this if you want to chain the parts
# in a different way.
#---------------------------------------------

mkdir $OUTPUT_DIR
PIPELINE_OUTPUT=$(haloplex ${PIPELINE_SETUP_XML})

# Perform final clean up
final_clean_up

#TODO Fix mechanism for setting walltimes.
