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

function cuffdiff {
    source piper -S ${SCRIPTS_DIR}/Cuffdiff.scala \
            -i $1 \
            --reference ${GENOME_REFERENCE} \
            --annotations ${ANNOTATIONS} \
            --replicates ${REPLICATES} \
            --library_type ${LIBRARY_TYPE} \
            --path_to_cuffdiff ${PATH_TO_CUFFDIFF} \
            -outputDir ${CUFFDIFF_OUTPUT}/ \
            --threads ${NBR_OF_THREADS} \
            --project_id ${PROJECT_ID} \
            -jobRunner ${JOB_RUNNER} \
            -jobNative "${JOB_NATIVE_ARGS}" \
            --job_walltime 259200 \
            -run \
            ${DEBUG} >> ${LOGS}/cuffdiff.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then
            echo "Caught non-zero exit status from cuffdiff. Cleaning up and exiting..."
            clean_up
            exit 1
    fi

    echo "1"
}


#------------------------------------------------------------------------------------------
# Run QC with RNA_QC
#------------------------------------------------------------------------------------------

function RNA_QC {
    source piper -S ${SCRIPTS_DIR}/RNAQC.scala \
	    -i $1 \
	    --downsample 1000 \
	    --project_id ${PROJECT_ID} \
		-R ${GENOME_REFERENCE} \
	    --transcripts ${ANNOTATIONS} \
	    --rRNA_targets ${RRNA_TARGETS} \
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

#---------------------------------------------
# Run template - setup which files to run etc
#---------------------------------------------

PIPELINE_SETUP_XML="src/test/resources/testdata/exampleForNewSetupXML.xml"
PROJECT_NAME="TestRNA"
PROJECT_ID="b2010028"
# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh
GENOME_REFERENCE=${GATK_BUNDLE}"/human_g1k_v37.fasta"
ANNOTATIONS="/proj/b2010028/references/piper_references/Homo_sapiens/Ensembl/GRCh37/Annotation/Genes/genes.gtf"
RRNA_TARGETS="/proj/b2010028/references/piper_references/rRNA_targets/rRNA.sorted.1-based.intervals.list"
LIBRARY_TYPE="" # Depends on the protocol, e.g. fr-secondstrand for ScriptSeq
# If you have replicates in you cohort specify them in a file accoring to the following
# On each line should be the label (e.g. the name of the condition)
# and sample names of the samples included in that condition seperated by
# tabs. Please note that only samples which have replicates need to be specified.
# The default is one sample - one replicate
REPLICATES=""
QOS="" # e.g. --qos=seqver


#---------------------------------------------
# Create output directories
#---------------------------------------------

if [ ! -d "${LOGS}" ]; then
   mkdir -p ${LOGS}
fi

if [ ! -d "${RAW_BAM_OUTPUT}" ]; then
   mkdir -p ${RAW_BAM_OUTPUT}
fi

if [ ! -d "${RNA_QC_OUTPUT}" ]; then
   mkdir -p ${RNA_QC_OUTPUT}
fi

if [ ! -d "${CUFFDIFF_OUTPUT}" ]; then
   mkdir -p ${CUFFDIFF_OUTPUT}
fi

#---------------------------------------------
# The actual running of the script
# Modify this if you want to chain the parts
# in a different way.
#---------------------------------------------

ALIGN_OUTPUT=$(alignWithTophat ${PIPELINE_SETUP_XML})
RNA_QC_OUTPUT=$(RNA_QC ${ALIGN_OUTPUT})
CUFFDIFF_OUT=$(cuffdiff ${ALIGN_OUTPUT})

# Perform final clean up
final_clean_up
