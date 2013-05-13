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
# Align fastq files using tophat.
#------------------------------------------------------------------------------------------

function alignWithTophat {
    source piper -S ${SCRIPTS_DIR}/AlignWithTophat.scala \
	    -i $1 \
	    --annotations ${ANNOTATIONS} \
	    --library_type ${LIBRARY_TYPE} \
	    -tophat ${PATH_TO_TOPHAT} \
	    -outputDir ${RAW_BAM_OUTPUT}/ \
	    -samtools ${PATH_TO_SAMTOOLS} \
	    --tophat_threads ${NBR_OF_THREADS} \
	    -jobRunner ${JOB_RUNNER} \
	    -jobNative "${JOB_NATIVE_ARGS}" \
	    --job_walltime 518400 \
	    -run \
	    ${DEBUG} >> ${LOGS}/alignWithTophat.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from AlignWithBwa. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi
    
    echo "${RAW_BAM_OUTPUT}/${PROJECT_NAME}.cohort.list"
}

#------------------------------------------------------------------------------------------
# Align fastq files using tophat.
#------------------------------------------------------------------------------------------

function RNA_QC {
    source piper -S ${SCRIPTS_DIR}/RNAQC.scala \
	    -i $1 \
	    --project_id ${PROJECT_ID} \
		-R ${GENOME_REFERENCE} \
	    --transcripts ${ANNOTATIONS} \
	    --rRNA_targets ${RNA_RNA_TARGETS} \
	    -outputDir ${RNA_QC_OUTPUT}/ \
	    -jobRunner ${JOB_RUNNER} \
	    -jobNative "${JOB_NATIVE_ARGS}" \
	    --job_walltime 259200 \
	    -run \
	    ${DEBUG} >> ${LOGS}/rnaQC.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from RNAQC. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi
    
    echo "RNA_QC does not have a output!"
}


# We also need the correct java engine and R version
module load java/sun_jdk1.6.0_18
module load R/2.15.0
module load bioinfo-tools
module load samtools/0.1.18
module load tophat/2.0.4

#---------------------------------------------
# Run template - setup which files to run etc
#---------------------------------------------

PIPELINE_SETUP_XML="src/test/resources/testdata/pipelineSetup.xml"
PROJECT_NAME="TestRNA"
PROJECT_ID="a2009002"
GENOME_REFERENCE=${GATK_BUNDLE}"/human_g1k_v37.fasta"
# Note that it's important that the last / is included in the root dir path
PROJECT_ROOT_DIR="/proj/a2009002/private/nobackup/testingRNASeqPipeline/SnpSeqPipeline/fastqs_with_adaptors_trimmed/"
ANNOTATIONS="/proj/a2009002/SnpSeqPipeline/Homo_sapiens/Ensembl/GRCh37/Annotation/Genes/genes.gtf"
RNA_RNA_TARGETS=""
LIBRARY_TYPE="fr-secondstrand"

#---------------------------------------------
# The actual running of the script
# Modify this if you want to chain the parts
# in a different way.
#---------------------------------------------

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

ALIGN_OUTPUT=$(alignWithTophat ${PIPELINE_SETUP_XML})
RNA_QC_OUTPUT=$(RNA_QC ${ALIGN_OUTPUT})

# Perform final clean up
final_clean_up
