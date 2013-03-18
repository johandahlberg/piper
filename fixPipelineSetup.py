#!/usr/bin/python

import sys 
import os
import getopt
 
def get_immediate_subdirectories(dir):
    return [name for name in os.listdir(dir)
            if os.path.isdir(os.path.join(dir, name))]
 
# Get runfolder name from file and return as list.
def get_runfolders_already_run(file):
	runfolders = list()

	try:
		with open(file) as file:
		       	for line in file:
          			runfolders.append(line.rstrip())
	except IOError as e:
		return runfolders	
        return runfolders
 
def print_sample_folder_entries(runFolder):
        for dir in get_immediate_subdirectories(runFolder):
                if ("Sample_" in dir):
                        sampleName = dir.replace("Sample_","")
                        samplePath = runFolder + "/" + dir
                        print ("\t\t<SampleFolder Name=\"%s\" Path=\"%s/\" Reference=\"%s\"></SampleFolder>" % (sampleName, samplePath,reference))
 
 

def usage():
	print "Useage ./fixPipelineSetup -p <projectName> -i <projectId> -R <referenceSequence> -r <projectRootDir>"

#------------------------------------------------------------------
# Make a list of all runfolders which have already been processed.
#------------------------------------------------------------------

projectName=""
projectId=""
reference=""
rootDir=""

#Parse options
try:
	opts, args = getopt.getopt(sys.argv[1:],"p:i:R:r:", ["projectName=", "projectId=", "reference=", "projectRootDir="])
except getopt.GetoptError:
	usage()
	sys.exit(2)

for o, p in opts:
	if o in ('-p','--projectName'):
		projectName = p
	elif o in ('-i','--projectId'):
		projectId = p
	elif o in ('-R','--reference'):
		reference = p
	elif o in ('-r','--projectRootDir'):
		rootDir = p
	else:
		usage()
		sys.exit(2)	

runFoldersAlreadyRunFile = "runFoldersAlreadyRun.txt"
runFoldersAlreadyRun = get_runfolders_already_run(runFoldersAlreadyRunFile)

 
#------------------------------------------------------------------
#
#------------------------------------------------------------------
 
print "<Project Name=\"" + projectName + "\"" + " SequencingCenter=\"SnqSeq - Uppsala\" Platform=\"Illumina\" UppmaxProjectId=\"" + projectId + "\">"
 
for runFolder in get_immediate_subdirectories(rootDir):
        if "_" in runFolder.lower() and runFolder not in runFoldersAlreadyRun:
                print("\t<RunFolder Report=\"%s%s/report.xml\">" % (rootDir, runFolder))
                print_sample_folder_entries(rootDir + runFolder)
                print("\t</RunFolder>")
                with open(runFoldersAlreadyRunFile,"a") as f:
                        f.write("\n" + runFolder)
print "</Project>"
