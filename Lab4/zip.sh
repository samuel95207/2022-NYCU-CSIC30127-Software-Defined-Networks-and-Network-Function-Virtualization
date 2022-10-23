rm -rf project4_311511034
rm -rf project4_311511034.zip

mkdir project4_311511034

cp -r unicastdhcp/* project4_311511034
rm -rf project4_311511034/target

zip -r project4_311511034.zip project4_311511034
rm -rf project4_311511034