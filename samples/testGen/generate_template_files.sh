#!/bin/bash
# run test data generator examples
call ../setenv.bat
echo "GenFile program to generate output from templates."

echo  -e "\nEach test will generate files into the out directory.\n"
echo "--------------------------------------------------------"
${JAVA_CMD} bench.gen.GenFile -help

echo -e "\nSimplePO.gen sample: Simple template with the most common options"
${JAVA_CMD} bench.gen.GenFile template=SimplePO.gen out=out/SimplePO.xml


echo -e "\nGenericOrder.gen sample: generates more complex purchase orders"
${JAVA_CMD} bench.gen.GenFile num=3 start=100 template=GenericOrder.gen out=out/GenericOrder.xml

echo -e "\nGenSyntaxTest.gen sample: demonstrates various template syntax options"
${JAVA_CMD} bench.gen.GenFile num=1 template=GenSyntaxTest.gen vars={CMDARG=PassedIN,WORDCOUNT=5} out=out/GenSyntaxTest.txt
