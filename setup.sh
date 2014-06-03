#!/bin/bash -l

########################################################
# Utility script to download dependencies and install
# Piper
# Usage:
#  /setup.sh <path to wher Piper should be installed>
# Default install location is $HOME/Bin/Piper
########################################################

INSTALL_PREFIX=$1

if [ ! -n "$1" ];
then
  INSTALL_PREFIX="$HOME/Bin/Piper"
fi

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

download_and_install_gatk()
{
  git clone https://github.com/broadgsa/gatk-protected.git gatk-protected
  check_errs $? "git clone FAILED"

  cd gatk-protected

  # Validated gatk-version
  git checkout 72492bb87544de91522cfa52ad16610a9ff85445
  check_errs $? "git checkout FAILED"

  mvn package
  check_errs $? "gatk compilation FAILED"
  mkdir ../lib
  cp target/* ../lib/
  cd ..
  rm -rf gatk-protected
}

echo "########################################################"
echo "Setting up and installing Piper to: ${INSTALL_PREFIX}"
echo "########################################################"

#Load needed module for build on uppmax
module load java/sun_jdk1.7.0_25

if (( $? == 0 )); then
  echo "Used module system to load Java (sun_jdk1.7.0_25)."
else
  echo "Couldn\'t load Java using a module system - but don't worry."
  echo "As long as you have Java installed (preferably sun_jdk1.7.0_25)"
  echo "you should be find anyway."
fi

echo "########################################################"
echo "Checking out and compiling the GATK and Queue"
echo "########################################################"

download_and_install_gatk

echo "########################################################"
echo "Download RNA-SeQC"
echo "########################################################"

wget http://www.broadinstitute.org/cancer/cga/sites/default/files/data/tools/rnaseqc/RNA-SeQC_v1.1.7.jar --directory-prefix=resources/ --no-clobber
check_errs $? "wget RNA-SeQC FAILED"

echo "########################################################"
echo "Compile, package and install Piper"
echo "########################################################"

sbt/bin/sbt pack && make -C target/pack/ install PREFIX=$INSTALL_PREFIX
check_errs $? "compiling and install piper failed."

echo "########################################################"
echo "Piper successfully installed to ${INSTALL_PREFIX}"
echo "Add it to your PATH by running:"
echo "  PATH=\$PATH:$INSTALL_PREFIX/bin"
echo "And verify it's been installed by running:"
echo "  piper -S qscripts/examples/NonUppmaxableTestScript.scala --help"
echo "This should show a list of available options if Piper"
echo " has been installed correctly."
echo "########################################################"

