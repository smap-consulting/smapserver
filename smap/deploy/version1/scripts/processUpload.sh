#!/bin/sh

if [ $# -lt "2" ]; then
        echo "usage $0 file_path final_file_path"
fi

echo "================================================="
echo "processing $0 $1 $2" 

filePath=$1
finalFilePath=$2

# Since xls2xform sets the top level form name from the file name create a temporary file with name "main"


extension="${filePath##*.}"

tmpDir="$filePath.tmp"
tmpFile="$tmpDir/main.$extension"

mkdir $tmpDir
cp $filePath $tmpFile
python /smap_bin/pyxform/xls2xform.py $tmpFile $finalFilePath
exitValue=$?

rm -rf $tmpDir

# If the transformation failed then run odkValidate
if [ $exitValue -ne "0" ]; then
java -jar /smap_bin/validate.jar $finalFilePath
fi

# Return the exit value from xls2xform.py rather than the clean up commands
exit $exitValue
