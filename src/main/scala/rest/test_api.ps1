#!/usr/bin/env pwsh
# Chess API Testing Script for Windows PowerShell

Write-Output "=== ME Chess REST API Test Script ==="
Write-Output ""
Write-Output "Prerequisites: Server must be running (sbt run)"
Write-Output ""

$BaseUrl = "http://localhost:8080/v1/chess"
$GameId = ""

function Test-GameCreation {
    Write-Output "1. Creating a new game..."
    $response = Invoke-RestMethod -Uri "$BaseUrl/games" -Method Post
    $GameId = $response.gameId
    Write-Output "   ✅ Game created with ID: $GameId"
    Write-Output "   Response: $(ConvertTo-Json $response)"
    Write-Output ""
    return $GameId
}

function Test-GetGameState {
    param([string]$id)
    Write-Output "2. Getting game state..."
    $response = Invoke-RestMethod -Uri "$BaseUrl/games/$id" -Method Get
    Write-Output "   ✅ Game state retrieved"
    Write-Output "   Current Turn: $($response.currentTurn)"
    Write-Output "   Move History: $($response.moveHistory.Count) moves"
    Write-Output "   Game Over: $($response.isGameOver)"
    Write-Output ""
    return $response
}

function Test-PlayMove {
    param([string]$id, [string]$from, [string]$to)
    Write-Output "3. Playing move: $from -> $to"
    $body = @{
        from = $from
        to = $to
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/games/$id/moves" `
            -Method Post `
            -Body $body `
            -ContentType "application/json"
        Write-Output "   ✅ Move played successfully"
        Write-Output "   Current Turn: $($response.currentTurn)"
        Write-Output "   Move History: $($response.moveHistory.Count) moves"
        Write-Output ""
    } catch {
        Write-Output "   ❌ Error: $($_.Exception.Message)"
        Write-Output ""
    }
}

function Test-ListGames {
    Write-Output "4. Listing all active games..."
    $response = Invoke-RestMethod -Uri "$BaseUrl/games" -Method Get
    Write-Output "   ✅ Active games: $($response.count)"
    $response.games | ForEach-Object { Write-Output "      - $_" }
    Write-Output ""
}

function Test-APIInfo {
    Write-Output "5. Getting API info..."
    $response = Invoke-RestMethod -Uri "$BaseUrl/info" -Method Get
    Write-Output "   ✅ API Info:"
    Write-Output "      Name: $($response.name)"
    Write-Output "      Version: $($response.version)"
    Write-Output "      Status: $($response.status)"
    Write-Output ""
}

function Test-DeleteGame {
    param([string]$id)
    Write-Output "6. Deleting game..."
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/games/$id" -Method Delete
        Write-Output "   ✅ Game deleted: $($response.message)"
        Write-Output ""
    } catch {
        Write-Output "   ❌ Error: $($_.Exception.Message)"
        Write-Output ""
    }
}

# Run all tests
Write-Output "Starting tests..."
Write-Output ""

$GameId = Test-GameCreation
Test-GetGameState $GameId
Test-PlayMove $GameId "e2" "e4"
Test-PlayMove $GameId "e7" "e5"
Test-PlayMove $GameId "g1" "f3"
Test-ListGames
Test-APIInfo
Test-DeleteGame $GameId

Write-Output "=== All tests completed ==="
Write-Output ""
Write-Output "Tips:"
Write-Output "- Valid positions: a1-h8 (algebraic notation)"
Write-Output "- Example moves: e2e4, e7e5, d2d4, etc."
Write-Output "- Check server logs for detailed information"

