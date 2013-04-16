git clone https://github.com/broadgsa/gatk-protected.git gatk-protected
cd gatk-protected

# Validated gatk-version
git checkout 5e89f01e106cc916e088d1ab43e66321f133b34c

ant
mkdir ../lib
cp dist/* ../lib/
cd ..
rm -rf gatk-protected

# Get RNA-SeQC
wget http://www.broadinstitute.org/cancer/cga/sites/default/files/data/tools/rnaseqc/RNA-SeQC_v1.1.7.jar --directory-prefix=resources/

# Uncomment this to run tests after setup
# sbt test
sbt package
