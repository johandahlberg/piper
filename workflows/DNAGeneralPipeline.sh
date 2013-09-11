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
function alignWithBwa {
    source piper -S ${SCRIPTS_DIR}/AlignWithBWA.scala \
			    -i $1 \
			    -outputDir ${RAW_BAM_OUTPUT}/ \
			    -bwa ${PATH_TO_BWA} \
			    -samtools ${PATH_TO_SAMTOOLS} \
			    --bwa_threads ${NBR_OF_THREADS} \
	            -jobRunner ${JOB_RUNNER} \
        		-jobNative "${JOB_NATIVE_ARGS}" \
			    --job_walltime 345600 \
			    -run \
			    ${DEBUG} >> ${LOGS}/alignWithBwa.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from AlignWithBwa. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi

    echo "${RAW_BAM_OUTPUT}/${PROJECT_NAME}.cohort.list"
}

#------------------------------------------------------------------------------------------
# NOTE: These parts of the analysis does not yet suport the xml based setup.
#       Running them will require manually setting up path etc.
#------------------------------------------------------------------------------------------

#------------------------------------------------------------------------------------------
# Merge bam files by sample name in read group
#------------------------------------------------------------------------------------------
function mergeBySampleName {
    source piper -S ${SCRIPTS_DIR}/MergeBamsBySample.scala \
                            -i $1 \
                            -outputDir ${RAW_MERGED_BAM_OUTPUT}/ \
			    --project ${PROJECT_NAME} \
                    -jobRunner ${JOB_RUNNER} \
                        -jobNative "${JOB_NATIVE_ARGS}" \
                            --job_walltime 86400 \
                            -run \
                            ${DEBUG} >> ${LOGS}/mergeBySampleName.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then
            echo "Caught non-zero exit status from merge by sample. Cleaning up and exiting..."
            clean_up
            exit 1
    fi

    echo "${RAW_MERGED_BAM_OUTPUT}/${PROJECT_NAME}.cohort.list"
}



#------------------------------------------------------------------------------------------
# CalculateCoverage of bam-files
#------------------------------------------------------------------------------------------
function alignmentQC {
    source piper -S ${SCRIPTS_DIR}/AlignmentQC.scala \
			    -i $1 \
    			-R ${GENOME_REFERENCE} \
    			--project_id ${PROJECT_ID} \
 			    -intervals ${INTERVALS} \
			    -outputDir ${ALIGNMENT_QC_OUTPUT}/ \
			    -nt ${NBR_OF_THREADS} \
	            -jobRunner ${JOB_RUNNER} \
        		-jobNative "${JOB_NATIVE_ARGS}" \
			    --job_walltime 345600 \
			    -run \
			    ${DEBUG} >> ${LOGS}/alignmentQC.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from alignmentQC. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi

    echo "${RAW_BAM_OUTPUT}/${PROJECT_NAME}.cohort.list"
}


#------------------------------------------------------------------------------------------
# Data preprocessing
#------------------------------------------------------------------------------------------
function dataPreprocessing {

    source piper -S ${SCRIPTS_DIR}/DataProcessingPipeline.scala \
			      -R ${GENOME_REFERENCE} \
			      --project ${PROJECT_NAME} \
			      -i $1 \
			      -outputDir ${PROCESSED_BAM_OUTPUT}/ \
        		  --dbsnp ${DB_SNP_B37} \
                  --extra_indels ${MILLS_B37} \
          		  --extra_indels ${ONE_K_G_B37} \
			      -intervals ${INTERVALS} \
			      -cm USE_SW \
			      -run \
		          -jobRunner ${JOB_RUNNER} \
         	      -jobNative "${JOB_NATIVE_ARGS}" \
			      --job_walltime 864000 \
			      -nt ${NBR_OF_THREADS} \
			      ${DEBUG} >> ${LOGS}/dataPreprocessing.log  2>&1

    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
            echo "Caught non-zero exit status from DataProcessingPipeline. Cleaning up and exiting..."
            clean_up
            exit 1
    fi
    
    echo "${PROCESSED_BAM_OUTPUT}/${PROJECT_NAME}.cohort.list"

}

#------------------------------------------------------------------------------------------
# Variant calling
#------------------------------------------------------------------------------------------

function variantCalling {

    source piper -S ${SCRIPTS_DIR}/VariantCalling.scala \
			      -R ${GENOME_REFERENCE} \
			      -res ${GATK_BUNDLE_B37} \
			      --project ${PROJECT_NAME} \
			      -i $1 \
			      -intervals ${INTERVALS} \
			      -outputDir ${VCF_OUTPUT}/ \
			      -run \
		          -jobRunner ${JOB_RUNNER} \
                  -jobNative "${JOB_NATIVE_ARGS}" \
			      --job_walltime 36000 \
			      -nt  ${NBR_OF_THREADS} \
			      -retry 2 \
			      ${DEBUG} >> ${LOGS}/variantCalling.log  2>&1

    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
            echo "Caught non-zero exit status from VariantCalling. Cleaning up and exiting..."
            clean_up
            exit 1
    fi
    
    echo "${VCF_OUTPUT}/${PROJECT_NAME}.cohort.list"
}

# We also need the correct java engine and R version
module load java/sun_jdk1.6.0_45
module load R/2.15.0

#---------------------------------------------
# Run template - setup which files to run etc
#---------------------------------------------

# WARNING
# This script is mainly keept as a template for creating new 
# pipelines for specific protocols.
# That means that there might be additional setup required to
# run this script.

PIPELINE_SETUP_XML="src/test/resources/testdata/newPipelineSetupSameSampleAcrossMultipleLanes.xml"
PROJECT_NAME="TestProject"
PROJECT_ID="a2009002"
# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh
INTERVALS=""
GENOME_REFERENCE=${GATK_BUNDLE_B37}"/human_g1k_v37.fasta"
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

if [ ! -d "${RAW_MERGED_BAM_OUTPUT}" ]; then
   mkdir -p ${RAW_MERGED_BAM_OUTPUT}
fi

if [ ! -d "${ALIGNMENT_QC_OUTPUT}" ]; then
   mkdir -p ${ALIGNMENT_QC_OUTPUT}
fi

if [ ! -d "${PROCESSED_BAM_OUTPUT}" ]; then
   mkdir -p ${PROCESSED_BAM_OUTPUT}
fi

if [ ! -d "${VCF_OUTPUT}" ]; then
   mkdir -p ${VCF_OUTPUT}
fi


#---------------------------------------------
# The actual running of the script
# Modify this if you want to chain the parts
# in a different way.
#---------------------------------------------

ALIGN_OUTPUT=$(alignWithBwa ${PIPELINE_SETUP_XML})
MERGED_BAMS_OUTPUT=$(mergeBySampleName ${ALIGN_OUTPUT})
ALIGN_QC_OUTPUT=$(alignmentQC ${MERGED_BAMS_OUTPUT})
DATAPROCESSING_OUTPUT=$(dataPreprocessing ${MERGED_BAMS_OUTPUT})
VARIANTCALLING_OUTPUT=$(variantCalling ${DATAPROCESSING_OUTPUT})

# Perform final clean up
final_clean_up
