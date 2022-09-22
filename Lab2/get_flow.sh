curl -u onos:rocks \
    -X GET \
    -H 'Accept: application/json' \
    "http://localhost:8181/onos/v1/flows/of:$(printf %016d ${1})" | json_pp