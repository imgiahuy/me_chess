Write-Host "==========================================" -ForegroundColor Green
Write-Host "Building all Scala JARs with sbt..." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green

# Build all modules
sbt clean

Write-Host "Building all JARs..." -ForegroundColor Yellow
sbt "project restApi" assembly
sbt "project playerService" assembly
sbt "project tournamentService" assembly
sbt "project botService" assembly
sbt "project spark" assembly

Write-Host "==========================================" -ForegroundColor Green
Write-Host "All JARs built successfully!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green

Write-Host "Building Docker images..." -ForegroundColor Yellow
docker-compose build

Write-Host "==========================================" -ForegroundColor Green
Write-Host "All Docker images built successfully!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
