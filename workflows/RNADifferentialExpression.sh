#!/bin/bash -l
#SBATCH -A a2009002
#SBATCH -p core
#SBATCH -n 1
#SBATCH -t 120:00:00
#SBATCH -J piper
#SBATCH -o pipeline-%j.out
#SBATCH -e pipeline-%j.error

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
	    --xml_input $PIPELINE_SETUP \
	    --annotations ${ANNOTATIONS} \
	    --library_type ${LIBRARY_TYPE} \
	    -tophat ${PATH_TO_TOPHAT} \
	    -outputDir ${RAW_BAM_OUTPUT}/ \
	    -samtools ${PATH_TO_SAMTOOLS} \
	    --tophat_threads ${NBR_OF_THREADS} \
	    -jobRunner ${JOB_RUNNER} \
	    -jobNative "${JOB_NATIVE_ARGS}" \
	    --job_walltime 518400 \
	    ${RUN} ${DEBUG} 2>&1 | tee -a ${LOGS}/alignWithTophat.log

    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from AlignWithBwa. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi
    
    echo "${RAW_BAM_OUTPUT}/cohort.list"
}

function cuffdiff {
    source piper -S ${SCRIPTS_DIR}/Cuffdiff.scala \
            -i $1 \
	    --xml_input $PIPELINE_SETUP \
            --reference ${GENOME_REFERENCE} \
            --annotations ${ANNOTATIONS} \
            --replicates ${REPLICATES} \
            --library_type ${LIBRARY_TYPE} \
            --path_to_cuffdiff ${PATH_TO_CUFFDIFF} \
            -outputDir ${CUFFDIFF_OUTPUT}/ \
            -jobRunner ${JOB_RUNNER} \
            -jobNative "${JOB_NATIVE_ARGS}" \
            --job_walltime 259200 \
            ${RUN} ${DEBUG} 2>&1 | tee -a ${LOGS}/cuffdiff.log


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
	    --xml_input $PIPELINE_SETUP \
	    --downsample 1000 \
  	    -R ${GENOME_REFERENCE} \
	    --transcripts ${ANNOTATIONS} \
	    --bed_transcripts ${BED_ANNOTATIONS}
	    --rRNA_targets ${RRNA_TARGETS} \
	    -outputDir ${RNA_QC_OUTPUT}/ \
            --path_to_samtools ${PATH_TO_SAMTOOLS} \
	    --path_to_gene_body_coverage_script ${PATH_TO_GENE_COVERAGE_SCRIPT} \
	    -jobRunner ${JOB_RUNNER} \
	    -jobNative "${JOB_NATIVE_ARGS}" \
	    --job_walltime 172800 \
	    ${RUN} ${DEBUG} 2>&1 | tee -a ${LOGS}/rnaQC.log


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from RNAQC. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi
    
    echo "RNA_QC does not have a output!"
}

function usage {
   echo "Usage: ./workflows/RNADifferentialExpression.sh --xml_input <setup.xml> --library_type <fr-secondstrand/fr-firststrand/unstranded> [--replicates <replicate_file>] [--alignments_only] [--run]"
}

#---------------------------------------------
# Parse the arguments
#---------------------------------------------
PIPELINE_SETUP=""
RUN=""
ONLY_ALIGNMENTS=""
LIBRARY_TYPE=""
REPLICATES=""


while :
    do
       case $1 in
           -h | --help | -\?)
               usage
               exit 0
               ;;
           -s | --xml_input)
               PIPELINE_SETUP=$2
               shift 2
               ;;
           -l | --library_type)
               LIBRARY_TYPE=$2
               shift 2
               ;;
           -c | --replicates)
               REPLICATES=$2
               shift 2
               ;;
           -r | --run)
               RUN="-run"
               shift
               ;;
           -a | --alignments_only)
               ONLY_ALIGNMENTS="--onlyAlignments"
               shift
               ;;
           -*)
               echo "WARN: Unknown option (ignored): $1" >&2
               shift
               ;;
           *)  # no more options. Stop while loop
               break
               ;;
       esac
   done

if [ ! "$PIPELINE_SETUP" ]; then
   usage
   exit 1
fi

if [ ! "$LIBRARY_TYPE" ]; then
   usage
   exit 1
fi

#---------------------------------------------
# Run template - setup which files to run etc
#---------------------------------------------

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

GENOME_REFERENCE=${GATK_BUNDLE_B37}"/human_g1k_v37.fasta"
ANNOTATIONS="/proj/b2010028/references/piper_references/Homo_sapiens/Ensembl/GRCh37/Annotation/Genes/genes.gtf"
BED_ANNOTATIONS="/proj/b2010028/references/piper_references/Homo_sapiens/Ensembl/GRCh37/Annotation/Genes/genes.bed"
RRNA_TARGETS="/proj/b2010028/references/piper_references/rRNA_targets/rRNA.sorted.1-based.intervals.list"

# We also need the correct java engine and R version
module load java/sun_jdk1.7.0_25
module load R/2.15.0
module load bioinfo-tools
module load tophat/2.0.4

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

if [ -n "$ONLY_ALIGNMENTS" ]; then
    ALIGN_OUTPUT=$(alignWithTophat ${PIPELINE_SETUP_XML})
else
    ALIGN_OUTPUT=$(alignWithTophat ${PIPELINE_SETUP_XML})
    RNA_QC_OUTPUT=$(RNA_QC ${RAW_BAM_OUTPUT}/cohort.list)
    CUFFDIFF_OUT=$(cuffdiff ${RAW_BAM_OUTPUT}/cohort.list)
fi

# Perform final clean up
final_clean_up
