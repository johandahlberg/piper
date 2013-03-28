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
module load bwa/0.6.2
module load samtools/0.1.18

#---------------------------------------------
# Run template - setup which files to run etc
#---------------------------------------------

PIPELINE_SETUP_XML="pipelineSetup.xml"
PROJECT_NAME="LC-0045"
PROJECT_ID="a2009002"
# Note that it's important that the last / is included in the root dir path
PROJECT_ROOT_DIR="/proj/a2009002/private/nobackup/OUTBOX/LC-0045/analysis/piper/"
INTERVALS=""

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

#TODO Fix mechanism for setting walltimes.

#------------------------------------------------------------------------------------------
# Align fastq files using bwa - outputs bam files.
#------------------------------------------------------------------------------------------
source piper -S ${SCRIPTS_DIR}/AlignWithBWA.scala \
			-i ${PIPELINE_SETUP_XML} \
			-outputDir ${RAW_BAM_OUTPUT}/ \
			-bwa ${PATH_TO_BWA} \
			-samtools ${PATH_TO_SAMTOOLS} \
			-bwape \
			--bwa_threads ${NBR_OF_THREADS} \
		        -jobRunner ${JOB_RUNNER} \
      			-jobNative "${JOB_NATIVE_ARGS}" \
			--job_walltime 345600 \
			-run \
			${DEBUG}


# Check the script exit status, and if it did not finish, clean up and exit
if [ $? -ne 0 ]; then 
	echo "Caught non-zero exit status from AlignWithBwa. Cleaning up and exiting..."
	clean_up
	exit 1
fi

#------------------------------------------------------------------------------------------
# NOTE: These parts of the analysis does not yet suport the xml based setup.
#       Running them will require manually setting up path etc.
#------------------------------------------------------------------------------------------

#------------------------------------------------------------------------------------------
# Data preprocessing
#------------------------------------------------------------------------------------------
source piper -S ${SCRIPTS_DIR}/DataProcessingPipeline.scala \
			  -R ${GENOME_REFERENCE} \
			  --project ${PROJECT_NAME} \
			  -i ${RAW_BAM_OUTPUT}/${PROJECT_NAME}.cohort.list \
			  -outputDir ${PROCESSED_BAM_OUTPUT}/ \
			  --dbsnp ${DB_SNP} \
	                  --extra_indels ${MILLS} \
             		  --extra_indels ${ONE_K_G} \
			  -bwa ${PATH_TO_BWA} \
			   --use_bwa_pair_ended \
                          --realign \
                          --fixMatePairInformation \
			  -intervals ${INTERVALS} \
			  -cm USE_SW \
			  -run \
		          -jobRunner ${JOB_RUNNER} \
     			  -jobNative "${JOB_NATIVE_ARGS}" \
			  --job_walltime 864000 \
			  -nt ${NBR_OF_THREADS} \
			  ${DEBUG}

# Check the script exit status, and if it did not finish, clean up and exit
if [ $? -ne 0 ]; then 
        echo "Caught non-zero exit status from DataProcessingPipeline. Cleaning up and exiting..."
        clean_up
        exit 1
fi

#------------------------------------------------------------------------------------------
# Run variant calling
#------------------------------------------------------------------------------------------
source piper -S ${SCRIPTS_DIR}/VariantCalling.scala \
			  -R ${GENOME_REFERENCE} \
			  -res ${GATK_BUNDLE} \
			  --project ${PROJECT_NAME} \
			  -i ${PROCESSED_BAM_OUTPUT}/${PROJECT_NAME}.cohort.list \
			  -intervals ${INTERVALS} \
			  -outputDir ${VCF_OUTPUT}/ \
			  -run \
		          -jobRunner ${JOB_RUNNER} \
         		  -jobNative "${JOB_NATIVE_ARGS}" \
			  --job_walltime 3600 \
			  -nt  ${NBR_OF_THREADS} \
			  -retry 2 \
			  ${DEBUG}

# Check the script exit status, and if it did not finish, clean up and exit
if [ $? -ne 0 ]; then 
        echo "Caught non-zero exit status from VariantCalling. Cleaning up and exiting..."
        clean_up
        exit 1
fi

# Perform final clean up
final_clean_up
