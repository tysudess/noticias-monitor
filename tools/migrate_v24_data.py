#!/usr/bin/env python3
"""Migração única do Monitor de Notícias v2.4 para a linha de assinatura permanente.

Por que isso existe
===================
A Release v2.4.0 foi assinada por uma chave de debug efêmera do GitHub Actions.
Essa chave privada não existe mais, então o Android não aceita um APK novo por
cima dela. Como a v2.4 é debuggable, o `run-as` do ADB permite salvar os dados
antes da troca de assinatura e restaurá-los numa build de migração também
debuggable, já assinada com a nova chave permanente.

O fluxo seguro é:
1. backup: salva somente news.db (+ WAL/SHM, se existirem) e shared_prefs;
2. migrate: remove v2.4 somente após validar o backup;
3. instala o APK de migração v2.5 (debuggable + chave permanente);
4. restaura dados e abre o app uma vez para executar a migração do banco;
5. instala por cima o APK final v2.5 (mesma chave permanente, dados mantidos).

Requisitos
==========
- Android Platform Tools (`adb`) no PATH;
- Depuração USB ativada e computador autorizado;
- v2.4.0 do GitHub ainda instalada no telefone;
- Python 3.9+.

Uso
===
  python tools/migrate_v24_data.py backup
  python tools/migrate_v24_data.py migrate \
      monitor-noticias-v24-data.tar \
      monitor-de-noticias-v2.5.0-migration.apk \
      monitor-de-noticias-v2.5.0.apk

O backup nunca é apagado automaticamente.
"""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys
import tarfile
import time
from datetime import datetime

PACKAGE = "br.com.monitordenoticias.android"
DEFAULT_BACKUP = pathlib.Path("monitor-noticias-v24-data.tar")


def run(args: list[str], **kwargs) -> subprocess.CompletedProcess:
    print("+", " ".join(args))
    return subprocess.run(args, check=True, **kwargs)


def adb_text(*args: str) -> str:
    return subprocess.check_output(["adb", *args], text=True, errors="replace").strip()


def require_adb() -> None:
    if shutil.which("adb") is None:
        raise SystemExit(
            "ERRO: adb não encontrado. Instale Android Platform Tools e adicione adb ao PATH."
        )
    run(["adb", "get-state"], stdout=subprocess.DEVNULL)


def installed_version() -> str:
    try:
        output = adb_text("shell", "dumpsys", "package", PACKAGE)
    except subprocess.CalledProcessError:
        return "desconhecida"
    for line in output.splitlines():
        line = line.strip()
        if line.startswith("versionName="):
            return line.split("=", 1)[1]
    return "desconhecida"


def ensure_run_as(context: str) -> None:
    probe = subprocess.run(
        ["adb", "shell", "run-as", PACKAGE, "pwd"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if probe.returncode != 0:
        raise SystemExit(
            f"ERRO ({context}): não foi possível acessar os dados com run-as.\n"
            "Confirme que o APK correto está instalado, é debuggable e a depuração USB está autorizada.\n"
            + probe.stderr.strip()
        )


def validate_backup(path: pathlib.Path) -> None:
    if not path.exists() or path.stat().st_size < 512:
        raise SystemExit(f"ERRO: backup inexistente ou vazio: {path}")
    try:
        with tarfile.open(path, "r") as tf:
            names = set(tf.getnames())
    except tarfile.TarError as exc:
        raise SystemExit(f"ERRO: backup TAR inválido: {exc}") from exc

    db_present = any(name.endswith("databases/news.db") or name == "databases/news.db" for name in names)
    prefs_present = any(name == "shared_prefs" or name.startswith("shared_prefs/") for name in names)
    if not db_present:
        raise SystemExit("ERRO: o backup não contém databases/news.db.")
    if not prefs_present:
        raise SystemExit("ERRO: o backup não contém shared_prefs.")


def backup(path: pathlib.Path = DEFAULT_BACKUP) -> None:
    require_adb()
    print(f"Versão instalada detectada: {installed_version()}")
    ensure_run_as("backup da v2.4")
    run(["adb", "shell", "am", "force-stop", PACKAGE])

    if path.exists():
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        previous = path.with_name(path.name + f".previous-{stamp}")
        path.replace(previous)
        print(f"Backup anterior preservado em: {previous.resolve()}")

    print(f"Criando backup em {path.resolve()} ...")
    shell = (
        f"cd /data/user/0/{PACKAGE} || exit 1; "
        "set -- databases/news.db shared_prefs; "
        "[ -f databases/news.db-wal ] && set -- \"$@\" databases/news.db-wal; "
        "[ -f databases/news.db-shm ] && set -- \"$@\" databases/news.db-shm; "
        "tar -cf - \"$@\""
    )
    with path.open("wb") as out:
        proc = subprocess.run(
            ["adb", "exec-out", "run-as", PACKAGE, "sh", "-c", shell],
            stdout=out,
            stderr=subprocess.PIPE,
        )
    if proc.returncode != 0:
        path.unlink(missing_ok=True)
        raise SystemExit("ERRO ao criar backup: " + proc.stderr.decode(errors="replace"))

    validate_backup(path)
    print(f"OK: backup validado ({path.stat().st_size} bytes).")
    print("Nenhum dado foi apagado do telefone.")


def restore_backup(path: pathlib.Path) -> None:
    print("Restaurando banco e preferências no APK de migração ...")
    with path.open("rb") as src:
        proc = subprocess.run(
            [
                "adb", "shell", "run-as", PACKAGE, "sh", "-c",
                f"cd /data/user/0/{PACKAGE} && tar -xf -",
            ],
            stdin=src,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    if proc.returncode != 0:
        raise SystemExit(
            "ERRO durante a restauração. O backup continua intacto em "
            f"{path.resolve()}\n" + proc.stderr.decode(errors="replace")
        )


def launch_once() -> None:
    subprocess.run(
        ["adb", "shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    time.sleep(4)
    run(["adb", "shell", "am", "force-stop", PACKAGE])


def migrate(backup_path: pathlib.Path, migration_apk: pathlib.Path, final_apk: pathlib.Path) -> None:
    require_adb()
    validate_backup(backup_path)
    for apk in (migration_apk, final_apk):
        if not apk.exists() or apk.stat().st_size < 1024:
            raise SystemExit(f"ERRO: APK não encontrado ou inválido: {apk}")

    print(f"Backup validado: {backup_path.resolve()}")
    print(f"Versão instalada antes da migração: {installed_version()}")
    answer = input(
        "A próxima etapa removerá a v2.4 somente porque o backup já foi validado.\n"
        "Depois instalará a build de migração, restaurará os dados e instalará a build final.\n"
        "Digite MIGRAR para continuar: "
    ).strip()
    if answer != "MIGRAR":
        raise SystemExit("Migração cancelada; o app instalado não foi alterado.")

    run(["adb", "shell", "am", "force-stop", PACKAGE])
    run(["adb", "uninstall", PACKAGE])

    try:
        run(["adb", "install", str(migration_apk)])
        ensure_run_as("restauração no APK de migração")
        run(["adb", "shell", "am", "force-stop", PACKAGE])
        restore_backup(backup_path)

        # Abre a build debuggable uma vez para o SQLiteOpenHelper executar
        # automaticamente a migração 2 -> 3 sobre o news.db restaurado.
        launch_once()
        ensure_run_as("verificação após migração do banco")

        # O APK final usa a mesma chave permanente. -r mantém os dados já restaurados.
        run(["adb", "install", "-r", str(final_apk)])
        subprocess.run(
            ["adb", "shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    except (subprocess.CalledProcessError, SystemExit) as exc:
        print("\nA migração não foi concluída, mas o arquivo de backup NÃO foi removido:")
        print(backup_path.resolve())
        print("Não apague esse arquivo. Ele permite repetir a restauração.")
        raise

    print("\nOK: migração concluída.")
    print(f"Versão instalada agora: {installed_version()}")
    print("Banco, demandas, termos, fontes e preferências foram preservados pelo backup/restauração.")
    print(f"Guarde o backup por segurança: {backup_path.resolve()}")


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(
            "Uso:\n"
            "  python tools/migrate_v24_data.py backup [arquivo-backup.tar]\n"
            "  python tools/migrate_v24_data.py migrate backup.tar migration.apk final.apk"
        )

    command = sys.argv[1]
    if command == "backup":
        if len(sys.argv) > 3:
            raise SystemExit("Uso: python tools/migrate_v24_data.py backup [arquivo-backup.tar]")
        path = pathlib.Path(sys.argv[2]) if len(sys.argv) == 3 else DEFAULT_BACKUP
        backup(path)
    elif command == "migrate":
        if len(sys.argv) != 5:
            raise SystemExit(
                "Uso: python tools/migrate_v24_data.py migrate backup.tar migration.apk final.apk"
            )
        migrate(pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3]), pathlib.Path(sys.argv[4]))
    else:
        raise SystemExit("Comando inválido. Use backup ou migrate.")


if __name__ == "__main__":
    main()
