#!/bin/bash -l

#Load needed module for build on uppmax
module load java/sun_jdk1.7.0_25
module load ant

check_errs()
{
  # Function. Parameter 1 is the return code
  # Parameter 2 is text to display on failure.
  # Kill all child processes before exit.

  if [ "${1}" -ne "0" ]; then
    echo "ERROR # ${1} : ${2}"
    for job in `jobs -p`
    do
        kill -9 $job
    done
    exit ${1}
  fi
}

#TODO Reset this to the normal broad branch when jobRunnerName patch has been included
# there.
#git clone https://github.com/broadgsa/gatk-protected.git gatk-protected
git clone git@github.com:johandahlberg/gatk-protected.git gatk-protected
check_errs $? "git clone FAILED"

cd gatk-protected

# Validated gatk-version
#git checkout 6bda5696664da40bc2baef4f4cb69e4ef1f86ce5
#check_errs $? "git checkout FAILED"

ant
check_errs $? "gatk compilation FAILED"
mkdir ../lib
cp dist/* ../lib/
cd ..
rm -rf gatk-protected

# Get RNA-SeQC
wget http://www.broadinstitute.org/cancer/cga/sites/default/files/data/tools/rnaseqc/RNA-SeQC_v1.1.7.jar --directory-prefix=resources/
check_errs $? "wget RNA-SeQC FAILED"

# Uncomment this to run tests after setup
# sbt test
sbt/bin/sbt package
