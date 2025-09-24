# from project root
docker compose up -d --build db
# (first start initializes DB + installs extension + runs tests)

# connect:
docker compose exec -it db psql -U fhir -d fhir

# rebuild the extension after SQL changes:
docker compose build db && docker compose up -d db
