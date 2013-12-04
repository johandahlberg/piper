#!/bin/bash -l
#SBATCH -A a2009002
#SBATCH -p core
#SBATCH -n 1
#SBATCH -t 120:00:00
#SBATCH -J piper
#SBATCH -o pipeline-%j.out
#SBATCH -e pipeline-%j.error

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

PIPELINE_SETUP=$1

if [ "$2" == "run" ]; then
    RUN="-run"
fi

# We also need the correct java engine and R version
module load java/sun_jdk1.7.0_25
module load R/2.15.0

#---------------------------------------------
# Create output directories
#---------------------------------------------
if [ ! -d "${LOGS}" ]; then
   mkdir -p ${LOGS}
fi

#---------------------------------------------
# Run qscript
#---------------------------------------------
source piper -S ${SCRIPTS_DIR}/DNABestPracticeVariantCalling.scala \
	     --xml_input ${PIPELINE_SETUP} \
	     --dbsnp ${DB_SNP_B37} \
             --extra_indels ${MILLS_B37} \
             --extra_indels ${ONE_K_G_B37} \
	     --hapmap ${HAPMAP_B37} \
	     --omni ${OMNI_B37} \
	     --mills ${MILLS_B37} \
	     -bwa ${PATH_TO_BWA} \
	     -samtools ${PATH_TO_SAMTOOLS} \
	     --number_of_threads 8 \
             --scatter_gather 23 \
	     -jobRunner ${JOB_RUNNER} \
             -jobNative "${JOB_NATIVE_ARGS}" \
	     --job_walltime 345600 \
	     ${RUN} ${DEBUG} 2>&1 | tee -a ${LOGS}/wholeGenome.log

# Perform final clean up
final_clean_up
