#!/bin/bash

#Load needed module for build on uppmax
module load java/sun_jdk1.6.0_04
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

git clone https://github.com/broadgsa/gatk-protected.git gatk-protected
check_errs $? "git clone FAILED"

cd gatk-protected

# Validated gatk-version
git checkout 5e89f01e106cc916e088d1ab43e66321f133b34c
check_errs $? "git checkout FAILED"

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
sbt package
