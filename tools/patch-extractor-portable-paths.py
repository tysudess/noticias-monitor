from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/ExtractorVideoEngine.kt'
if not ENGINE.exists():
    raise SystemExit('ExtractorVideoEngine.kt ausente.')

src = ENGINE.read_text(encoding='utf-8')
old = '''    companion object {\n        private fun discoverAppDir(): Path {\n            val cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()\n            val candidates = listOf(cwd, cwd.parent ?: cwd)\n            return candidates.firstOrNull { it.resolve("bin").toFile().exists() } ?: cwd\n        }\n    }'''
new = '''    companion object {\n        private fun discoverAppDir(): Path {\n            val cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()\n            val launcherDir = runCatching {\n                System.getProperty("jpackage.app-path", "")\n                    .trim()\n                    .takeIf { it.isNotBlank() }\n                    ?.let { Paths.get(it).toAbsolutePath().normalize().parent }\n            }.getOrNull()\n            val codeSourceDir = runCatching {\n                val path = Paths.get(ExtractorVideoEngine::class.java.protectionDomain.codeSource.location.toURI())\n                    .toAbsolutePath().normalize()\n                if (path.toFile().isFile) path.parent else path\n            }.getOrNull()\n            val candidates = listOfNotNull(launcherDir, cwd, codeSourceDir, cwd.parent).distinct()\n            return candidates.firstOrNull { candidate ->\n                candidate.resolve("bin").toFile().exists() ||\n                    candidate.resolve("MonitorDeNoticias.exe").toFile().exists()\n            } ?: launcherDir ?: cwd\n        }\n    }'''
if old in src:
    src = src.replace(old, new, 1)
elif 'jpackage.app-path' not in src:
    raise SystemExit('Bloco discoverAppDir não encontrado para patch portable.')

ENGINE.write_text(src, encoding='utf-8')
updated = ENGINE.read_text(encoding='utf-8')
assert 'jpackage.app-path' in updated
assert 'MonitorDeNoticias.exe' in updated
assert 'codeSource.location.toURI()' in updated
print('Diretório portable resolvido pelo launcher jpackage/bin antes de user.dir.')
