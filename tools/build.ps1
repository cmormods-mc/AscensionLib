param(
    [Parameter(Mandatory = $true)]
    [string] $JavaHome
)
$ErrorActionPreference = 'Stop'
$ascendRoot = Split-Path -Parent $PSScriptRoot
$ascendJava = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $ascendJava -PathType Leaf)) {
    throw "Java executable does not exist: $ascendJava"
}
& $ascendJava -classpath (Join-Path $ascendRoot 'gradle/wrapper/gradle-wrapper.jar') org.gradle.wrapper.GradleWrapperMain -p $ascendRoot :domain:test :fabric:build --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
