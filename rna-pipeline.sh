#!/bin/bash -l
#SBATCH -A a2009002
#SBATCH -p core
#SBATCH -n 1
#SBATCH -t 120:00:00
#SBATCH -J snp_seq_pipeline_controller
#SBATCH -o pipeline-%j.out
#SBATCH -e pipeline-%j.error
#SBATCH --qos=seqver

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
# Note that it's important that the last / is included in the root dir path
PROJECT_ROOT_DIR="/proj/a2009002/private/nobackup/testingRNASeqPipeline/SnpSeqPipeline/fastqs_with_adaptors_trimmed/"
ANNOTATIONS="/proj/a2009002/SnpSeqPipeline/Homo_sapiens/Ensembl/GRCh37/Annotation/Genes/genes.gtf"
LIBRARY_TYPE="fr-secondstrand"

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

#------------------------------------------------------------------------------------------
# Align fastq files using tophat.
#------------------------------------------------------------------------------------------
source piper -S ${SCRIPTS_DIR}/AlignWithTophat.scala \
	-i ${PIPELINE_SETUP_XML} \
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
	${DEBUG}


# Check the script exit status, and if it did not finish, clean up and exit
if [ $? -ne 0 ]; then 
	echo "Caught non-zero exit status from AlignWithBwa. Cleaning up and exiting..."
	clean_up
	exit 1
fi

# Perform final clean up
final_clean_up
