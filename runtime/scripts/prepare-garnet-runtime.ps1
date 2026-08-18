[CmdletBinding()]
param(
  [string] $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$runtimeRoot = Join-Path $ProjectRoot 'runtime\vendor\win-x64'
$downloadRoot = Join-Path $runtimeRoot '_downloads'
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) 'ming-harness-garnet-runtime'

New-Item -ItemType Directory -Force -Path $runtimeRoot, $downloadRoot, $temporaryRoot | Out-Null

function Get-VerifiedDownload {
  param(
    [Parameter(Mandatory = $true)] [string] $Url,
    [Parameter(Mandatory = $true)] [string] $Sha256,
    [Parameter(Mandatory = $true)] [string] $Destination
  )

  if (Test-Path -LiteralPath $Destination -PathType Leaf) {
    $currentHash = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($currentHash -eq $Sha256.ToLowerInvariant()) {
      Write-Host "Using cached download: $Destination"
      return
    }
    Remove-Item -LiteralPath $Destination -Force
  }

  Write-Host "Downloading $Url"
  Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
  $actualHash = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actualHash -ne $Sha256.ToLowerInvariant()) {
    throw "SHA-256 mismatch for $Destination. Expected $Sha256, got $actualHash"
  }
}

function Copy-DirectoryContents {
  param(
    [Parameter(Mandatory = $true)] [string] $Source,
    [Parameter(Mandatory = $true)] [string] $Destination
  )

  New-Item -ItemType Directory -Force -Path $Destination | Out-Null
  Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force
  }
}

function Copy-GarnetLegalNotices {
  $garnetRoot = Join-Path $runtimeRoot 'garnet'
  $legalFiles = @(
    [pscustomobject]@{
      Source = (Join-Path $ProjectRoot 'runtime\licenses\GARNET-LICENSE.txt')
      Destination = (Join-Path $garnetRoot 'LICENSE')
      Label = 'Garnet LICENSE'
    }
    [pscustomobject]@{
      Source = (Join-Path $ProjectRoot 'runtime\licenses\GARNET-NOTICE.md')
      Destination = (Join-Path $garnetRoot 'NOTICE.md')
      Label = 'Garnet NOTICE'
    }
  )

  foreach ($legalFile in $legalFiles) {
    if (-not (Test-Path -LiteralPath $legalFile.Source -PathType Leaf)) {
      throw "$($legalFile.Label) 源文件不存在：$($legalFile.Source)"
    }
    Copy-Item -LiteralPath $legalFile.Source -Destination $legalFile.Destination -Force
  }
  Write-Host '已补齐 Garnet LICENSE 和 NOTICE.md'
}

function Expand-RuntimeArchive {
  param(
    [Parameter(Mandatory = $true)] [string] $Archive,
    [Parameter(Mandatory = $true)] [string] $Destination,
    [string] $ExpectedRootFile
  )

  $extractRoot = Join-Path $temporaryRoot ([guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
  try {
    Expand-Archive -LiteralPath $Archive -DestinationPath $extractRoot -Force
    $children = @(Get-ChildItem -LiteralPath $extractRoot -Force)
    $source = $extractRoot
    if ($ExpectedRootFile) {
      $rootFiles = @(Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter $ExpectedRootFile |
        Sort-Object @{ Expression = { $_.FullName.Length }; Ascending = $true }, FullName)
      if ($rootFiles.Count -eq 0) {
        throw "运行时压缩包中未找到 $ExpectedRootFile：$Archive"
      }
      if ($rootFiles.Count -gt 1) {
        $candidates = ($rootFiles | ForEach-Object { $_.FullName }) -join '; '
        Write-Warning "运行时压缩包中存在多个 $ExpectedRootFile，将使用路径最短的发布目录：$candidates"
      }
      $source = $rootFiles[0].Directory.FullName
    } elseif ($children.Count -eq 1 -and $children[0].PSIsContainer) {
      $source = $children[0].FullName
    }
    if (Test-Path -LiteralPath $Destination) {
      Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    Copy-DirectoryContents -Source $source -Destination $Destination
  }
  finally {
    if (Test-Path -LiteralPath $extractRoot) {
      Remove-Item -LiteralPath $extractRoot -Recurse -Force
    }
  }
}

$archives = @(
  [pscustomobject]@{
    Name = 'PostgreSQL 17.6'
    Url = 'https://get.enterprisedb.com/postgresql/postgresql-17.6-1-windows-x64-binaries.zip'
    Sha256 = 'd378882abd001a186735acd6f6ba716bca6ccd192e800412d4fd15ed25376b3e'
    File = 'postgresql-17.6-1-windows-x64-binaries.zip'
    Destination = (Join-Path $runtimeRoot 'postgres')
  }
  [pscustomobject]@{
    Name = 'RabbitMQ 4.3.4'
    Url = 'https://github.com/rabbitmq/rabbitmq-server/releases/download/v4.3.4/rabbitmq-server-windows-4.3.4.zip'
    Sha256 = '45f0076637fb2d5920c5d12638946222d9352259643a128073815aa9159878c9'
    File = 'rabbitmq-server-windows-4.3.4.zip'
    Destination = (Join-Path $runtimeRoot 'rabbitmq')
  }
  [pscustomobject]@{
    Name = 'Erlang/OTP 28.5.0.5'
    Url = 'https://github.com/erlang/otp/releases/download/OTP-28.5.0.5/otp_win64_28.5.0.5.zip'
    Sha256 = 'ea3a6f78a981183384baedefd337b0542d7935335f6f865b8b6aab2542d205d6'
    File = 'otp_win64_28.5.0.5.zip'
    Destination = (Join-Path $runtimeRoot 'erlang')
  }
  [pscustomobject]@{
    Name = 'Temurin JRE 17.0.20+8'
    Url = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20%2B8/OpenJDK17U-jre_x64_windows_hotspot_17.0.20_8.zip'
    Sha256 = '7fe2324cc5901a89aaa0e8dc232075c59f2aca5270c533bdb1b861a5274af834'
    File = 'OpenJDK17U-jre_x64_windows_hotspot_17.0.20_8.zip'
    Destination = (Join-Path $runtimeRoot 'jre')
  }
  [pscustomobject]@{
    Name = 'Microsoft Garnet 2.1.1'
    Url = 'https://github.com/microsoft/garnet/releases/download/v2.1.1/win-x64-based-readytorun.zip'
    Sha256 = '73fd3c601176ae6aaa823e2d5b370a4afd6eaeac3416c572b8ebe1aab9556e89'
    File = 'win-x64-based-readytorun.zip'
    Destination = (Join-Path $runtimeRoot 'garnet')
  }
  [pscustomobject]@{
    Name = '.NET Runtime 8.0.18'
    Url = 'https://dotnetcli.blob.core.windows.net/dotnet/Runtime/8.0.18/dotnet-runtime-8.0.18-win-x64.zip'
    Sha256 = '595518cef68b0a111961f46346481d7da40409e5d6a7ba72ac418b5957b599af'
    File = 'dotnet-runtime-8.0.18-win-x64.zip'
    Destination = (Join-Path $runtimeRoot 'garnet\dotnet')
  }
)

foreach ($archive in $archives) {
  $file = Join-Path $downloadRoot $archive.File
  Get-VerifiedDownload -Url $archive.Url -Sha256 $archive.Sha256 -Destination $file
  if ($archive.Name -eq 'Microsoft Garnet 2.1.1') {
    Expand-RuntimeArchive -Archive $file -Destination $archive.Destination -ExpectedRootFile 'GarnetServer.exe'
    Copy-GarnetLegalNotices
  } else {
    Expand-RuntimeArchive -Archive $file -Destination $archive.Destination
  }
  Write-Host "Prepared $($archive.Name)"
}

Write-Host "Garnet Windows runtime prepared under $runtimeRoot"
