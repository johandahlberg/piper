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

function call_variants {
    source piper -S ${SCRIPTS_DIR}/RNAVariantCalling.scala \
	    -i $1 \
	    --reference ${HG19} \
	    --project ${PROJECT_NAME} \
	    -intervals ${CUSTOM_EXOME} \
	    --dbsnp ${DBSNP} \
	    --vep_path ${VEP} \
	    --mapability_50mer ${Mapability50mer} \
            --mapability_100mer ${Mapability100mer} \
	    --COSMIC_1 ${COSMIC_1} \
	    --COSMIC_2 ${COSMIC_2} \
	    --GENOMIC_SUPER_DUPS ${GENOMIC_SUPER_DUPS} \
	    --SELFCHAIN ${SELFCHAIN} \
	    --ESP_ESP6500SI_V2 ${ESP_ESP6500SI_V2} \
	    --RNA_EDITING ${RNA_EDITING} \
	    --REPEATMASKER ${REPEATMASKER} \
	    --extra_indels ${KNOWN_INDELS} \
	    -outputDir ${PROCESSED_BAM_OUTPUT}/ \
	    --nbr_of_threads 8 \
	    --scatter_gather 1 \
	    -jobRunner ${JOB_RUNNER} \
	    -jobNative "${JOB_NATIVE_ARGS}" \
	    -gv graph.dot \
	    -run \
	    --job_walltime 86400 \
	    ${DEBUG} >> ${LOGS}/rnaVariantCalling.log  2>&1


    # Check the script exit status, and if it did not finish, clean up and exit
    if [ $? -ne 0 ]; then 
	    echo "Caught non-zero exit status from call_variants. Cleaning up and exiting..."
	    clean_up
	    exit 1
    fi    
}


# We also need the correct java engine and R version
module load java/sun_jdk1.6.0_18
module load R/2.15.0

#---------------------------------------------
# Run template - setup which files to run etc
#---------------------------------------------

COHORT_FILE="cohort.list"
PROJECT_NAME="rna_all"
PROJECT_ID="a2009002"
GENOME_REFERENCE=${GATK_BUNDLE}"/human_g1k_v37.fasta"
# Note that it's important that the last / is included in the root dir path
QOS="--qos=seqver" # e.g. --qos=seqver

#---------------------------------------------
# The actual running of the script
# Modify this if you want to chain the parts
# in a different way.
#---------------------------------------------

# Loads the global settings. To change them open globalConfig.sh and rewrite them.
source globalConfig.sh

# -----------------------
# Pers custom annotations
# -----------------------
VEP=/lynx/cvol/v24/b2012073/private/bin/VEP
TABIX=/bubo/home/h2/anderslu/b2012073/private/bin/tabix-0.2.6/
VCFTOOLS=/bubo/home/h2/anderslu/b2012073/private/bin/vcftools_0.1.10/perl/

export PERL5LIB=$VCFTOOLS

##General reference data
REF_DATA=/proj/b2012073/private/nobackup/references
HG19=/bubo/nobackup/uppnex/reference/biodata/GATK/ftp.broadinstitute.org/bundle/2.2/b37/human_g1k_v37.fasta
#DBSNP=$REF_DATA/dbsnp_135.hg19.sort.vcf
DBSNP=/proj/b2012073/private/nobackup/devel/perw/ALL_RNA_seq/reference_data/dbsnp_137.b37.vcf

##Reference data
##from Johan 
KNOWN_INDELS=/bubo/nobackup/uppnex/reference/biodata/GATK/ftp.broadinstitute.org/bundle/2.2/b37/Mills_and_1000G_gold_standard.indels.b37.vcf
##Exome set created for ALL RNA-seq project
CUSTOM_EXOME=/proj/b2012073/private/nobackup/references/SNP_calling_RNA_seq/Ensembl_RefSeq_UCSC_genes_sno_miRNA_sort_merge.bed
##mårtens WGS data 
ALL_WGS=/proj/b2012073/private/nobackup/references/ALL_WGS/06_274_sorted_realign_recal_duprm.filter30.sorted.gr2.bwa.bam.GATK.SNPs.vcf
##Exome_db (index by tabix)
ESP_ESP6500SI_V2=/proj/b2012073/private/nobackup/references/Exome_Variant_Server/ESP6500SI-V2.ALL.snps_withoutIndels_without_rs_snp.bed.gz
##Cosmic_V64 (two files..., index by tabix)
COSMIC_1=/proj/b2012073/private/nobackup/references/Cosmic_db/v64/CosmicCodingMuts_v64_26032013_noLimit_wgs.vcf.gz
COSMIC_2=/proj/b2012073/private/nobackup/references/Cosmic_db/v64/CosmicCodingMuts_v64_02042013_noLimit.vcf.gz
##Mapability (Works with VEP script)
Mapability50mer=/proj/b2012073/private/nobackup/references/wgEncodeCrgMapabilityAlign50mer.bg.gz
Mapability75mer=/proj/b2012073/private/nobackup/references/wgEncodeCrgMapabilityAlign75mer.bg.gz
Mapability100mer=/proj/b2012073/private/nobackup/references/wgEncodeCrgMapabilityAlign100mer.bg.gz
#Data from UCSC table browser (have created index with tabix)
DGV_STRUCT_VAR_DB=/proj/b2012073/private/nobackup/references/SNP_calling_RNA_seq/RNA_SNP_annotation_tracks/DGV_Struct_Var_db_mod.bed.gz
GENOMIC_SUPER_DUPS=/proj/b2012073/private/nobackup/references/SNP_calling_RNA_seq/RNA_SNP_annotation_tracks/genomicSuperDups_mod.bed.gz
REPEATMASKER=/proj/b2012073/private/nobackup/references/SNP_calling_RNA_seq/RNA_SNP_annotation_tracks/RepeatMasker_mod.bed.gz
SELFCHAIN=/proj/b2012073/private/nobackup/references/SNP_calling_RNA_seq/RNA_SNP_annotation_tracks/SelfChain_mod.bed.gz
RNA_EDITING=/proj/b2012073/private/nobackup/references/SNP_calling_RNA_seq/RNA_SNP_annotation_tracks/Human_RNA_editing_mod.bed.gz

#------------------
# Run the function
#------------------

call_variants ${COHORT_FILE}

# Perform final clean up
final_clean_up
