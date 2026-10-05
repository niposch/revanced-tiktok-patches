param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Get-VerifiedTool($Name, $Url, $Hash) {
    $path = Join-Path $PSScriptRoot "tools/$Name"
    if (!(Test-Path -LiteralPath $path)) { Invoke-WebRequest -Uri $Url -OutFile $path }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $Hash) {
        throw "Tool checksum mismatch: $Name"
    }
    return $path
}

New-Item -ItemType Directory -Force -Path tools, dist | Out-Null
$version = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'VERSION') -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid VERSION file.' }
$taskJdk = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/javac.exe")) {
    $env:JAVA_HOME
} else {
    $taskJava = Get-ChildItem 'C:/Program Files/Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } |
        Sort-Object Name -Descending | Select-Object -First 1
    if (!$taskJava) { throw 'Set JAVA_HOME to a JDK 17 or newer.' }
    $taskJava.FullName
}
$java = Join-Path $taskJdk 'bin/java.exe'
$javac = Join-Path $taskJdk 'bin/javac.exe'
$jar = Join-Path $taskJdk 'bin/jar.exe'
$cli = Get-VerifiedTool 'revanced-cli.jar' 'https://github.com/ReVanced/revanced-cli/releases/download/v6.0.0/revanced-cli-6.0.0-all.jar' 'C25549BC17D59D2EB94FA5F86E60E9B77A02772CA88F7050F8F1276F923A9958'
$r8 = Get-VerifiedTool 'r8.jar' 'https://storage.googleapis.com/r8-releases/raw/8.13.17/r8.jar' '98B7741C0ED50BF86174BEA93A62D1879E838FAE6D7166FD4FAC2787E11B3572'

function Invoke-Checked($Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Program" }
}

# A fresh directory avoids stale compiled classes without deleting earlier output.
$out = Join-Path $PSScriptRoot ('build/' + [Guid]::NewGuid().ToString('N'))
$stubs = Join-Path $out 'stubs'
$extension = Join-Path $out 'extension'
$patches = Join-Path $out 'patches'
$extensionDex = Join-Path $out 'extension-dex'
$patchDex = Join-Path $out 'patch-dex'
$tests = Join-Path $out 'tests'
$resources = Join-Path $out 'resources/extensions'
New-Item -ItemType Directory -Force -Path $stubs,$extension,$patches,$extensionDex,$patchDex,$tests,$resources | Out-Null

Invoke-Checked $javac (@('--release','11','-d',$stubs) + @(Get-ChildItem stubs/src -Recurse -Filter *.java | ForEach-Object FullName))
$extensionSources = Get-ChildItem extension/src -Recurse -Filter *.java
$patchSources = Get-ChildItem patches/src -Recurse -Filter *.java
Invoke-Checked $javac (@('--release','11','-cp',$stubs,'-d',$extension) + @($extensionSources | ForEach-Object FullName))
if (!$SkipTests) {
    $testSources = Get-ChildItem tests -Filter *.java
    Invoke-Checked $javac (@('--release','11','-cp',"$stubs;$extension",'-d',$tests) + @($testSources | ForEach-Object FullName))
    Invoke-Checked $java @('-cp',"$stubs;$extension;$tests",'FeedFilterTest')
    Invoke-Checked $java @('-cp',"$stubs;$extension;$tests",'RegistrationIdentityTest')
}
Invoke-Checked $javac (@('--release','11','-cp',$cli,'-d',$patches) + @($patchSources | ForEach-Object FullName))
Invoke-Checked $jar @('--create','--file',"$out/extension.jar",'-C',$extension,'.')
Invoke-Checked $jar @('--create','--file',"$out/patches.jar",'-C',$patches,'.')
Invoke-Checked $java @('-Xmx2g','-cp',$r8,'com.android.tools.r8.D8','--release','--min-api','23','--classpath',$stubs,'--output',$extensionDex,"$out/extension.jar")
Invoke-Checked $java @('-Xmx2g','-cp',$r8,'com.android.tools.r8.D8','--release','--min-api','26','--classpath',$cli,'--output',$patchDex,"$out/patches.jar")
Copy-Item -LiteralPath "$extensionDex/classes.dex" -Destination "$resources/tiktok.rve"
$manifest = Join-Path $out 'MANIFEST.MF'
$fingerprintFiles = @('VERSION','build.ps1') + @(
    @($extensionSources) + @($patchSources) + @(Get-ChildItem stubs/src -Recurse -Filter *.java) |
        ForEach-Object { [IO.Path]::GetRelativePath($PSScriptRoot,$_.FullName).Replace('\','/') }
)
[Array]::Sort($fingerprintFiles,[StringComparer]::Ordinal)
$fingerprintText = [Text.StringBuilder]::new()
foreach ($fingerprintFile in $fingerprintFiles) {
    $sourceText = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $fingerprintFile)).Replace("`r`n","`n")
    [void]$fingerprintText.Append($fingerprintFile).Append("`n").Append($sourceText).Append("`n")
}
$sourceHash = [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash(
    [Text.Encoding]::UTF8.GetBytes($fingerprintText.ToString()))).Replace('-','').ToLowerInvariant()
@"
Manifest-Version: 1.0
Name: TikTok feed filters
Version: $version
Description: Ad and Shop video filters for TikTok 47.1.4
License: GPL-3.0-only
Source-SHA256: $sourceHash

"@ | Set-Content -LiteralPath $manifest -Encoding ascii
$bundle = Join-Path $PSScriptRoot "dist/tiktok-feed-filters-$version.rvp"
Invoke-Checked $jar @('--create','--file',$bundle,'--manifest',$manifest,'-C',$patches,'.','-C',$patchDex,'.','-C',"$out/resources",'.')
Invoke-Checked $java @('-jar',$cli,'list-patches','-p',$bundle,'-b','--packages','--versions')
Write-Host "Built $bundle"
