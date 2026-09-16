$ErrorActionPreference = "Stop"
$root = "C:\AVSP_ALL_ANDROID_M1_M9_INTEGRATED\avsp_android_master"
Set-Location $root

function Backup-Once($path) {
    $bak = "$path.avsp_m7_bridge.bak"
    if (-not (Test-Path $bak)) { Copy-Item $path $bak }
}

# AppContainer: expose the existing M7 services.
$app = ".\app\src\main\java\com\avsp\pro\di\AppContainer.kt"
Backup-Once $app
$a = Get-Content $app -Raw
if ($a -notmatch 'import com\.avsp\.pro\.m7\.M7Services') {
    $a = $a.Replace(
        'import com.avsp.pro.media.BundledMediaSeeder',
        "import com.avsp.pro.media.BundledMediaSeeder`r`nimport com.avsp.pro.m7.M7Services"
    )
}
if ($a -notmatch 'val m7Services: M7Services') {
    $a = $a.Replace(
        '    private val appContext = context.applicationContext',
        "    private val appContext = context.applicationContext`r`n`r`n    val m7Services: M7Services = M7Services(appContext)"
    )
}
Set-Content $app $a -Encoding UTF8

# MediaViewModel: merge legacy media_assets with M7 project_media.
$vm = ".\app\src\main\java\com\avsp\pro\ui\viewmodel\MediaViewModel.kt"
Backup-Once $vm
$s = Get-Content $vm -Raw

if ($s -notmatch 'M7MediaRepository') {
    $s = $s.Replace(
        'import com.avsp.pro.repository.ProjectRepository',
        "import com.avsp.pro.repository.ProjectRepository`r`nimport com.avsp.pro.m7.data.repository.MediaRepository as M7MediaRepository"
    )
}
if ($s -notmatch 'private val m7MediaRepository: M7MediaRepository') {
    $oldCtor = @'
    private val projectRepository: ProjectRepository,
    private val logger: AvspLogger,
    private val bundledMediaSeeder: BundledMediaSeeder
) : ViewModel() {
'@
    $newCtor = @'
    private val projectRepository: ProjectRepository,
    private val logger: AvspLogger,
    private val bundledMediaSeeder: BundledMediaSeeder,
    private val m7MediaRepository: M7MediaRepository
) : ViewModel() {
'@
    if (-not $s.Contains($oldCtor)) { throw "MediaViewModel constructor not found." }
    $s = $s.Replace($oldCtor, $newCtor)
}

if ($s -notmatch 'val legacyAssets = projectRepository\.listMediaAssets') {
    $oldBlock = @'
                    bundledMediaSeeder.ensureForProject(project.projectId)
                    project.projectId to projectRepository.listMediaAssets(project.projectId)
'@
    $newBlock = @'
                    bundledMediaSeeder.ensureForProject(project.projectId)
                    val legacyAssets = projectRepository.listMediaAssets(project.projectId)
                    val m7Assets = m7MediaRepository.getMediaForProject(project.projectId).map { media ->
                        com.avsp.pro.core.contracts.MediaAsset(
                            assetId = media.id,
                            projectId = media.projectId,
                            fileName = media.displayName,
                            relativePath = media.uriString,
                            mimeType = if (media.mediaType.equals("VIDEO", ignoreCase = true)) "video/mp4" else "image/jpeg",
                            sizeBytes = media.fileSizeBytes,
                            durationMs = if (media.durationSeconds > 0L) media.durationSeconds * 1000L else null,
                            width = media.width.takeIf { it > 0 },
                            height = media.height.takeIf { it > 0 },
                            createdAt = media.createdAt,
                            tags = media.tagsList(),
                            metadata = mapOf(
                                "source" to "M7",
                                "shotType" to media.shotType,
                                "category" to media.category,
                                "recommendation" to media.recommendation,
                                "qualityScore" to media.qualityScore.toString()
                            )
                        )
                    }
                    project.projectId to (m7Assets + legacyAssets)
'@
    if (-not $s.Contains($oldBlock)) { throw "MediaViewModel refresh block not found." }
    $s = $s.Replace($oldBlock, $newBlock)
}
Set-Content $vm $s -Encoding UTF8

# ViewModelFactory: inject the same M7 MediaRepository.
$factory = ".\app\src\main\java\com\avsp\pro\ui\viewmodel\ViewModelFactory.kt"
Backup-Once $factory
$f = Get-Content $factory -Raw
if ($f -notmatch 'm7MediaRepository = container\.m7Services\.mediaRepository') {
    $oldF = @'
                    projectRepository = container.projectRepository,
                    logger = container.logger,
                    bundledMediaSeeder = container.bundledMediaSeeder
                ) as T
'@
    $newF = @'
                    projectRepository = container.projectRepository,
                    logger = container.logger,
                    bundledMediaSeeder = container.bundledMediaSeeder,
                    m7MediaRepository = container.m7Services.mediaRepository
                ) as T
'@
    if (-not $f.Contains($oldF)) { throw "ViewModelFactory MediaViewModel block not found." }
    $f = $f.Replace($oldF, $newF)
}
Set-Content $factory $f -Encoding UTF8

Write-Host "AVSP M7 MEDIA UI BRIDGE APPLIED"
Write-Host "Modified: AppContainer.kt, MediaViewModel.kt, ViewModelFactory.kt"
Write-Host "Backups: *.avsp_m7_bridge.bak"
