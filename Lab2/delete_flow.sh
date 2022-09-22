curl -u onos:rocks \
    -X DELETE \
    -H 'Accept: application/json' \
    -i \
    "http://localhost:8181/onos/v1/flows/of:$(printf %016d ${1})/${2}"
echo