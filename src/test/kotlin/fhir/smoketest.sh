#Make sure that the application is running, on the exact port.
# But I also attached the Postman collection for Doing the test and multiple checks
# Create
curl -X POST http://localhost:8081/fhir/Patient \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"Patient","name":[{"family":"Smoke","given":["Test_Shell"]}],"gender":"female","birthDate":"1990-01-01"}' -v

# Search
curl "http://localhost:8081/fhir/Patient?name=Doe&gender=female&_count=10&_offset=0" -H "Accept: application/fhir+json"

# Get (replace <id> from Location)
curl http://localhost:8081/fhir/Patient/<id> -H "Accept: application/fhir+json"
