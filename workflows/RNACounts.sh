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

function usage {
   echo "Usage: ./workflows/RNACounts.sh --xml_input <setup.xml> --library_type <fr-secondstrand/fr-firststrand/unstranded> [--alignments_only] [--run]"
}

#---------------------------------------------
# Parse the arguments
#---------------------------------------------
PIPELINE_SETUP=""
RUN=""
ONLY_ALIGNMENTS=""
LIBRARY_TYPE=""

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
#ANNOTATIONS="/data/test_data/piper_references/Homo_sapiens/Ensembl/GRCh37/Annotation/Genes/genes.gtf"
RRNA_TARGETS="/proj/b2010028/references/piper_references/rRNA_targets/rRNA.sorted.1-based.intervals.list"
#RRNA_TARGETS="/local/test_data/piper_references/rRNA_targets/rRNA.sorted.1-based.intervals.list"

# We also need the correct java engine and R version
module load java/sun_jdk1.7.0_25
module load R/2.15.0
module load bioinfo-tools
module load tophat/2.0.10
module load samtools/0.1.19
module load bowtie2/2.1.0

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

if [ ! -d "${CUFFLINKS_OUTPUT}" ]; then
   mkdir -p ${CUFFLINKS_OUTPUT}
fi

#---------------------------------------------
# The actual running of the script
# Modify this if you want to chain the parts
# in a different way.
#---------------------------------------------

piper -S ${SCRIPTS_DIR}/RNACounts.scala \
	    --xml_input $PIPELINE_SETUP \
	    --transcripts ${ANNOTATIONS} \
	    --annotations ${ANNOTATIONS} \
	    --mask ${RRNA_TARGETS} \
	    --library_type ${LIBRARY_TYPE} \
	    --rRNA_targets ${RRNA_TARGETS} \
	    --downsample 1000 \
	    --path_to_tophat ${PATH_TO_TOPHAT} \
	    --path_to_cufflinks ${PATH_TO_CUFFLINKS} \
	    --bam_output_directory ${RAW_BAM_OUTPUT} \
	    --qc_output_directory ${RNA_QC_OUTPUT} \
	    --cufflink_output_directory ${CUFFLINKS_OUTPUT} \
	    --tophat_threads ${NBR_OF_THREADS} \
	    --path_to_cutadapt ${PATH_TO_CUTADAPT} \
	    --path_sync_script ${PATH_TO_SYNC_CUTADAPT} \
	    --rna_seqc ${PATH_TO_RNA_SEQ_QC} \
	    -jobRunner ${JOB_RUNNER} \
	    -jobNative ${JOB_NATIVE_ARGS} \
	     ${ONLY_ALIGNMENTS} \
	    -ca \
	    --job_walltime 518400 \
	    ${RUN} ${DEBUG} 2>&1 | tee -a ${LOGS}/RNACount.log    

# Perform final clean up
final_clean_up
