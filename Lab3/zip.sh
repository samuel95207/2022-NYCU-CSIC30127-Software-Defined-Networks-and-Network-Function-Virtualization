rm -rf project3_311511034
rm -rf project3_311511034.zip

mkdir project3_311511034

cp -r bridge-app/* project3_311511034
rm -rf project3_311511034/target

zip -r project3_311511034.zip project3_311511034
rm -rf project3_311511034