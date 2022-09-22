curl -u onos:rocks \
    -X POST \
    -H 'Content-Type: application/json' \
    -d @$1 \
    -i \
    "http://localhost:8181/onos/v1/flows/of:$(printf %016d ${2})"
echo
