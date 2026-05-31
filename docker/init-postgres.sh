#!/bin/bash
set -e

# Function to create database if it doesn't exist
create_database() {
    local database=$1
    local user=$2
    local password=$3
    
    echo "Creating database: $database"
    psql -v ON_ERROR_STOP=1 --username "$user" --dbname "$POSTGRES_DB" <<-EOSQL
        SELECT 'CREATE DATABASE $database'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$database')\gexec
        GRANT ALL PRIVILEGES ON DATABASE $database TO $user;
EOSQL
}

# Create additional databases
if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
    for db in $(echo $POSTGRES_MULTIPLE_DATABASES | tr ',' ' '); do
        create_database $db $POSTGRES_USER $POSTGRES_PASSWORD
    done
fi
