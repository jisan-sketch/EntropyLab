Write-Host "Building EntropyLab..."

mvn compile

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build successful."
    Write-Host "Launching EntropyLab..."

    mvn javafx:run
}
else {
    Write-Host "Build failed. EntropyLab was not launched."
    exit 1
}