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
  GATK_INSTALL_DIR="gatk-protected"

  if [ ! -d "$GATK_INSTALL_DIR" ]; then
    echo "Cloning..."
    git clone https://github.com/broadgsa/gatk-protected.git $GATK_INSTALL_DIR
    check_errs $? "git clone FAILED"
  fi

  cd $GATK_INSTALL_DIR

  # Validated gatk-version
  git checkout eee94ec81f721044557f590c62aeea6880afd927
  check_errs $? "git checkout FAILED"

  mvn package
  check_errs $? "gatk compilation FAILED"
  mkdir ../lib
  cp target/* ../lib/
  cd ..
  rm -rf $GATK_INSTALL_DIR
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
echo "Copy workflows, qscripts and globalConfig to install location"
echo "########################################################"
cp -rv workflows $INSTALL_PREFIX/
check_errs $? "copying workflows failed."

cp -rv --dereference qscripts $INSTALL_PREFIX/
check_errs $? "copying qscripts failed."

cp -rv globalConfig.* uppmax_global_config.xml $INSTALL_PREFIX/workflows/
check_errs $? "copying globalConfig.sh failed."

cp -rv globalConfig.xml $INSTALL_PREFIX/workflows/
check_errs $? "copying globalConfig.xml failed."

# Red text - making people notice instructions since pre-school!
coloured_text() {
  echo -e "\e[1;31m ${1} \e[0m"
}

echo "########################################################"
echo "Piper successfully installed to ${INSTALL_PREFIX}"
echo "Add it to your PATH by running:"
coloured_text "  PATH=\$PATH:$INSTALL_PREFIX/bin"
echo "And verify it's been installed by running:"
coloured_text "  piper -S qscripts/examples/NonUppmaxableTestScript.scala --help"
echo "This should show a list of available options if Piper"
echo "has been installed correctly."
echo "To access to workflows from any folder, add them to your path:"
coloured_text " PATH=\$PATH:$INSTALL_PREFIX/workflows/"
echo "If you want to run on a cluster you need to make sure "
echo "that the appropriate libraries are available on the path."
echo "On Uppmax this is done by exporting: "
coloured_text '  export LD_LIBRARY_PATH=/sw/apps/build/slurm-drmaa/default/lib/:$LD_LIBRARY_PATH'
echo "On other systems you need to figure out the path to the slurm-drmaa libraries, "
echo "and add it in the same way."
echo "To make sure you can always reach piper, add run following commands to "
echo "add it to you .bashrc: "
coloured_text "  echo \"\" >>  ~/.bashrc"
coloured_text "  echo \"# Piper related variables and setup\" >>  ~/.bashrc"
coloured_text "  echo 'PATH=\$PATH:$INSTALL_PREFIX/bin' >> ~/.bashrc"
coloured_text "  echo 'PATH=\$PATH:$INSTALL_PREFIX/workflows' >> ~/.bashrc"
coloured_text "  echo 'export LD_LIBRARY_PATH=/sw/apps/build/slurm-drmaa/default/lib/:\$LD_LIBRARY_PATH' >> ~/.bashrc"
coloured_text "  echo 'export PIPER_GLOB_CONF=$INSTALL_PREFIX/workflows/globalConfig.sh' >> ~/.bashrc" 
echo "########################################################"

