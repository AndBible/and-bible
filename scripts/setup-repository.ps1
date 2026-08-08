$ErrorActionPreference = "Stop"

$upstreamUrl = "https://github.com/andbible/and-bible.git"

Write-Host "Configuring Git repository..."

# Verify that we are inside a Git repository.
git rev-parse --show-toplevel *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "This script must be run inside a Git repository."
    exit 1
}

# Configure upstream remote.
$upstream = git remote get-url upstream 2>$null

if (-not $upstream) {
    Write-Host "Adding upstream remote..."
    git remote add upstream $upstreamUrl
}
elseif ($upstream -ne $upstreamUrl) {
    Write-Host "Updating upstream remote..."
    git remote set-url upstream $upstreamUrl
}

# Disable pushing to upstream to prevent accidental pushes.
git remote set-url --push upstream DISABLED

Write-Host ""
Write-Host "Repository configured successfully."
Write-Host ""

git remote -v