#!/bin/bash -l
#SBATCH -A a2009002
#SBATCH -p core
#SBATCH -n 1
#SBATCH -t 120:00:00
#SBATCH -J piper
#SBATCH -o pipeline-%j.out
#SBATCH -e pipeline-%j.error



function usage {
   echo "Usage: ./workflows/Haloplex.sh --xml_input <setup.xml> --intervals <regions file> --amplicons <amplicon file> [--alignments_only] [--run]"
}

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

#---------------------------------------------
# Parse the arguments
#---------------------------------------------
PIPELINE_SETUP=""
RUN=""
ONLY_ALIGMENTS=""
INTERVALS="" # Your design bed-file
AMPLICONS="" # The amplicon design file

OUTPUT_DIR="pipeline_output/haloplex"

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
           -r | --run)
               RUN="-run"
               shift
               ;;
           -a | --alignments_only)
               ONLY_ALIGMENTS="--onlyAlignments"
               shift
               ;; 
           -i | --intervals)
               INTERVALS=$2
               shift 2
               ;;     
	   -a | --amplicons)
	       AMPLICONS=$2
               shift 2
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

if [ ! "$PIPELINE_SETUP" ] || [ ! "$INTERVALS" ] || [ ! "$AMPLICONS" ]; then
   usage
   exit 1
fi

# We also need the correct java engine and R version
module load java/sun_jdk1.7.0_25
module load R/2.15.0

mkdir -p $OUTPUT_DIR
mkdir -p ${LOGS}

source piper -S ${SCRIPTS_DIR}/Haloplex.scala \
	     --xml_input ${PIPELINE_SETUP} \
	     --resources ${GATK_BUNDLE_B37} \
	     -intervals ${INTERVALS} \
	     --amplicons ${AMPLICONS} \
	     -outputDir ${OUTPUT_DIR}/ \
	     -bwa ${PATH_TO_BWA} \
	     -samtools ${PATH_TO_SAMTOOLS} \
	     -cutadapt ${PATH_TO_CUTADAPT} \
	     --nbr_of_threads ${NBR_OF_THREADS} \
	     -jobRunner ${JOB_RUNNER} \
             -jobNative "${JOB_NATIVE_ARGS}" \
	     --job_walltime 36000 \
	     -sg 1 \
	     ${RUN} ${ONLY_ALIGNMENTS} ${DEBUG} 2>&1 | tee ${LOGS}/haloplex.log

# Perform final clean up
final_clean_up

