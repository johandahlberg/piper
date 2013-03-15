git clone https://github.com/broadgsa/gatk-protected.git gatk-protected
cd gatk-protected

# Validated gatk-version
git checkout 5e89f01e106cc916e088d1ab43e66321f133b34c

ant
mkdir ../lib
cp dist/* ../lib/
cd ..
rm -rf gatk-protected

# Uncomment this to run tests after setup
# sbt test
